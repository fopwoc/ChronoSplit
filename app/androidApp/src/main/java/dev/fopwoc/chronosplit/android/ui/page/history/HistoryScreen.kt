package dev.fopwoc.chronosplit.android.ui.page.history

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
fun HistoryScreen(
    appViewModel: AndroidAppViewModel,
    onBack: () -> Unit,
) {
    val history by appViewModel.history.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configuration history") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No attempts yet", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { attempt ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(attempt.runTitle, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (attempt.startedAtEpochMilliseconds > 0) {
                                    DateFormat.getDateTimeInstance().format(Date(attempt.startedAtEpochMilliseconds))
                                } else {
                                    "Imported LiveSplit attempt"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (attempt.completed) {
                                    "Completed · ${attempt.completedSegmentCount} segments"
                                } else {
                                    "Interrupted · ${attempt.completedSegmentCount} segments"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            attempt.elapsedMilliseconds?.let { elapsed ->
                                Text("Time ${elapsed / 60_000}:${((elapsed / 1_000) % 60).toString().padStart(2, '0')}.${(elapsed % 1_000 / 100)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

