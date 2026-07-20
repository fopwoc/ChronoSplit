package dev.fopwoc.chronosplit.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import dev.fopwoc.chronosplit.presentation.RunBoard
import dev.fopwoc.chronosplit.model.RunStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
@OptIn(ExperimentalTime::class)
fun RunBoardRoute(
    viewModel: RunBoardViewModel,
    modifier: Modifier = Modifier,
    onSegmentClick: (() -> Unit)? = viewModel::primaryAction,
) {
    val model by viewModel.model.collectAsState()

    LaunchedEffect(viewModel, model.status) {
        if (model.status == RunStatus.RUNNING) {
            while (isActive) {
                viewModel.tick(Clock.System.now().toEpochMilliseconds())
                delay(TimerTickMilliseconds)
            }
        } else {
            viewModel.tick(Clock.System.now().toEpochMilliseconds())
        }
    }

    RunBoard(
        model = model,
        modifier = modifier,
        onSegmentClick = onSegmentClick,
    )
}

private const val TimerTickMilliseconds = 50L
