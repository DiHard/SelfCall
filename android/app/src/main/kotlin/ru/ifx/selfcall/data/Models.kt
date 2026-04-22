package ru.ifx.selfcall.data

import kotlinx.serialization.Serializable

@Serializable
data class Room(val name: String)

@Serializable
data class RoomsResponse(val rooms: List<Room>)

@Serializable
data class TokenRequest(val room: String, val username: String)

@Serializable
data class TokenResponse(
    val token: String,
    val url: String,
    val room: String,
    val identity: String,
)
