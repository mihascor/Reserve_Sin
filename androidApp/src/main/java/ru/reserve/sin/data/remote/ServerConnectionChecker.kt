package ru.reserve.sin.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

sealed interface ServerConnectionResult {
    data object Connected : ServerConnectionResult
    data object Unauthorized : ServerConnectionResult
    data class Failed(val message: String) : ServerConnectionResult
}

class ServerConnectionChecker {
    suspend fun check(serverUrl: String, token: String?): ServerConnectionResult {
        if (token.isNullOrBlank()) {
            return ServerConnectionResult.Failed("Сначала сохраните API-токен")
        }
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return try {
            val response = client.get("$serverUrl/api/v1/changes?after=0") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            when (response.status) {
                HttpStatusCode.OK -> ServerConnectionResult.Connected
                HttpStatusCode.Unauthorized -> ServerConnectionResult.Unauthorized
                else -> ServerConnectionResult.Failed("Сервер ответил: ${response.status.value}")
            }
        } catch (_: Exception) {
            ServerConnectionResult.Failed("Не удалось подключиться к серверу")
        } finally {
            client.close()
        }
    }
}
