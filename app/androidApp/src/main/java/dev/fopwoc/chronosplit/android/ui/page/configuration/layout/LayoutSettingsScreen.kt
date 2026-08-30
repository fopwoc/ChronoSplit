package dev.fopwoc.chronosplit.android.ui.page.configuration.layout

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
fun LayoutSettingsScreen(
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

