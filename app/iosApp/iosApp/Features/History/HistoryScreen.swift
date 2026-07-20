import ChronoSplitIosApp
import SwiftUI

struct HistoryScreen: View {
    let model: ChronoSplitAppModel

    private var configurations: [ConfigurationSummary] {
        model.configuration.summaries.sorted {
            configurationName($0).localizedCaseInsensitiveCompare(configurationName($1)) == .orderedAscending
        }
    }

    var body: some View {
        Group {
            if configurations.isEmpty {
                ContentUnavailableView(
                    "No Configurations",
                    systemImage: "clock.arrow.circlepath",
                    description: Text("Create or import a configuration before recording attempts."),
                )
            } else {
                List(configurations, id: \.id) { configuration in
                    NavigationLink {
                        ConfigurationAttemptsScreen(model: model, configuration: configuration)
                    } label: {
                        HistoryConfigurationRow(
                            configuration: configuration,
                            attemptCount: model.history.details.count { $0.runId == configuration.id },
                        )
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("History")
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            model.reloadConfigurations()
        }
    }
}

private struct HistoryConfigurationRow: View {
    let configuration: ConfigurationSummary
    let attemptCount: Int

    var body: some View {
        HStack(spacing: 12) {
            HistoryIcon(base64: configuration.iconPngBase64)
            VStack(alignment: .leading, spacing: 3) {
                Text(configurationName(configuration))
                    .font(.body.weight(.semibold))
                if let category = configuration.categoryName, !category.isEmpty {
                    Text(category)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text("\(attemptCount) runs")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 3)
    }
}

private struct ConfigurationAttemptsScreen: View {
    let model: ChronoSplitAppModel
    let configuration: ConfigurationSummary

    private var attempts: [AttemptDetail] {
        model.history.details.filter { $0.runId == configuration.id }
    }

    var body: some View {
        Group {
            if attempts.isEmpty {
                ContentUnavailableView(
                    "No Runs Yet",
                    systemImage: "timer",
                    description: Text("Completed and interrupted runs for this configuration appear here."),
                )
            } else {
                List(attempts, id: \.id) { attempt in
                    NavigationLink {
                        AttemptDetailScreen(attempt: attempt)
                    } label: {
                        AttemptRow(attempt: attempt)
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle(configurationName(configuration))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct AttemptRow: View {
    let attempt: AttemptDetail

    private var completed: Bool { attempt.completedAtEpochMilliseconds != nil }
    private var completedSegments: Int {
        attempt.segments.count { $0.segmentDurationMilliseconds != nil }
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: completed ? "checkmark" : "pause.fill")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(completed ? ChronoTheme.mint : ChronoTheme.warning)
                .frame(width: 36, height: 36)
                .background(
                    (completed ? ChronoTheme.mint : ChronoTheme.warning).opacity(0.14),
                    in: Circle(),
                )

            VStack(alignment: .leading, spacing: 3) {
                if attempt.startedAtEpochMilliseconds > 0 {
                    Text(
                        Date(timeIntervalSince1970: Double(attempt.startedAtEpochMilliseconds) / 1_000),
                        format: .dateTime.month(.abbreviated).day().year().hour().minute(),
                    )
                } else {
                    Text("Imported LiveSplit attempt")
                }
                Text(completed ? "Completed" : "Interrupted")
                    .font(.subheadline)
                    .foregroundStyle(completed ? ChronoTheme.mint : ChronoTheme.warning)
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 3) {
                if let elapsed = attempt.elapsedMilliseconds {
                    Text(formatHistoryDuration(elapsed.int64Value))
                        .font(.body.monospacedDigit().weight(.semibold))
                }
                Text("\(completedSegments)/\(attempt.segments.count) splits")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct AttemptDetailScreen: View {
    let attempt: AttemptDetail

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                AttemptBoardPreview(attempt: attempt)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Split log")
                        .font(.title3.weight(.semibold))
                    ForEach(attempt.segments.filter { $0.segmentDurationMilliseconds != nil }, id: \.id) { segment in
                        SplitLogRow(segment: segment)
                        if segment.id != attempt.segments.last(where: { $0.segmentDurationMilliseconds != nil })?.id {
                            Divider()
                        }
                    }
                }
                .padding(16)
                .background(.background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Run details")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct AttemptBoardPreview: View {
    let attempt: AttemptDetail

    private var completed: Bool { attempt.completedAtEpochMilliseconds != nil }
    private var title: String { attempt.gameName ?? attempt.runTitle }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline)
                    if let category = attempt.categoryName, !category.isEmpty {
                        Text(category).font(.subheadline)
                    }
                }
                Spacer()
                Text(completed ? "FINISHED" : "INTERRUPTED")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(completed ? ChronoTheme.mint : ChronoTheme.warning)
            }
            .padding(14)

            ForEach(attempt.segments, id: \.id) { segment in
                Divider().overlay(.white.opacity(0.14))
                HStack(spacing: 12) {
                    Text(segment.title)
                        .lineLimit(1)
                    Spacer()
                    if let delta = segment.comparisonDeltaMilliseconds {
                        Text(formatHistoryDelta(delta.int64Value))
                            .font(.caption.monospacedDigit().weight(.semibold))
                            .foregroundStyle(historyDeltaColor(delta.int64Value, isBest: segment.isBestSegment))
                    }
                    Text(segment.segmentDurationMilliseconds.map { formatHistoryDuration($0.int64Value) } ?? "—")
                        .monospacedDigit()
                        .foregroundStyle(segment.segmentDurationMilliseconds == nil ? .secondary : .primary)
                }
                .font(.subheadline)
                .padding(.horizontal, 14)
                .frame(minHeight: 38)
            }

            HStack(alignment: .firstTextBaseline) {
                Text("Total")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(attempt.elapsedMilliseconds.map { formatHistoryDuration($0.int64Value) } ?? "—")
                    .font(.system(size: 42, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(completed ? ChronoTheme.mint : ChronoTheme.warning)
            }
            .padding(14)
        }
        .foregroundStyle(.white)
        .background(Color.black.opacity(0.92), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

private struct SplitLogRow: View {
    let segment: AttemptSegmentDetail

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(segment.title).font(.body.weight(.medium))
                if let delta = segment.comparisonDeltaMilliseconds {
                    Text(formatHistoryDelta(delta.int64Value))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(historyDeltaColor(delta.int64Value, isBest: segment.isBestSegment))
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                if let duration = segment.segmentDurationMilliseconds {
                    Text(formatHistoryDuration(duration.int64Value))
                        .font(.body.monospacedDigit().weight(.semibold))
                }
                if let elapsed = segment.elapsedAtEndMilliseconds {
                    Text("Total \(formatHistoryDuration(elapsed.int64Value))")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

private struct HistoryIcon: View {
    let base64: String?

    var body: some View {
        Group {
            if let base64, let data = Data(base64Encoded: base64), let image = UIImage(data: data) {
                Image(uiImage: image).resizable().scaledToFit().padding(3)
            } else {
                Image(systemName: "flag.checkered").foregroundStyle(ChronoTheme.accent)
            }
        }
        .frame(width: 40, height: 40)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private func configurationName(_ configuration: ConfigurationSummary) -> String {
    let game = configuration.gameName?.trimmingCharacters(in: .whitespacesAndNewlines)
    if let game, !game.isEmpty { return game }
    return configuration.title
}

private func formatHistoryDuration(_ milliseconds: Int64) -> String {
    let minutes = milliseconds / 60_000
    let seconds = milliseconds / 1_000 % 60
    let tenths = milliseconds % 1_000 / 100
    return "\(minutes):\(String(format: "%02lld", seconds)).\(tenths)"
}

private func formatHistoryDelta(_ milliseconds: Int64) -> String {
    let sign = milliseconds > 0 ? "+" : milliseconds < 0 ? "−" : "±"
    return sign + formatHistoryDuration(abs(milliseconds))
}

private func historyDeltaColor(_ milliseconds: Int64, isBest: Bool) -> Color {
    if isBest { return .yellow }
    if milliseconds < 0 { return ChronoTheme.mint }
    if milliseconds > 0 { return ChronoTheme.warning }
    return .secondary
}
