package org.syncbin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val jsonFormat = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class SessionPayload(
    val text: String = "",
    val files: List<String> = emptyList(),
)

class SessionRepository(
    private val client: HttpClient = createHttpClient(),
) {
    fun observeSession(sessionId: String): Flow<SessionPayload> = flow {
        var previous: SessionPayload? = null
        while (currentCoroutineContext().isActive) {
            val current = fetchSession(sessionId)
            if (current != previous) {
                emit(current)
                previous = current
            }
            delay(1_000)
        }
    }

    suspend fun updateText(sessionId: String, text: String) {
        val current = fetchSession(sessionId)
        writeSession(sessionId, current.copy(text = text))
    }

    suspend fun uploadFile(sessionId: String, file: PickedFile) {
        client.post(storageObjectsUrl()) {
            parameter("uploadType", "media")
            parameter("name", "$sessionId/${file.name}")
            contentType(
                file.mimeType?.let(ContentType::parse) ?: ContentType.Application.OctetStream,
            )
            setBody(file.bytes)
        }
        val current = fetchSession(sessionId)
        val nextFiles = (current.files + file.name).distinct()
        writeSession(sessionId, current.copy(files = nextFiles))
    }

    suspend fun deleteFile(sessionId: String, fileName: String) {
        client.delete("${storageObjectsUrl()}/${storagePath(sessionId, fileName)}")
        val current = fetchSession(sessionId)
        writeSession(sessionId, current.copy(files = current.files.filterNot { it == fileName }))
    }

    fun publicFileUrl(sessionId: String, fileName: String): String {
        return "${storageObjectsUrl()}/${storagePath(sessionId, fileName)}?alt=media"
    }

    private suspend fun fetchSession(sessionId: String): SessionPayload {
        val response = client.get("${FirebaseConfig.databaseUrl}/$sessionId.json")
        val body = response.body<String>()
        if (body == "null" || body.isBlank()) {
            return SessionPayload()
        }
        return jsonFormat.decodeFromString(SessionPayload.serializer(), body)
    }

    private suspend fun writeSession(sessionId: String, payload: SessionPayload) {
        client.put("${FirebaseConfig.databaseUrl}/$sessionId.json") {
            contentType(ContentType.Application.Json)
            setBody(jsonFormat.encodeToString(SessionPayload.serializer(), payload))
        }
    }

    private fun storageObjectsUrl(): String {
        return "https://firebasestorage.googleapis.com/v0/b/${FirebaseConfig.storageBucket}/o"
    }

    private fun storagePath(sessionId: String, fileName: String): String {
        return "$sessionId/$fileName".encodeURLPathPart()
    }
}

expect fun createHttpClient(): HttpClient
