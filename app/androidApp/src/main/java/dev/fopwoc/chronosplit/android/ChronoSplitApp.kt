package dev.fopwoc.chronosplit.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.RequiresApi
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

@Serializable
private data object TimerRoute : NavKey

@Serializable
private data object ConfigurationRoute : NavKey

@Serializable
private data object RunConfigurationRoute : NavKey

@Serializable
private data object LayoutRoute : NavKey

@Serializable
private data object HistoryRoute : NavKey

@Serializable
private data object RelayRoute : NavKey

@ExperimentalMaterial3Api
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun ChronoSplitApp(
    appViewModel: AndroidAppViewModel = viewModel(),
) {
    ChronoSplitTheme {
        val backStack = rememberNavBackStack(TimerRoute)
        val activity = LocalContext.current.findActivity()
        val navigate: (NavKey) -> Unit = { route -> backStack += route }

        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) backStack.removeLast()
                else activity?.finish()
            },
            entryProvider = entryProvider {
                entry<TimerRoute> {
                    TimerScreen(
                        appViewModel = appViewModel,
                        onNavigate = navigate,
                    )
                }
                entry<ConfigurationRoute> {
                    ConfigurationHubScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenLayout = { navigate(LayoutRoute) },
                        onOpenRuns = { navigate(RunConfigurationRoute) },
                    )
                }
                entry<RunConfigurationRoute> {
                    RunConfigurationScreen(
                        appViewModel = appViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenHistory = { navigate(HistoryRoute) },
                    )
                }
                entry<LayoutRoute> {
                    LayoutSettingsScreen(
                        appViewModel = appViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<HistoryRoute> {
                    HistoryScreen(
                        appViewModel = appViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<RelayRoute> {
                    RelayScreen(
                        appViewModel = appViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}

@Composable
private fun ChronoSplitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) {
            darkColorScheme()
        } else {
            lightColorScheme()
        },
        content = content,
    )
}


@ExperimentalMaterial3Api
@Composable
private fun TimerScreen(
    appViewModel: AndroidAppViewModel,
    onNavigate: (NavKey) -> Unit,
) {
    val boardModel by appViewModel.runBoardViewModel.model.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChronoSplit") },
                actions = {
                    TextButton(onClick = { onNavigate(HistoryRoute) }) { Text("History") }
                    TextButton(onClick = { onNavigate(ConfigurationRoute) }) { Text("Configure") }
                    TextButton(onClick = { onNavigate(RelayRoute) }) { Text("Integration") }
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

@ExperimentalMaterial3Api
@Composable
private fun ConfigurationHubScreen(
    onBack: () -> Unit,
    onOpenLayout: () -> Unit,
    onOpenRuns: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Run data", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenRuns, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Run configurations")
                    Text(
                        "Select runs, edit splits and icons, inspect history, and import or export .lss files.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenLayout, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Layout")
                    Text(
                        "Preview the board, configure components and timing display, and import or export .ls1l files.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
private fun RunConfigurationScreen(
    appViewModel: AndroidAppViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var hasError by rememberSaveable { mutableStateOf(false) }
    var hasRunImportError by rememberSaveable { mutableStateOf(false) }
    var isConfigurationMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingIconIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val configurations by appViewModel.configurations.collectAsState()
    val context = LocalContext.current
    val runPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        }
        if (content == null || !appViewModel.importRun(content)) {
            hasRunImportError = true
        }
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bytes = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
        val target = pendingIconIndex
        if (bytes != null) {
            if (target == null) appViewModel.setConfigurationIcon(bytes)
            else appViewModel.setSegmentIcon(target, bytes)
        }
        pendingIconIndex = null
    }
    val runExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                writer.write(appViewModel.exportCurrentRun())
            }
        }
    }
    val selectedConfigurationTitle = configurations
        .firstOrNull { it.id == appViewModel.selectedConfigurationId }
        ?.title
        ?: "New configuration"

    LaunchedEffect(Unit) {
        appViewModel.refreshConfigurationEditor()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run configurations") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Saved configurations", style = MaterialTheme.typography.titleMedium) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { isConfigurationMenuExpanded = true }) {
                        Text(selectedConfigurationTitle)
                    }
                    DropdownMenu(
                        expanded = isConfigurationMenuExpanded,
                        onDismissRequest = { isConfigurationMenuExpanded = false },
                    ) {
                        configurations.forEach { configuration ->
                            DropdownMenuItem(
                                text = { Text(configuration.title) },
                                onClick = {
                                    isConfigurationMenuExpanded = false
                                    appViewModel.selectConfiguration(configuration.id)
                                },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = appViewModel::startNewConfiguration) {
                    Text("New")
                }
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = appViewModel::copySelectedConfiguration,
                    enabled = appViewModel.selectedConfigurationId.isNotBlank(),
                ) { Text("Copy") }
                OutlinedButton(
                    onClick = appViewModel::deleteSelectedConfiguration,
                    enabled = configurations.isNotEmpty(),
                ) { Text("Delete") }
                OutlinedButton(onClick = onOpenHistory) { Text("History") }
            } }
            item { RunBoardRoute(
                viewModel = appViewModel.runBoardViewModel,
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)),
            ) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                onClick = {
                    runPicker.launch(arrayOf("application/xml", "text/xml", "text/*", "application/octet-stream"))
                },
                ) { Text("Import .lss") }
                OutlinedButton(
                    onClick = { runExporter.launch("${appViewModel.runTitle.ifBlank { "run" }}.lss") },
                    enabled = appViewModel.selectedConfigurationId.isNotBlank(),
                ) { Text("Export .lss") }
            } }
            item { Text("Run", style = MaterialTheme.typography.titleMedium) }
            item { OutlinedTextField(
                value = appViewModel.runTitle,
                onValueChange = {
                    appViewModel.runTitle = it
                    hasError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
                isError = hasError,
            ) }
            item { OutlinedTextField(
                value = appViewModel.gameName,
                onValueChange = { appViewModel.gameName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Game name") },
                singleLine = true,
            ) }
            item { OutlinedTextField(
                value = appViewModel.categoryName,
                onValueChange = { appViewModel.categoryName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category") },
                singleLine = true,
            ) }
            item { OutlinedButton(
                onClick = {
                    pendingIconIndex = null
                    iconPicker.launch(arrayOf("image/png", "image/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (appViewModel.configurationIconBase64 == null) "Add run icon" else "Replace run icon") } }
            item { Text("Splits", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sum of Best", style = MaterialTheme.typography.titleSmall)
                    Text(appViewModel.sumOfBestForEditor(), style = MaterialTheme.typography.titleSmall)
                }
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Icon · Segment name · Split time · Segment time · Best segment", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = appViewModel::addSegment) { Text("Add") }
            } }
            itemsIndexed(appViewModel.editableSegments, key = { _, segment -> segment.id }) { index, segment ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                pendingIconIndex = index
                                iconPicker.launch(arrayOf("image/png", "image/*"))
                            }) { Text(if (segment.iconPngBase64 == null) "Icon" else "Change icon") }
                            OutlinedTextField(
                                value = segment.name,
                                onValueChange = { appViewModel.updateSegment(index, segment.copy(name = it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Segment name") },
                                singleLine = true,
                            )
                            TextButton(onClick = { appViewModel.removeSegment(index) }) { Text("Remove") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = segment.splitTime,
                                onValueChange = { appViewModel.updateSegment(index, segment.copy(splitTime = it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Split time") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = appViewModel.segmentTimeForEditor(index),
                                onValueChange = {},
                                modifier = Modifier.weight(1f),
                                label = { Text("Segment time") },
                                enabled = false,
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = segment.bestSegment,
                                onValueChange = { appViewModel.updateSegment(index, segment.copy(bestSegment = it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Best segment") },
                                placeholder = { Text("—") },
                                singleLine = true,
                            )
                        }
                    }
                }
            }
            item { Button(
                onClick = {
                    if (appViewModel.applyConfiguration()) onBack() else hasError = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use Configuration")
            } }
            if (hasRunImportError) item {
                Text(
                    "Could not import this .lss run.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
private fun LayoutSettingsScreen(
    appViewModel: AndroidAppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var importError by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val content = uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } }
        importError = content == null || !appViewModel.importLayout(content)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(appViewModel.exportCurrentLayout()) } }
    }
    val draft = appViewModel.layoutDraft

    Scaffold(
        topBar = { TopAppBar(title = { Text("Layout settings") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { RunBoardRoute(appViewModel.runBoardViewModel, Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp))) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) { Text("Import .ls1l") }
                OutlinedButton(onClick = { exporter.launch("layout.ls1l") }) { Text("Export .ls1l") }
            } }
            item { LayoutSwitch("Title", draft.titleEnabled) { appViewModel.layoutDraft = draft.copy(titleEnabled = it) } }
            item { LayoutSwitch("Game name", draft.showGameName) { appViewModel.layoutDraft = draft.copy(showGameName = it) } }
            item { LayoutSwitch("Category", draft.showCategoryName) { appViewModel.layoutDraft = draft.copy(showCategoryName = it) } }
            item { LayoutSwitch("Attempt count", draft.showAttemptCount) { appViewModel.layoutDraft = draft.copy(showAttemptCount = it) } }
            item { LayoutSwitch("Previous / live segment", draft.previousSegmentEnabled) { appViewModel.layoutDraft = draft.copy(previousSegmentEnabled = it) } }
            item { LayoutSwitch("Thin separators", draft.showThinSeparators) { appViewModel.layoutDraft = draft.copy(showThinSeparators = it) } }
            item { LayoutSwitch("Fill blank split rows", draft.fillWithBlankSpace) { appViewModel.layoutDraft = draft.copy(fillWithBlankSpace = it) } }
            item { LayoutSwitch("Always show final split", draft.alwaysShowLastSplit) { appViewModel.layoutDraft = draft.copy(alwaysShowLastSplit = it) } }
            item { LayoutSwitch("Column labels", draft.showColumnLabels) { appViewModel.layoutDraft = draft.copy(showColumnLabels = it) } }
            item { OutlinedTextField(
                value = draft.visualSplitCount?.toString().orEmpty(),
                onValueChange = { appViewModel.layoutDraft = draft.copy(visualSplitCount = it.toIntOrNull()) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Visible split count (automatic when empty)") },
                singleLine = true,
            ) }
            item { OutlinedTextField(
                value = draft.splitPreviewCount.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { appViewModel.layoutDraft = draft.copy(splitPreviewCount = it) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Split preview count") },
                singleLine = true,
            ) }
            item { AccuracyPicker("Split time accuracy", draft.splitTimeAccuracy) { appViewModel.layoutDraft = draft.copy(splitTimeAccuracy = it) } }
            item { AccuracyPicker("Delta accuracy", draft.deltaTimeAccuracy) { appViewModel.layoutDraft = draft.copy(deltaTimeAccuracy = it) } }
            item { LayoutSwitch("Segment timer", draft.segmentTimer) { appViewModel.layoutDraft = draft.copy(segmentTimer = it) } }
            item { LayoutSwitch("Timer gradient", draft.timerGradient) { appViewModel.layoutDraft = draft.copy(timerGradient = it) } }
            item { AccuracyPicker("Timer accuracy", draft.timerAccuracy) { appViewModel.layoutDraft = draft.copy(timerAccuracy = it) } }
            item { Button(onClick = { appViewModel.applyLayout(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save Layout") } }
            if (importError) item { Text("Could not import this .ls1l layout.", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun LayoutSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccuracyPicker(
    label: String,
    selected: LayoutAccuracy,
    onSelected: (LayoutAccuracy) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected.name.lowercase().replaceFirstChar(Char::uppercase)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LayoutAccuracy.entries.forEach { accuracy ->
                DropdownMenuItem(text = { Text(accuracy.name.lowercase().replaceFirstChar(Char::uppercase)) }, onClick = {
                    expanded = false
                    onSelected(accuracy)
                })
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
private fun HistoryScreen(
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

@ExperimentalMaterial3Api
@Composable
private fun RelayScreen(
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
