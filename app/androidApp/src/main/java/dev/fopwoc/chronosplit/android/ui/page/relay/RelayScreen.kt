package dev.fopwoc.chronosplit.android.ui.page.relay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.fopwoc.chronosplit.app.presentation.RunBoardRoute
import dev.fopwoc.chronosplit.mobile.RelayConnectionState
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.LayoutAccuracy
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import dev.fopwoc.chronosplit.android.AndroidAppViewModel

@ExperimentalMaterial3Api
@Composable
fun RelayScreen(
    appViewModel: AndroidAppViewModel,
    onBack: () -> Unit,
) {
    val connectionState by appViewModel.relayConnectionState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Integration") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("OBS relay", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = appViewModel.relayUrl,
                onValueChange = { appViewModel.relayUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Relay URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = appViewModel.relayToken,
                onValueChange = { appViewModel.relayToken = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mobile auth token") },
                singleLine = true,
            )
            Button(onClick = appViewModel::connectRelay) {
                Text("Connect")
            }
            Text(connectionState.label())
            Text("The mobile app owns the run and history. Relays mirror the latest state.")
        }
    }
}

private fun RelayConnectionState.label(): String = when (this) {
    RelayConnectionState.DISCONNECTED -> "Disconnected"
    RelayConnectionState.CONNECTING -> "Connecting…"
    RelayConnectionState.CONNECTED -> "Connected"
    RelayConnectionState.AUTHENTICATION_FAILED -> "Authentication failed"
    RelayConnectionState.SESSION_BUSY -> "Another mobile is already connected"
}

