package ru.ifx.selfcall.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallContact(val login: String, val name: String)

@Serializable
data class CallDirectory(val incoming: CallContact?, val contacts: List<CallContact>)

@Serializable
data class DirectCall(
    val id: String,
    val name: String,
    val status: String,
    val incoming: Boolean,
    @SerialName("remaining_ms") val remainingMs: Long,
)

@Serializable
internal data class IncomingRegistration(
    @SerialName("device_id") val deviceId: String,
    val name: String,
    val token: String,
)

@Serializable
internal data class IncomingDevice(
    @SerialName("device_id") val deviceId: String,
    val token: String? = null,
)

@Serializable
internal data class DialRequest(val id: String, val login: String)
