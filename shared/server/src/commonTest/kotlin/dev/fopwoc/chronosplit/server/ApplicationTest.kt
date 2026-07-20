package dev.fopwoc.chronosplit.server

import dev.fopwoc.chronosplit.model.MobileAuthHeader
import dev.fopwoc.chronosplit.model.MobileClientMessage
import dev.fopwoc.chronosplit.model.MobileServerMessage
import dev.fopwoc.chronosplit.model.MobileSessionHeader
import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.RunSnapshot
import dev.fopwoc.chronosplit.model.RunState
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.SegmentDefinition
import dev.fopwoc.chronosplit.model.StateMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ApplicationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun healthEndpointIsAvailableWithoutMobileState() = testApplication {
        application { relayModule() }

        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.NoContent, client.get("/api/state").status)
    }

    @Test
    fun mobileUpgradeRejectsInvalidAuth() = testApplication {
        application { relayModule(mobileAuthToken = "secret") }
        val websocketClient = createClient { install(WebSockets) }

        assertFailsWith<IllegalStateException> {
            websocketClient.webSocket(
                request = {
                    url.takeFrom("/api/mobile")
                    header(MobileSessionHeader, "mobile-1")
                    header(MobileAuthHeader, "wrong")
                },
            ) { }
        }
    }

    @Test
    fun mobileUpgradeAllowsEmptyAuthWhenRelayAuthIsEmpty() = testApplication {
        application { relayModule() }
        val websocketClient = createClient { install(WebSockets) }

        websocketClient.webSocket(
            request = {
                url.takeFrom("/api/mobile")
                header(MobileSessionHeader, "mobile-1")
            },
        ) { }
    }

    @Test
    fun secondMobileUpgradeIsRejectedWhileAnotherMobileIsConnected() = testApplication {
        application { relayModule() }
        val websocketClient = createClient { install(WebSockets) }
        val firstConnected = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        coroutineScope {
            val first = launch {
                websocketClient.webSocket(
                    request = {
                        url.takeFrom("/api/mobile")
                        header(MobileSessionHeader, "mobile-1")
                    },
                ) {
                    firstConnected.complete(Unit)
                    releaseFirst.await()
                }
            }

            firstConnected.await()
            assertFailsWith<IllegalStateException> {
                websocketClient.webSocket(
                    request = {
                        url.takeFrom("/api/mobile")
                        header(MobileSessionHeader, "mobile-2")
                    },
                ) { }
            }

            releaseFirst.complete(Unit)
            first.join()
        }
    }

    @Test
    fun authenticatedMobileCanPublishState() = testApplication {
        application { relayModule(mobileAuthToken = "secret") }
        val websocketClient = createClient { install(WebSockets) }
        val snapshot = RunSnapshot(
            definition = RunDefinition(
                id = "run",
                title = "Run",
                segments = listOf(SegmentDefinition("segment", "Segment")),
            ),
            state = RunState(runId = "run", status = RunStatus.RUNNING),
            capturedAtEpochMilliseconds = 1_000,
            elapsedMilliseconds = 0,
        )

        websocketClient.webSocket(
            request = {
                url.takeFrom("/api/mobile")
                header(MobileSessionHeader, "mobile-1")
                header(MobileAuthHeader, "secret")
            },
        ) {
            send(
                Frame.Text(
                    json.encodeToString<MobileClientMessage>(
                        MobileClientMessage.State(StateMessage(snapshot = snapshot)),
                    ),
                ),
            )
            val accepted = assertIs<MobileServerMessage.StateAccepted>(
                json.decodeFromString<MobileServerMessage>((incoming.receive() as Frame.Text).readText()),
            )
            assertEquals(snapshot.state.revision, accepted.revision)
        }

        assertEquals(HttpStatusCode.OK, client.get("/api/state").status)
    }
}
