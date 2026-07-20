package dev.fopwoc.chronosplit.server

import dev.fopwoc.chronosplit.model.MobileAuthHeader
import dev.fopwoc.chronosplit.model.MobileClientMessage
import dev.fopwoc.chronosplit.model.MobileServerMessage
import dev.fopwoc.chronosplit.model.MobileSessionHeader
import dev.fopwoc.chronosplit.model.MobileWebSocketPingPeriodSeconds
import dev.fopwoc.chronosplit.model.MobileWebSocketTimeoutSeconds
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

private val wireJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

const val DefaultMobileAuthToken = ""

private val mobileHandshakeKey = AttributeKey<MobileHandshake>("chronosplit.mobile.handshake")

private data class MobileHandshake(
    val sessionId: String,
)

fun Application.relayModule(
    store: RelayStore = RelayStore(),
    mobileAuthToken: String = DefaultMobileAuthToken,
) {
    install(ContentNegotiation) { json(wireJson) }
    install(WebSockets) {
        pingPeriod = MobileWebSocketPingPeriodSeconds.seconds
        timeout = MobileWebSocketTimeoutSeconds.seconds
    }

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path() != "/api/mobile" || !call.isWebSocketUpgrade()) return@intercept

        val sessionId = call.request.headers[MobileSessionHeader]
        val clientAuthToken = call.request.headers[MobileAuthHeader].orEmpty()
        when {
            sessionId.isNullOrBlank() -> {
                call.respond(HttpStatusCode.BadRequest, "Mobile session header is required")
                finish()
            }

            !authMatches(clientAuthToken, mobileAuthToken) -> {
                call.respond(HttpStatusCode.Unauthorized, "Invalid mobile auth token")
                finish()
            }

            !store.tryClaimMobileSession(sessionId) -> {
                call.respond(HttpStatusCode.Conflict, "Another mobile session is connected")
                finish()
            }

            else -> call.attributes.put(mobileHandshakeKey, MobileHandshake(sessionId))
        }
    }

    routing {
        get("/health") {
            call.respondText("ok")
        }

        get("/api/state") {
            val current = store.current.value
            if (current == null) call.respond(HttpStatusCode.NoContent)
            else call.respond(current)
        }

        webSocket("/api/view") {
            store.current.filterNotNull().collect { message ->
                send(Frame.Text(wireJson.encodeToString(message)))
            }
        }

        webSocket("/api/mobile") {
            val handshake = call.attributes[mobileHandshakeKey]
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val message = runCatching {
                        wireJson.decodeFromString<MobileClientMessage>(frame.readText())
                    }.getOrNull() ?: return@webSocket

                    when (message) {
                        is MobileClientMessage.State -> {
                            if (message.message.protocolVersion != 1) return@webSocket
                            store.publish(message.message)
                            send(
                                Frame.Text(
                                    wireJson.encodeToString<MobileServerMessage>(
                                        MobileServerMessage.StateAccepted(
                                            revision = message.message.snapshot.state.revision,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            } finally {
                store.releaseMobileSession(handshake.sessionId)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.isWebSocketUpgrade(): Boolean =
    request.headers[HttpHeaders.Upgrade].equals("websocket", ignoreCase = true) &&
        request.headers[HttpHeaders.Connection]
            ?.split(',')
            ?.any { it.trim().equals("upgrade", ignoreCase = true) } == true

private fun authMatches(clientToken: String, serverToken: String): Boolean {
    if (clientToken.isBlank() && serverToken.isBlank()) return true
    if (clientToken.isBlank() || serverToken.isBlank()) return false
    return secureEquals(clientToken, serverToken)
}

private fun secureEquals(actual: String, expected: String): Boolean {
    if (actual.length != expected.length) return false
    var difference = 0
    actual.indices.forEach { index -> difference = difference or (actual[index].code xor expected[index].code) }
    return difference == 0
}
