import SwiftUI

struct TimerScreen: View {
    let model: ChronoSplitAppModel
    @State private var isShowingConfiguration = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                runBoard
                    .layoutPriority(1)
                TimerControls(model: model)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(ChronoBackground())
            .navigationTitle("Timer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Configure", systemImage: "slider.horizontal.3") {
                        isShowingConfiguration = true
                    }
                }
            }
        }
        .sheet(isPresented: $isShowingConfiguration) {
            NavigationStack {
                ConfigurationScreen(model: model)
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
    }

    private var runBoard: some View {
        Group {
            if model.timer.hasConfigurations {
                SharedRunBoard(session: model.session, onSegmentClick: model.primaryAction)
            } else {
                ContentUnavailableView(
                    "No Configuration",
                    systemImage: "flag.checkered",
                    description: Text("Create or import a run in Settings to start timing."),
                )
            }
        }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(Color(uiColor: .separator).opacity(0.35), lineWidth: 0.8)
            }
            .shadow(color: .black.opacity(0.16), radius: 18, y: 10)
    }
}

private struct TimerControls: View {
    let model: ChronoSplitAppModel

    var body: some View {
        HStack(spacing: 10) {
            Button {
                model.togglePause()
            } label: {
                Image(systemName: model.timer.isPaused ? "play.fill" : "pause.fill")
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.circle)
            .accessibilityLabel(localizedActionTitle(model.timer.pauseActionTitle))
            .disabled(!model.timer.hasConfigurations || (!model.timer.isRunning && !model.timer.isPaused))

            Button {
                model.primaryAction()
            } label: {
                Label(
                    localizedActionTitle(model.timer.primaryActionTitle),
                    systemImage: model.timer.isRunning ? "forward.fill" : "play.fill",
                )
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
            }
            .tint(ChronoTheme.accent)
            .chronoPrimaryButtonStyle()
            .disabled(
                !model.timer.hasConfigurations ||
                    model.timer.primaryActionTitle == "Paused" ||
                    model.timer.primaryActionTitle == "Finished"
            )

            Button(role: .destructive) {
                model.reset()
            } label: {
                Image(systemName: "arrow.counterclockwise")
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.circle)
            .accessibilityLabel("Reset")
            .disabled(!model.timer.hasConfigurations)
        }
        .padding(10)
        .chronoGlass(cornerRadius: 18)
    }
}

private func localizedActionTitle(_ title: String) -> String {
    String(localized: String.LocalizationValue(title))
}
