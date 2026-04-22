package ru.ifx.selfcall.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ru.ifx.selfcall.BuildConfig

object Api {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val base: String = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun fetchRooms(): List<Room> =
        client.get("$base/rooms").body<RoomsResponse>().rooms

    suspend fun fetchToken(room: String, username: String): TokenResponse =
        client.post("$base/token") {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(room, username))
        }.body()
}
