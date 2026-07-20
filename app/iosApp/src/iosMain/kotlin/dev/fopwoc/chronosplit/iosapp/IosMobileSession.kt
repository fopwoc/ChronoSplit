package dev.fopwoc.chronosplit.iosapp

import androidx.compose.ui.window.ComposeUIViewController
import dev.fopwoc.chronosplit.app.presentation.RunBoardRoute
import dev.fopwoc.chronosplit.app.presentation.RunBoardViewModel
import dev.fopwoc.chronosplit.app.session.AttemptDetail
import dev.fopwoc.chronosplit.app.session.ConfigurationSummary
import dev.fopwoc.chronosplit.app.session.createMobileSession
import dev.fopwoc.chronosplit.mobile.RelayConnectionState
import dev.fopwoc.chronosplit.mobile.StatePublisher
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.parseLs1lLayout
import dev.fopwoc.chronosplit.model.parseLssDocument
import dev.fopwoc.chronosplit.model.parseConfigurationDraftJson
import dev.fopwoc.chronosplit.model.parseLayoutEditorDraftJson
import dev.fopwoc.chronosplit.model.toConfigurationDraftJson
import dev.fopwoc.chronosplit.model.toEditorDraftJson
import dev.fopwoc.chronosplit.model.toRunBoardModel
import dev.fopwoc.chronosplit.model.toRunDefinition
import dev.fopwoc.chronosplit.model.withEditorDraft
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import dev.fopwoc.chronosplit.model.MobileWebSocketPingPeriodSeconds
import platform.UIKit.UIViewController

class IosMobileSession(databasePath: String) {
    private val session = createMobileSession(databasePath, ::now)
    private val runBoardViewModel = RunBoardViewModel(session)
    private val scope = MainScope()
    private val mobileSessionId = Random.nextLong().toString(16)
    private var publisher: StatePublisher? = null
    private var attemptDetailsObserver: ((List<AttemptDetail>) -> Unit)? = null

    init {
        scope.launch {
            session.snapshot.collectLatest { snapshot ->
                publisher?.let { target -> runCatching { target.publish(snapshot) } }
            }
        }
        scope.launch {
            session.attemptDetails.collectLatest { details ->
                latestAttemptDetails = details
                attemptDetailsObserver?.invoke(details)
            }
        }
    }

    private var latestAttemptDetails: List<AttemptDetail> = emptyList()

    fun observeAttemptDetails(observer: (List<AttemptDetail>) -> Unit) {
        attemptDetailsObserver = observer
        observer(latestAttemptDetails)
    }

    fun currentConfigurationSummaries(): List<ConfigurationSummary> =
        session.configurations.value.map { definition ->
            ConfigurationSummary(
                id = definition.id,
                title = definition.title,
                segmentCount = definition.segments.size,
                gameName = definition.gameName,
                categoryName = definition.categoryName,
                iconPngBase64 = definition.gameIconPngBase64,
            )
        }

    fun primaryAction() = runBoardViewModel.primaryAction()

    fun togglePause() = runBoardViewModel.togglePause()

    fun reset() = runBoardViewModel.reset()

    fun makeRunBoardViewController(onSegmentClick: (() -> Unit)?): UIViewController = ComposeUIViewController {
        RunBoardRoute(runBoardViewModel, onSegmentClick = onSegmentClick)
    }

    fun primaryActionTitle(): String = runBoardViewModel.primaryActionTitle()

    fun pauseActionTitle(): String = runBoardViewModel.pauseActionTitle()

    fun selectConfiguration(id: String): Boolean = runBoardViewModel.selectConfiguration(id)

    fun importLayout(layoutJson: String): Boolean = runCatching {
        runBoardViewModel.importLayout(parseLs1lLayout(layoutJson))
        true
    }.getOrDefault(false)

    fun importRun(runXml: String): Boolean = runCatching {
        runBoardViewModel.importRun(parseLssDocument(runXml))
        true
    }.getOrDefault(false)

    fun currentConfigurationDraftJson(): String =
        runBoardViewModel.currentConfiguration().toConfigurationDraftJson()

    fun saveConfigurationDraftJson(content: String, createNew: Boolean): Boolean = runCatching {
        val draft = parseConfigurationDraftJson(content)
        val current = runBoardViewModel.currentConfiguration()
        val definition = draft.toRunDefinition(
            layout = if (createNew) current.layout else {
                runBoardViewModel.configuration(draft.id.orEmpty())?.layout ?: current.layout
            },
        )
        if (createNew) runBoardViewModel.createConfiguration(definition)
        else runBoardViewModel.configure(definition, preserveGoldSplits = false)
        true
    }.getOrDefault(false)

    fun copyConfiguration(id: String): Boolean = runBoardViewModel.copyConfiguration(id) != null

    fun deleteConfiguration(id: String): Boolean = runBoardViewModel.deleteConfiguration(id)

    fun currentLayoutDraftJson(): String =
        runBoardViewModel.currentConfiguration().layout.toEditorDraftJson()

    fun saveLayoutDraftJson(content: String): Boolean = runCatching {
        val current = runBoardViewModel.currentConfiguration()
        runBoardViewModel.importLayout(current.layout.withEditorDraft(parseLayoutEditorDraftJson(content)))
        true
    }.getOrDefault(false)

    fun previewLayoutDraftJson(content: String): Boolean = runCatching {
        val current = runBoardViewModel.currentConfiguration()
        runBoardViewModel.previewLayout(current.layout.withEditorDraft(parseLayoutEditorDraftJson(content)))
        true
    }.getOrDefault(false)

    fun previewConfigurationDraftJson(content: String): Boolean = runCatching {
        val draft = parseConfigurationDraftJson(content)
        val current = runBoardViewModel.currentConfiguration()
        val layout = draft.id
            ?.let(runBoardViewModel::configuration)
            ?.layout
            ?: current.layout
        runBoardViewModel.previewConfiguration(draft.toRunDefinition(layout))
        true
    }.getOrDefault(false)

    fun clearRunBoardPreview() = runBoardViewModel.clearPreview()

    fun exportCurrentRun(): String = runBoardViewModel.exportCurrentRun()

    fun exportCurrentLayout(): String = runBoardViewModel.exportCurrentLayout()

    fun currentConfigurationId(): String =
        session.snapshot.value.definition.id.takeIf { id -> session.configurations.value.any { it.id == id } }.orEmpty()

    fun hasConfigurations(): Boolean = session.configurations.value.isNotEmpty()

    fun configureRelay(baseUrl: String, authToken: String) {
        publisher?.close()
        val target = StatePublisher(
            baseUrl = baseUrl,
            authToken = authToken,
            sessionId = mobileSessionId,
            client = relayClient(),
        )
        publisher = target
        val current = session.snapshot.value
        scope.launch { runCatching { target.publish(current, force = true) } }
    }

    fun syncRelay() {
        val current = session.snapshot.value
        publisher?.let { target ->
            scope.launch { runCatching { target.publish(current, force = true) } }
        }
    }

    fun isRelayConnected(): Boolean = publisher?.isConnected() == true

    fun relayConnectionState(): String =
        (publisher?.connectionState?.value ?: RelayConnectionState.DISCONNECTED).name

    fun isRunning(): Boolean = session.snapshot.value.state.status == RunStatus.RUNNING

    fun isReadyForRemoteCommands(): Boolean = session.configurations.value.isNotEmpty()

    fun watchRunState(): IosWatchRunState {
        val timestamp = now()
        // Remote commands and their replies run synchronously. Reading the collected
        // snapshot here can lag one command behind because its collector resumes later.
        val snapshot = session.snapshot.value
        val board = snapshot.toRunBoardModel(
            nowEpochMilliseconds = timestamp,
            primaryActionTitle = primaryActionTitle(),
            pauseActionTitle = pauseActionTitle(),
        )
        val segment = board.segments.firstOrNull { it.isActive } ?: board.segments.last()
        val delta = segment.comparisonDeltaMilliseconds

        return IosWatchRunState(
            configurationId = snapshot.definition.id,
            configurationTitle = snapshot.definition.title,
            segmentName = segment.title,
            segmentIndex = snapshot.state.activeSegmentIndex.coerceIn(snapshot.definition.segments.indices),
            segmentCount = snapshot.definition.segments.size,
            status = snapshot.state.status.name,
            elapsedMilliseconds = board.elapsedMilliseconds,
            capturedAtEpochMilliseconds = timestamp,
            deltaMilliseconds = delta ?: 0,
            hasDelta = delta != null,
            primaryActionTitle = board.primaryActionTitle,
            pauseActionTitle = board.pauseActionTitle,
            relayConnected = isRelayConnected(),
        )
    }

    private companion object {
        fun relayClient() = HttpClient(Darwin.create()) {
            install(WebSockets) {
                pingInterval = MobileWebSocketPingPeriodSeconds.seconds
            }
        }
        @OptIn(ExperimentalTime::class)
        fun now(): Long = Clock.System.now().toEpochMilliseconds()
    }
}

data class IosWatchRunState(
    val configurationId: String,
    val configurationTitle: String,
    val segmentName: String,
    val segmentIndex: Int,
    val segmentCount: Int,
    val status: String,
    val elapsedMilliseconds: Long,
    val capturedAtEpochMilliseconds: Long,
    val deltaMilliseconds: Long,
    val hasDelta: Boolean,
    val primaryActionTitle: String,
    val pauseActionTitle: String,
    val relayConnected: Boolean,
)
