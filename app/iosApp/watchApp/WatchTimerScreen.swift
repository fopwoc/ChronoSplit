import SwiftUI

struct WatchTimerScreen: View {
    let session: WatchSessionModel

    var body: some View {
        NavigationStack {
            Group {
                if session.state.hasConfiguration {
                    TimelineView(.periodic(from: .now, by: 0.05)) { context in
                        VStack(spacing: 3) {
                            header
                            timer(at: context.date)
                            if let commandError = session.commandError {
                                Text(commandError)
                                    .font(.caption2)
                                    .foregroundStyle(.orange)
                                    .lineLimit(1)
                            }
                            Spacer(minLength: 0)
                            primaryButton
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.horizontal, 6)
                        .padding(.bottom, 2)
                        .overlay(alignment: .bottomLeading) {
                            pauseButton
                        }
                        .overlay(alignment: .bottomTrailing) {
                            resetButton
                        }
                    }
                } else {
                    ContentUnavailableView(
                        "No configurations",
                        systemImage: "iphone",
                        description: Text("Add a run in ChronoSplit on iPhone")
                    )
                }
            }
        }
    }

    private var header: some View {
        NavigationLink {
            WatchConfigurationScreen(session: session)
        } label: {
            HStack(spacing: 4) {
                Circle()
                    .fill(integrationColor)
                    .frame(width: 5, height: 5)
                Text(session.state.segmentName)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                Spacer(minLength: 0)
                if session.state.segmentCount > 0 {
                    Text("\(session.state.segmentIndex + 1)/\(session.state.segmentCount)")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                Image(systemName: "list.bullet")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(session.state.segmentName), change run")
    }

    private func timer(at date: Date) -> some View {
        VStack(spacing: 0) {
            Text(formatTime(session.state.elapsed(at: date)))
                .font(.system(size: 29, weight: .semibold, design: .rounded).monospacedDigit())
                .minimumScaleFactor(0.65)
                .lineLimit(1)
            Text(formatDelta(session.state.delta(at: date)))
                .font(.caption.monospacedDigit().weight(.semibold))
                .foregroundStyle(deltaColor(session.state.delta(at: date)))
        }
    }

    private var primaryButton: some View {
        Button {
            session.perform(.primary)
        } label: {
            Text(primaryLabel)
                .font(.system(size: 21, weight: .black, design: .rounded))
                .frame(width: 82, height: 82)
                .background(.mint.gradient, in: Circle())
                .foregroundStyle(.black)
        }
        .buttonStyle(.plain)
        .disabled(!session.isPhoneReachable || session.state.status == .paused || session.state.status == .finished)
        .opacity(session.isPhoneReachable ? 1 : 0.45)
        .accessibilityLabel(primaryLabel)
    }

    private var pauseButton: some View {
        compactControl(
            title: session.state.status == .paused ? String(localized: "Resume") : String(localized: "Pause"),
            systemImage: session.state.status == .paused ? "play.fill" : "pause.fill",
            disabled: session.state.status != .running && session.state.status != .paused
        ) {
            session.perform(.pause)
        }
    }

    private var resetButton: some View {
        compactControl(title: String(localized: "Reset run"), systemImage: "arrow.counterclockwise") {
            session.perform(.rerun)
        }
    }

    private func compactControl(
        title: String,
        systemImage: String,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 13, weight: .semibold))
                .frame(width: 32, height: 32)
                .background(.thinMaterial, in: Circle())
        }
        .buttonStyle(.plain)
        .frame(width: 40, height: 40)
        .contentShape(Circle())
        .disabled(disabled)
        .opacity(disabled ? 0.35 : 1)
        .accessibilityLabel(title)
    }

    private var primaryLabel: String {
        if session.state.status == .ready { return String(localized: "RUN") }
        if session.state.status == .finished { return String(localized: "DONE") }
        if session.state.segmentIndex >= session.state.segmentCount - 1 { return String(localized: "FINISH") }
        return String(localized: "SPLIT")
    }

    private var integrationColor: Color {
        if session.state.relayConnected { return .green }
        if session.state.relayConfigured { return .yellow }
        return .secondary
    }

    private func formatTime(_ milliseconds: Int64) -> String {
        let totalHundredths = max(0, milliseconds) / 10
        let minutes = totalHundredths / 6_000
        let seconds = (totalHundredths / 100) % 60
        let hundredths = totalHundredths % 100
        return String(format: "%02lld:%02lld.%02lld", minutes, seconds, hundredths)
    }

    private func formatDelta(_ milliseconds: Int64?) -> String {
        guard let milliseconds else { return String(localized: "No comparison") }
        let sign = milliseconds < 0 ? "−" : "+"
        let absolute = abs(milliseconds)
        return String(format: "%@%lld.%02lld", sign, absolute / 1_000, (absolute % 1_000) / 10)
    }

    private func deltaColor(_ milliseconds: Int64?) -> Color {
        guard let milliseconds else { return .secondary }
        return milliseconds <= 0 ? .green : .red
    }
}

private struct WatchConfigurationScreen: View {
    let session: WatchSessionModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if session.configurations.isEmpty {
                ContentUnavailableView(
                    "No configurations",
                    systemImage: "iphone",
                    description: Text("Open ChronoSplit on iPhone")
                )
            } else {
                List(session.configurations) { configuration in
                    Button {
                        session.perform(.selectConfiguration, configurationId: configuration.id)
                        dismiss()
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(configuration.title)
                                    .lineLimit(2)
                                Text("\(configuration.segmentCount) segments")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if configuration.id == session.state.configurationId {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(.mint)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Run")
    }
}
