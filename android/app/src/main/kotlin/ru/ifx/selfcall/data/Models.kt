package ru.ifx.selfcall.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingCreated(val id: String)

@Serializable
data class MeetingInfo(
    val id: String,
    val active: Boolean,
    val participants: Int,
    @SerialName("max_participants") val maxParticipants: Int,
)

@Serializable
data class JoinRequest(val name: String?)

@Serializable
data class JoinResponse(
    val token: String,
    val url: String,
    @SerialName("meeting_id") val meetingId: String,
    val identity: String,
    val name: String,
    @SerialName("elapsed_ms") val elapsedMs: Long? = null,
)

@Serializable
internal data class ErrorBody(val detail: ErrorDetail? = null)

@Serializable
internal data class ErrorDetail(
    val code: String? = null,
    val message: String? = null,
    val update: AndroidUpdatePolicy? = null,
)

@Serializable
data class AndroidUpdatePolicy(
    @SerialName("minimum_version_code") val minimumVersionCode: Int = 0,
    val latest: AndroidRelease? = null,
)

@Serializable
data class AndroidRelease(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
)
