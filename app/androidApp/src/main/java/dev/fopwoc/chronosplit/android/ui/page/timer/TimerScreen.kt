package dev.fopwoc.chronosplit.android.ui.page.timer

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
fun TimerScreen(
    appViewModel: AndroidAppViewModel,
    onOpenHistory: () -> Unit,
    onOpenConfiguration: () -> Unit,
    onOpenRelay: () -> Unit,
) {
    val boardModel by appViewModel.runBoardViewModel.model.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChronoSplit") },
                actions = {
                    TextButton(onClick = onOpenHistory) { Text("History") }
                    TextButton(onClick = onOpenConfiguration) { Text("Configure") }
                    TextButton(onClick = onOpenRelay) { Text("Integration") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RunBoardRoute(
                viewModel = appViewModel.runBoardViewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = appViewModel.runBoardViewModel::togglePause,
                    enabled = boardModel.status == RunStatus.RUNNING || boardModel.status == RunStatus.PAUSED,
                ) {
                    Text(boardModel.pauseActionTitle)
                }
                Button(
                    onClick = appViewModel.runBoardViewModel::primaryAction,
                    modifier = Modifier.weight(1f),
                    enabled = boardModel.status == RunStatus.READY || boardModel.status == RunStatus.RUNNING,
                ) {
                    Text(boardModel.primaryActionTitle)
                }
                OutlinedButton(onClick = appViewModel.runBoardViewModel::reset) {
                    Text("Reset")
                }
            }
        }
    }
}

