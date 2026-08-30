package dev.fopwoc.chronosplit.android.ui.page.configuration.run

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
fun RunConfigurationScreen(
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

