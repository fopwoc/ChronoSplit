package dev.fopwoc.chronosplit.mobile

import dev.fopwoc.chronosplit.model.MobileAuthHeader
import dev.fopwoc.chronosplit.model.MobileClientMessage
import dev.fopwoc.chronosplit.model.MobileServerMessage
import dev.fopwoc.chronosplit.model.MobileSessionHeader
import dev.fopwoc.chronosplit.model.RunSnapshot
import dev.fopwoc.chronosplit.model.RunState
import dev.fopwoc.chronosplit.model.StateMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

enum class RelayConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATION_FAILED,
    SESSION_BUSY,
}

class StatePublisher(
    private val baseUrl: String,
    private val authToken: String,
    private val sessionId: String,
    private val client: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingStates = Channel<RunSnapshot>(Channel.CONFLATED)
    private val snapshotMutex = Mutex()
    private val mutableConnectionState = kotlinx.coroutines.flow.MutableStateFlow(
        RelayConnectionState.DISCONNECTED,
    )
    private var latestSnapshot: RunSnapshot? = null
    private var lastQueuedState: RunState? = null

    val connectionState = mutableConnectionState.asStateFlow()

    init {
        scope.launch { connectionLoop() }
    }

    suspend fun publish(snapshot: RunSnapshot, force: Boolean = false) {
        val shouldQueue = snapshotMutex.withLock {
            latestSnapshot = snapshot
            if (!force && snapshot.state == lastQueuedState) {
                false
            } else {
                lastQueuedState = snapshot.state
                true
            }
        }
        if (shouldQueue) pendingStates.trySend(snapshot)
    }

    fun isConnected(): Boolean = connectionState.value == RelayConnectionState.CONNECTED

    fun close() {
        scope.cancel()
        client.close()
    }

    private suspend fun connectionLoop() {
        var retryDelayMilliseconds = InitialRetryDelayMilliseconds

        while (currentCoroutineContext().isActive) {
            mutableConnectionState.value = RelayConnectionState.CONNECTING
            try {
                client.webSocket(
                    request = {
                        url.takeFrom(webSocketUrl())
                        header(MobileSessionHeader, sessionId)
                        if (authToken.isNotBlank()) header(MobileAuthHeader, authToken)
                    },
                ) {
                    mutableConnectionState.value = RelayConnectionState.CONNECTED
                    retryDelayMilliseconds = InitialRetryDelayMilliseconds

                    while (pendingStates.tryReceive().isSuccess) Unit
                    snapshotMutex.withLock { latestSnapshot }?.let { snapshot ->
                        sendState(snapshot)
                    }

                    val senderJob = launch {
                        for (snapshot in pendingStates) sendState(snapshot)
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                wireJson.decodeFromString<MobileServerMessage>(frame.readText())
                            }
                        }
                    } finally {
                        senderJob.cancel()
                    }
                }

                mutableConnectionState.value = RelayConnectionState.DISCONNECTED
                retryDelayMilliseconds = waitBeforeRetry(retryDelayMilliseconds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (response: ResponseException) {
                when (response.response.status) {
                    HttpStatusCode.Unauthorized -> {
                        mutableConnectionState.value = RelayConnectionState.AUTHENTICATION_FAILED
                        return
                    }

                    HttpStatusCode.Conflict -> {
                        mutableConnectionState.value = RelayConnectionState.SESSION_BUSY
                        retryDelayMilliseconds = waitBeforeRetry(retryDelayMilliseconds)
                    }

                    else -> {
                        mutableConnectionState.value = RelayConnectionState.DISCONNECTED
                        retryDelayMilliseconds = waitBeforeRetry(retryDelayMilliseconds)
                    }
                }
            } catch (_: Throwable) {
                mutableConnectionState.value = RelayConnectionState.DISCONNECTED
                retryDelayMilliseconds = waitBeforeRetry(retryDelayMilliseconds)
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendState(snapshot: RunSnapshot) {
        send(
            Frame.Text(
                wireJson.encodeToString<MobileClientMessage>(
                    MobileClientMessage.State(StateMessage(snapshot = snapshot)),
                ),
            ),
        )
    }

    private suspend fun waitBeforeRetry(currentDelayMilliseconds: Long): Long {
        delay(currentDelayMilliseconds)
        return (currentDelayMilliseconds * 2).coerceAtMost(MaxRetryDelayMilliseconds)
    }

    private fun webSocketUrl(): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val url = when {
            normalizedBaseUrl.startsWith("https://") -> "wss://${normalizedBaseUrl.removePrefix("https://")}"
            normalizedBaseUrl.startsWith("http://") -> "ws://${normalizedBaseUrl.removePrefix("http://")}"
            normalizedBaseUrl.startsWith("wss://") || normalizedBaseUrl.startsWith("ws://") -> normalizedBaseUrl
            else -> "ws://$normalizedBaseUrl"
        }
        return "$url/api/mobile"
    }

    private companion object {
        const val InitialRetryDelayMilliseconds = 1_000L
        const val MaxRetryDelayMilliseconds = 30_000L

        val wireJson = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}

fun statePublisher(
    baseUrl: String,
    authToken: String,
    sessionId: String,
    client: HttpClient,
): StatePublisher = StatePublisher(baseUrl, authToken, sessionId, client)
