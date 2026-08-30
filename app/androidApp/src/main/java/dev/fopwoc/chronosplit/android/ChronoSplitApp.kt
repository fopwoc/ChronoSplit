package dev.fopwoc.chronosplit.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.fopwoc.chronosplit.android.ui.page.configuration.hub.ConfigurationHubScreen
import dev.fopwoc.chronosplit.android.ui.page.configuration.layout.LayoutSettingsScreen
import dev.fopwoc.chronosplit.android.ui.page.configuration.run.RunConfigurationScreen
import dev.fopwoc.chronosplit.android.ui.page.history.HistoryScreen
import dev.fopwoc.chronosplit.android.ui.page.relay.RelayScreen
import dev.fopwoc.chronosplit.android.ui.page.timer.TimerScreen
import dev.fopwoc.chronosplit.android.ui.theme.ChronoSplitTheme
import kotlinx.serialization.Serializable

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
                        onOpenHistory = { navigate(HistoryRoute) },
                        onOpenConfiguration = { navigate(ConfigurationRoute) },
                        onOpenRelay = { navigate(RelayRoute) },
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
