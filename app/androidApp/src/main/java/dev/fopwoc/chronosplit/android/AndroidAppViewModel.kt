package dev.fopwoc.chronosplit.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import dev.fopwoc.chronosplit.app.presentation.RunBoardViewModel
import dev.fopwoc.chronosplit.app.session.MobileSession
import dev.fopwoc.chronosplit.app.session.createMobileSession
import dev.fopwoc.chronosplit.mobile.RelayConnectionState
import dev.fopwoc.chronosplit.mobile.StatePublisher
import dev.fopwoc.chronosplit.model.MobileWebSocketPingPeriodSeconds
import dev.fopwoc.chronosplit.model.LayoutDefinition
import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.SegmentDefinition
import dev.fopwoc.chronosplit.model.configurationIdForTitle
import dev.fopwoc.chronosplit.model.parseLs1lLayout
import dev.fopwoc.chronosplit.model.parseLssDocument
import dev.fopwoc.chronosplit.model.toEditorDraft
import dev.fopwoc.chronosplit.model.withEditorDraft
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class AndroidAppViewModel(application: Application) : AndroidViewModel(application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionId = Random.nextLong().toString(16)
    private var publisher: StatePublisher? = null
    private var publisherStateJob: Job? = null
    private val mutableRelayConnectionState = MutableStateFlow(RelayConnectionState.DISCONNECTED)

    val session: MobileSession = createMobileSession(
        databasePath = application.filesDir.resolve("chronosplit/history.db").path,
        now = ::now,
    )
    val runBoardViewModel = RunBoardViewModel(session)
    val configurations = runBoardViewModel.configurations
    val history = session.history
    val relayConnectionState: StateFlow<RelayConnectionState> =
        mutableRelayConnectionState.asStateFlow()

    var runTitle by mutableStateOf("New Run")
    var gameName by mutableStateOf("")
    var categoryName by mutableStateOf("")
    var configurationIconBase64 by mutableStateOf<String?>(null)
    val editableSegments = mutableStateListOf<EditableSegment>()
    var layoutDraft by mutableStateOf(LayoutDefinition().toEditorDraft())
    var relayUrl by mutableStateOf("http://10.0.2.2:8080")
    var relayToken by mutableStateOf("")
    var selectedConfigurationId by mutableStateOf("")

    private var editorHydrated = false

    init {
        scope.launch {
            session.snapshot.collect { snapshot ->
                publisher?.let { target -> runCatching { target.publish(snapshot) } }
            }
        }
        scope.launch {
            configurations.collect { definitions ->
                if (!editorHydrated && definitions.isNotEmpty()) {
                    val active = definitions.firstOrNull { it.id == session.snapshot.value.definition.id }
                        ?: definitions.first()
                    selectedConfigurationId = active.id
                    loadEditor(active)
                    editorHydrated = true
                }
            }
        }
    }

    fun applyConfiguration(): Boolean {
        val title = runTitle.trim()
        val segments = editableSegments.filter { it.name.isNotBlank() }
        if (title.isBlank() || segments.isEmpty()) return false

        val previous = configurations.value.firstOrNull { it.id == selectedConfigurationId }

        val definition = RunDefinition(
            id = selectedConfigurationId.ifBlank { configurationIdForTitle(title) },
            title = title,
            gameName = gameName.trim().takeIf(String::isNotEmpty),
            categoryName = categoryName.trim().takeIf(String::isNotEmpty),
            gameIconPngBase64 = configurationIconBase64,
            attemptCount = previous?.attemptCount ?: 0,
            offsetMilliseconds = previous?.offsetMilliseconds ?: 0,
            layout = previous?.layout ?: session.snapshot.value.definition.layout,
            segments = segments.mapIndexed { index, segment ->
                SegmentDefinition(
                    id = segment.id.ifBlank { "segment-${index + 1}" },
                    title = segment.name.trim(),
                    iconPngBase64 = segment.iconPngBase64,
                    personalBestTimeMilliseconds = parseEditorTime(segment.splitTime),
                    goldTimeMilliseconds = parseEditorTime(segment.bestSegment),
                )
            },
        )
        val saved = if (selectedConfigurationId.isBlank()) {
            runBoardViewModel.createConfiguration(definition)
        } else {
            runBoardViewModel.configure(definition, preserveGoldSplits = false)
            definition
        }
        selectedConfigurationId = saved.id
        loadEditor(saved)
        return true
    }

    fun selectConfiguration(id: String) {
        val selected = configurations.value.firstOrNull { it.id == id } ?: return
        if (!runBoardViewModel.selectConfiguration(id)) return
        selectedConfigurationId = id
        loadEditor(selected)
    }

    fun refreshConfigurationEditor() {
        loadEditor(runBoardViewModel.currentConfiguration())
        selectedConfigurationId = runBoardViewModel.currentConfiguration().id
    }

    fun startNewConfiguration() {
        selectedConfigurationId = ""
        runTitle = "New Run"
        gameName = ""
        categoryName = ""
        configurationIconBase64 = null
        editableSegments.clear()
        repeat(3) { index -> editableSegments += EditableSegment("segment-${index + 1}", "Segment ${index + 1}") }
    }

    fun copySelectedConfiguration(): Boolean {
        val copied = runBoardViewModel.copyConfiguration(selectedConfigurationId) ?: return false
        selectedConfigurationId = copied.id
        loadEditor(copied)
        return true
    }

    fun deleteSelectedConfiguration(): Boolean {
        if (!runBoardViewModel.deleteConfiguration(selectedConfigurationId)) return false
        val next = configurations.value.firstOrNull { it.id != selectedConfigurationId }
        if (next == null) startNewConfiguration() else selectConfiguration(next.id)
        return true
    }

    fun addSegment() {
        val number = editableSegments.size + 1
        editableSegments += EditableSegment("segment-$number", "Segment $number")
    }

    fun removeSegment(index: Int) {
        if (editableSegments.size > 1 && index in editableSegments.indices) editableSegments.removeAt(index)
    }

    fun updateSegment(index: Int, segment: EditableSegment) {
        if (index in editableSegments.indices) editableSegments[index] = segment
    }

    fun segmentTimeForEditor(index: Int): String {
        val split = editableSegments.getOrNull(index)?.splitTime?.let(::parseEditorTime) ?: return "—"
        val previous = editableSegments.getOrNull(index - 1)?.splitTime?.let(::parseEditorTime) ?: 0L
        return formatEditorTime((split - previous).coerceAtLeast(0))
    }

    fun sumOfBestForEditor(): String {
        val bestSegments = editableSegments.map { parseEditorTime(it.bestSegment) }
        if (bestSegments.any { it == null }) return "—"
        return formatEditorTime(bestSegments.sumOf { requireNotNull(it) })
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun setConfigurationIcon(bytes: ByteArray) {
        configurationIconBase64 = Base64.encode(bytes)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun setSegmentIcon(index: Int, bytes: ByteArray) {
        if (index in editableSegments.indices) {
            editableSegments[index] = editableSegments[index].copy(iconPngBase64 = Base64.encode(bytes))
        }
    }

    fun importLayout(content: String): Boolean = runCatching {
        runBoardViewModel.importLayout(parseLs1lLayout(content))
        layoutDraft = runBoardViewModel.currentConfiguration().layout.toEditorDraft()
        true
    }.getOrDefault(false)

    fun importRun(content: String): Boolean = runCatching {
        val imported = runBoardViewModel.importRun(parseLssDocument(content))
        selectedConfigurationId = imported.id
        loadEditor(imported)
        true
    }.getOrDefault(false)

    fun applyLayout() {
        val current = runBoardViewModel.currentConfiguration()
        runBoardViewModel.importLayout(current.layout.withEditorDraft(layoutDraft))
    }

    fun exportCurrentRun(): String = runBoardViewModel.exportCurrentRun()

    fun exportCurrentLayout(): String = runBoardViewModel.exportCurrentLayout()

    fun connectRelay() {
        publisherStateJob?.cancel()
        publisher?.close()

        val target = StatePublisher(
            baseUrl = relayUrl,
            authToken = relayToken,
            sessionId = sessionId,
            client = relayClient(),
        )
        publisher = target
        publisherStateJob = scope.launch {
            target.connectionState.collect { state -> mutableRelayConnectionState.value = state }
        }
        scope.launch { runCatching { target.publish(session.snapshot.value, force = true) } }
    }

    fun syncRelay() {
        publisher?.let { target ->
            scope.launch { runCatching { target.publish(session.snapshot.value, force = true) } }
        }
    }

    override fun onCleared() {
        publisherStateJob?.cancel()
        publisher?.close()
        scope.cancel()
        runBoardViewModel.close()
        super.onCleared()
    }

    private fun loadEditor(definition: RunDefinition) {
        runTitle = definition.title
        gameName = definition.gameName.orEmpty()
        categoryName = definition.categoryName.orEmpty()
        configurationIconBase64 = definition.gameIconPngBase64
        editableSegments.clear()
        editableSegments += definition.segments.map { segment ->
            EditableSegment(
                id = segment.id,
                name = segment.title,
                iconPngBase64 = segment.iconPngBase64,
                splitTime = formatEditorTime(segment.personalBestTimeMilliseconds),
                bestSegment = formatEditorTime(segment.goldTimeMilliseconds),
            )
        }
        layoutDraft = definition.layout.toEditorDraft()
    }

    private companion object {
        @OptIn(ExperimentalTime::class)
        fun now(): Long = Clock.System.now().toEpochMilliseconds()

        fun relayClient() = HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = MobileWebSocketPingPeriodSeconds.seconds
            }
        }
    }
}

data class EditableSegment(
    val id: String,
    val name: String,
    val iconPngBase64: String? = null,
    val splitTime: String = "",
    val bestSegment: String = "",
)

private fun parseEditorTime(value: String): Long? {
    val raw = value.trim()
    if (raw.isEmpty()) return null
    val parts = raw.split(':')
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val minutes = parts.getOrNull(parts.lastIndex - 1)?.toLongOrNull() ?: 0
    val hours = parts.getOrNull(parts.lastIndex - 2)?.toLongOrNull() ?: 0
    return ((hours * 3_600 + minutes * 60) * 1_000 + seconds * 1_000).toLong()
}

private fun formatEditorTime(milliseconds: Long?): String {
    milliseconds ?: return ""
    val minutes = milliseconds / 60_000
    val seconds = milliseconds / 1_000 % 60
    val tenths = milliseconds % 1_000 / 100
    return "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
}
