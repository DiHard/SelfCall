package ru.ifx.selfcall.data

import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import ru.ifx.selfcall.BuildConfig
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Код ошибки от сервера (`{"detail": {"code", "message"}}`) или клиентский:
 * `network` — нет связи, `unknown` — что-то другое. Текст для людей подбирает экран по коду.
 */
class ApiException(val code: String, message: String) : Exception(message)

class Api(context: Context, private val onUpdateRequired: ((AndroidUpdatePolicy) -> Unit)? = null) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true }
    private val jsonType = "application/json".toMediaType()
    private val server = BuildConfig.SERVER.toHttpUrl()
    private val credentialStore = ServerCredentialStore(context, BuildConfig.SERVER)
    @Volatile private var authorization: String? = null
    private val storedAuthorization by lazy { credentialStore.read() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val sameServer = original.url.host == server.host && original.url.port == server.port &&
                original.url.scheme == server.scheme
            val request = if (sameServer && original.url.encodedPath.startsWith("/api/")) {
                original.newBuilder().header("X-SelfCall-Android-Version", BuildConfig.VERSION_CODE.toString()).build()
            } else original
            val saved = if (sameServer && server.isHttps) authorization ?: storedAuthorization else null
            val header = request.header("Authorization")
            if (saved == null) {
                chain.proceed(request)
            } else if (header == null) {
                chain.proceed(request.newBuilder().header("Authorization", saved).build())
            } else if (header.startsWith("Bearer ") &&
                request.url.encodedPath in listOf("/rtc", "/rtc/validate")
            ) {
                // nginx принимает Basic, а LiveKit принимает токен из параметра access_token.
                val url = request.url.newBuilder().setQueryParameter("access_token", header.removePrefix("Bearer ")).build()
                chain.proceed(request.newBuilder().url(url).header("Authorization", saved).build())
            } else {
                chain.proceed(request)
            }
        }
        .build()

    val signalingClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .build()

    private val base = "${BuildConfig.SERVER}/api"

    suspend fun androidUpdate(): AndroidUpdatePolicy =
        call(Request.Builder().url("$base/android/update").build())

    suspend fun downloadAndroidRelease(
        release: AndroidRelease,
        destination: File,
        onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(release.sizeBytes in 1..250L * 1024 * 1024)
        val request = Request.Builder().url("$base/android/releases/selfcall-${release.versionCode}.apk").build()
        val digest = MessageDigest.getInstance("SHA-256")
        val download = signalingClient.newCall(request)
        // Закрываем соединение при отмене, даже если чтение сейчас ждёт данных от сервера.
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                download.cancel()
            }
        }
        try {
            download.execute().use { response ->
                checkBasicAuth(response)
                if (!response.isSuccessful) {
                    throw if (response.code == 404) ApiException("release_not_found", "Проверьте, какая версия доступна сейчас.")
                    else ApiException("update_unavailable", "Не удалось скачать обновление. Попробуйте ещё раз.")
                }
                val body = response.body ?: throw IOException("Empty APK response")
                var remaining = release.sizeBytes
                onProgress(0)
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (remaining > 0) {
                            ensureActive()
                            val size = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (size < 0) throw IOException("Incomplete APK")
                            output.write(buffer, 0, size)
                            digest.update(buffer, 0, size)
                            remaining -= size
                            onProgress(release.sizeBytes - remaining)
                        }
                        if (input.read() != -1) throw IOException("Unexpected APK size")
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha256 != release.sha256) throw ApiException("invalid_apk", "Файл обновления не прошёл проверку. Скачайте его ещё раз.")
        } catch (failure: IOException) {
            ensureActive()
            throw failure
        } finally {
            cancellation.cancel()
        }
    }

    suspend fun signIn(login: String, password: String) = withContext(Dispatchers.IO) {
        check(server.isHttps)
        val candidate = Credentials.basic(login, password, Charsets.UTF_8)
        val request = Request.Builder().url("$base/health").header("Authorization", candidate).build()
        call<Map<String, String>>(request)
        try {
            credentialStore.write(candidate)
        } catch (error: Exception) {
            throw ApiException("credential_storage", "Не удалось сохранить вход на телефоне. Попробуйте ещё раз.")
        }
        authorization = candidate
    }

    suspend fun createMeeting(): MeetingCreated =
        call(Request.Builder().url("$base/meetings").post(EMPTY_BODY.toRequestBody(jsonType)).build())

    suspend fun getMeeting(id: String): MeetingInfo =
        call(Request.Builder().url("$base/meetings/${enc(id)}").get().build())

    suspend fun callDirectory(deviceId: String): CallDirectory =
        call(Request.Builder().url("$base/calls/directory?device_id=${enc(deviceId)}").build())

    suspend fun enableIncoming(deviceId: String, name: String, token: String) =
        postCall<Map<String, String>>("enable", json.encodeToString(IncomingRegistration(deviceId, name, token)))

    suspend fun refreshIncoming(deviceId: String, token: String) =
        postCall<Map<String, String>>("refresh", json.encodeToString(IncomingDevice(deviceId, token)))

    suspend fun disableIncoming(deviceId: String) =
        postCall<Map<String, String>>("disable", json.encodeToString(IncomingDevice(deviceId)))

    suspend fun dial(id: String, login: String): DirectCall =
        postCall("dial", json.encodeToString(DialRequest(id, login)))

    suspend fun getDirectCall(id: String): DirectCall =
        call(Request.Builder().url("$base/calls/${enc(id)}").build())

    suspend fun changeDirectCall(id: String, action: String): DirectCall =
        postCall("${enc(id)}/state/$action")

    suspend fun connectDirectCall(id: String): JoinResponse = postCall("${enc(id)}/connect")

    private suspend inline fun <reified T> postCall(path: String, body: String = EMPTY_BODY): T =
        call(Request.Builder().url("$base/calls/$path").post(body.toRequestBody(jsonType)).build())

    suspend fun joinMeeting(id: String, name: String): JoinResponse {
        val body = json.encodeToString(JoinRequest(name.trim().ifEmpty { null }))
        return call(
            Request.Builder().url("$base/meetings/${enc(id)}/join").post(body.toRequestBody(jsonType)).build(),
        )
    }

    private suspend inline fun <reified T> call(request: Request): T = withContext(Dispatchers.IO) {
        val (status, text) = try {
            client.newCall(request).execute().use { response ->
                checkBasicAuth(response)
                response.code to response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw ApiException("network", "Нет связи с сервером")
        }
        if (status in 200..299) {
            try {
                return@withContext json.decodeFromString<T>(text)
            } catch (e: Exception) {
                throw ApiException("unknown", "Сервер прислал непонятный ответ")
            }
        }
        val detail = runCatching { json.decodeFromString<ErrorBody>(text).detail }.getOrNull()
        val policy = detail?.update
        if (status == 426 && detail?.code == "update_required" && policy != null && onUpdateRequired != null) {
            withContext(Dispatchers.Main.immediate) { onUpdateRequired.invoke(policy) }
        }
        throw ApiException(detail?.code ?: "unknown", detail?.message ?: "Ошибка сервера ($status)")
    }

    private fun checkBasicAuth(response: Response) {
        if (server.isHttps && response.code == 401 &&
            response.challenges().any { it.scheme.equals("Basic", ignoreCase = true) }
        ) {
            throw ApiException("sign_in_required", "Проверьте логин и пароль и попробуйте снова.")
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val EMPTY_BODY = "{}"
    }
}
