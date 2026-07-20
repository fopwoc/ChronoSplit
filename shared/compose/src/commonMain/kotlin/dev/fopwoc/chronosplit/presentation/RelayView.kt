package dev.fopwoc.chronosplit.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import dev.fopwoc.chronosplit.model.StateMessage
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.toRunBoardModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
@OptIn(ExperimentalTime::class)
fun RelayView(
    message: StateMessage?,
    modifier: Modifier = Modifier,
) {
    if (message == null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF14161B)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Waiting for mobile state…",
                style = TextStyle(color = Color.White, fontSize = 18.sp),
            )
        }
    } else {
        var nowEpochMilliseconds by remember(
            message.snapshot.state.revision,
            message.snapshot.capturedAtEpochMilliseconds,
        ) {
            mutableLongStateOf(message.snapshot.capturedAtEpochMilliseconds)
        }

        LaunchedEffect(
            message.snapshot.state.revision,
            message.snapshot.capturedAtEpochMilliseconds,
        ) {
            nowEpochMilliseconds = Clock.System.now().toEpochMilliseconds()
            if (message.snapshot.state.status == RunStatus.RUNNING) {
                while (isActive) {
                    delay(TimerTickMilliseconds)
                    nowEpochMilliseconds = Clock.System.now().toEpochMilliseconds()
                }
            }
        }

        RunBoard(
            model = message.snapshot.toRunBoardModel(nowEpochMilliseconds),
            modifier = modifier,
        )
    }
}

private const val TimerTickMilliseconds = 50L
