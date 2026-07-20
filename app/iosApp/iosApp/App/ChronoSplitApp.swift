import SwiftUI

@main
struct ChronoSplitApp: App {
    @State private var model = ChronoSplitAppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ChronoRootView(model: model)
                .tint(ChronoTheme.accent)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        model.syncRelay()
                    }
                }
        }
    }
}

private struct ChronoRootView: View {
    let model: ChronoSplitAppModel
    @State private var selectedTab: RootTab = .timer

    private enum RootTab: Hashable {
        case timer
        case history
        case integration
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Timer", systemImage: "timer", value: RootTab.timer) {
                TimerScreen(model: model)
            }

            Tab("History", systemImage: "clock.arrow.circlepath", value: RootTab.history) {
                NavigationStack {
                    HistoryScreen(model: model)
                }
            }

            Tab("Integration", systemImage: "dot.radiowaves.left.and.right", value: RootTab.integration) {
                NavigationStack {
                    RelayScreen(model: model)
                }
            }
        }
    }
}

#Preview("App — Light") {
    ChronoRootView(model: ChronoSplitAppModel())
        .tint(ChronoTheme.accent)
        .preferredColorScheme(.light)
}

#Preview("App — Dark") {
    ChronoRootView(model: ChronoSplitAppModel())
        .tint(ChronoTheme.accent)
        .preferredColorScheme(.dark)
}
