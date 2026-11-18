package ru.ifx.selfcall.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class ServerCredentialStore(context: Context, private val server: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "server-credential"))

    fun read(): String? = runCatching {
        val encrypted = file.readFully()
        val key = keyStore().getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted, 0, IV_SIZE))
        cipher.updateAAD(server.toByteArray())
        cipher.doFinal(encrypted, IV_SIZE, encrypted.size - IV_SIZE).decodeToString()
    }.getOrNull()

    fun write(authorization: String) {
        val key = keyStore().getKey(keyAlias, null) as? SecretKey ?: createKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(server.toByteArray())
        val encrypted = cipher.iv + cipher.doFinal(authorization.encodeToByteArray())
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()

    private companion object {
        const val keyAlias = "selfcall.server-credential"
        const val IV_SIZE = 12
    }
}
