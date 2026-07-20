package dev.fopwoc.chronosplit.app.presentation

import androidx.lifecycle.ViewModel
import dev.fopwoc.chronosplit.app.session.MobileSession
import dev.fopwoc.chronosplit.model.LayoutDefinition
import dev.fopwoc.chronosplit.model.LssRunDocument
import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.RunBoardModel
import dev.fopwoc.chronosplit.model.RunSnapshot
import dev.fopwoc.chronosplit.model.RunState
import dev.fopwoc.chronosplit.model.toRunBoardModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RunBoardViewModel(
    private val session: MobileSession,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val previewDefinition = MutableStateFlow<RunDefinition?>(null)
    private val mutableModel = MutableStateFlow(createModel(session.snapshot.value))

    val model: StateFlow<RunBoardModel> = mutableModel.asStateFlow()
    val configurations = session.configurations

    init {
        scope.launch {
            session.snapshot.collect { snapshot ->
                mutableModel.value = createModel(snapshot)
            }
        }
    }

    fun tick(nowEpochMilliseconds: Long) {
        mutableModel.value = createModel(session.snapshot.value, nowEpochMilliseconds)
    }

    fun primaryAction() = session.primaryAction()

    fun togglePause() = session.togglePause()

    fun reset() = session.reset()

    fun configure(
        definition: RunDefinition,
        preserveGoldSplits: Boolean = true,
    ) = session.configure(definition, preserveGoldSplits)

    fun createConfiguration(definition: RunDefinition): RunDefinition =
        session.createConfiguration(definition)

    fun copyConfiguration(id: String): RunDefinition? = session.copyConfiguration(id)

    fun importLayout(layout: LayoutDefinition) = session.importLayout(layout)

    fun previewLayout(layout: LayoutDefinition) {
        val definition = previewDefinition.value ?: session.currentConfiguration()
        previewDefinition.value = definition.copy(layout = layout)
        mutableModel.value = createModel(session.snapshot.value)
    }

    fun previewConfiguration(definition: RunDefinition) {
        previewDefinition.value = definition
        mutableModel.value = createModel(session.snapshot.value)
    }

    fun clearPreview() {
        previewDefinition.value = null
        mutableModel.value = createModel(session.snapshot.value)
    }

    fun importRun(definition: RunDefinition): RunDefinition = session.importRun(definition)

    fun importRun(document: LssRunDocument): RunDefinition = session.importRun(document)

    fun currentConfiguration(): RunDefinition = session.currentConfiguration()

    fun configuration(id: String): RunDefinition? = session.configuration(id)

    fun exportCurrentRun(): String = session.exportCurrentRun()

    fun exportCurrentLayout(): String = session.exportCurrentLayout()

    fun selectConfiguration(id: String): Boolean = session.selectConfiguration(id)

    fun deleteConfiguration(id: String): Boolean = session.deleteConfiguration(id)

    fun primaryActionTitle(): String = session.primaryActionTitle()

    fun pauseActionTitle(): String = session.pauseActionTitle()

    fun close() {
        scope.cancel()
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private fun createModel(
        snapshot: RunSnapshot,
        nowEpochMilliseconds: Long = snapshot.capturedAtEpochMilliseconds,
    ): RunBoardModel {
        val preview = previewDefinition.value
        val displayedSnapshot = if (preview == null) {
            snapshot
        } else {
            RunSnapshot(
                definition = preview,
                state = RunState(runId = preview.id),
                capturedAtEpochMilliseconds = nowEpochMilliseconds,
                elapsedMilliseconds = 0,
            )
        }
        return displayedSnapshot.toRunBoardModel(
            nowEpochMilliseconds = nowEpochMilliseconds,
            primaryActionTitle = session.primaryActionTitle(),
            pauseActionTitle = session.pauseActionTitle(),
        )
    }
}
