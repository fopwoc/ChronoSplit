package dev.fopwoc.chronosplit.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.fopwoc.chronosplit.presentation.RelayView
import dev.fopwoc.chronosplit.server.RelayStore
import dev.fopwoc.chronosplit.server.relayModule
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val store = RelayStore()
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val mobileAuthToken = System.getenv("MOBILE_AUTH_TOKEN").orEmpty()
    val server = embeddedServer(Netty, host = "0.0.0.0", port = port) {
        relayModule(store, mobileAuthToken)
    }
        .start(wait = false)

    application {
        Window(
            onCloseRequest = {
                server.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
                exitApplication()
            },
            title = "ChronoSplit Relay :$port",
            state = rememberWindowState(width = 360.dp, height = 560.dp),
        ) {
            val message by store.current.collectAsState()
            RelayView(message)
        }
    }
}
