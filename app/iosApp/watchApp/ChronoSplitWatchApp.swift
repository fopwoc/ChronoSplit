import SwiftUI

@main
struct ChronoSplitWatchApp: App {
    @State private var session = WatchSessionModel()

    var body: some Scene {
        WindowGroup {
            WatchTimerScreen(session: session)
                .tint(.mint)
        }
    }
}
