package dev.fopwoc.chronosplit.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.fopwoc.chronosplit.model.StateMessage
import dev.fopwoc.chronosplit.presentation.RelayView
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.w3c.dom.WebSocket

private val json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
fun main() {
    var state by mutableStateOf<StateMessage?>(null)
    val websocketScheme = if (window.location.protocol == "https:") "wss" else "ws"
    val socket = WebSocket("$websocketScheme://${window.location.host}/api/view")
    socket.onmessage = { event ->
        state = json.decodeFromString<StateMessage>(event.data.toString())
    }

    ComposeViewport(document.body!!) {
        RelayView(state)
    }
}
