import SwiftUI
import UniformTypeIdentifiers

struct RunConfigurationEditorScreen: View {
    @Bindable var model: ChronoSplitAppModel
    let configurationId: String?
    @Environment(\.dismiss) private var dismiss
    @State private var isImportingIcon = false
    @State private var iconTarget: IconTarget = .run
    @State private var hasLoadedEditor = false
    @State private var isPreviewActive = false

    private enum IconTarget {
        case run
        case segment(String)
    }

    private var canSave: Bool {
        !(model.configuration.runDraft.gameName ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            model.configuration.runDraft.segments.allSatisfy {
                !$0.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
    }

    var body: some View {
        Form {
            Section("Preview") {
                SharedRunBoard(session: model.session, onSegmentClick: nil)
                    .frame(height: 280)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }

            Section("Run") {
                HStack(spacing: 12) {
                    IconButton(
                        base64: model.configuration.runDraft.iconPngBase64,
                        title: String(localized: "Choose game icon")
                    ) {
                        iconTarget = .run
                        isImportingIcon = true
                    }
                    TextField("Game", text: optionalText($model.configuration.runDraft.gameName))
                        .font(.body.weight(.semibold))
                }
                TextField("Category", text: optionalText($model.configuration.runDraft.categoryName))
                LabeledContent("Attempts", value: "\(model.configuration.runDraft.attemptCount)")
            }

            Section {
                ForEach($model.configuration.runDraft.segments) { $segment in
                    SegmentEditorRow(
                        segment: $segment,
                        segmentTime: segmentTime(for: segment.id),
                        selectIcon: {
                            iconTarget = .segment(segment.id)
                            isImportingIcon = true
                        }
                    )
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        if model.configuration.runDraft.segments.count > 1 {
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                model.removeSegment(id: segment.id)
                            }
                        }
                    }
                }

                Button("Add Split", systemImage: "plus") { model.addSegment() }
            } header: {
                HStack {
                    Text("Splits")
                    Spacer()
                    Text("Sum of best  \(sumOfBest)").monospacedDigit()
                }
            }
        }
        .listSectionSpacing(.compact)
        .navigationTitle(configurationId == nil ? String(localized: "New Run") : String(localized: "Edit Run"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard !hasLoadedEditor else { return }
            hasLoadedEditor = true
            if let configurationId {
                model.selectConfiguration(configurationId)
            } else {
                model.startNewConfiguration()
            }
            isPreviewActive = true
            model.previewRunDraft()
        }
        .onChange(of: model.configuration.runDraft) { _, _ in
            guard isPreviewActive else { return }
            model.previewRunDraft()
        }
        .onDisappear { model.endRunBoardPreview() }
        .fileImporter(isPresented: $isImportingIcon, allowedContentTypes: [.png, .image]) { result in
            guard case .success(let url) = result else { return }
            withSecurityAccess(url) {
                guard let data = try? Data(contentsOf: url) else { return }
                switch iconTarget {
                case .run: model.setRunIcon(data)
                case .segment(let id): model.setSegmentIcon(data, id: id)
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    if model.applyConfiguration() {
                        model.endRunBoardPreview()
                        dismiss()
                    }
                }
                .disabled(!canSave)
            }
        }
        .alert("Could not save run", isPresented: saveErrorBinding) {
            Button("OK", role: .cancel) { model.configuration.saveError = nil }
        } message: {
            Text(model.configuration.saveError ?? String(localized: "Unknown save error"))
        }
    }

    private var saveErrorBinding: Binding<Bool> {
        Binding(
            get: { model.configuration.saveError != nil },
            set: { if !$0 { model.configuration.saveError = nil } }
        )
    }

    private func segmentTime(for id: String) -> String {
        let segments = model.configuration.runDraft.segments
        guard let index = segments.firstIndex(where: { $0.id == id }),
              let split = segments[index].splitTimeMilliseconds
        else { return "—" }
        let previous = index > 0 ? segments[index - 1].splitTimeMilliseconds ?? 0 : 0
        return formatMilliseconds(max(0, split - previous))
    }

    private var sumOfBest: String {
        let segments = model.configuration.runDraft.segments
        let values = segments.compactMap(\.bestSegmentMilliseconds)
        guard values.count == segments.count else { return "—" }
        return formatMilliseconds(values.reduce(0, +))
    }
}

private struct SegmentEditorRow: View {
    @Binding var segment: SegmentConfigurationDraft
    let segmentTime: String
    let selectIcon: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                IconButton(
                    base64: segment.iconPngBase64,
                    title: String(localized: "Choose icon for \(segment.name)"),
                    size: 36,
                    action: selectIcon
                )
                TextField("Segment name", text: $segment.name)
                    .font(.body.weight(.medium))
                    .textInputAutocapitalization(.words)
            }

            HStack(alignment: .top, spacing: 8) {
                CompactTimeField(
                    title: String(localized: "Split"),
                    placeholder: "0:00.0",
                    milliseconds: $segment.splitTimeMilliseconds
                )
                CompactTimeValue(title: String(localized: "Segment"), value: segmentTime)
                CompactTimeField(
                    title: String(localized: "Best"),
                    placeholder: "—",
                    milliseconds: $segment.bestSegmentMilliseconds
                )
            }
        }
        .padding(.vertical, 3)
    }
}

private struct CompactTimeField: View {
    let title: String
    let placeholder: String
    @Binding var milliseconds: Int64?
    @State private var input: String
    @FocusState private var isFocused: Bool

    init(title: String, placeholder: String, milliseconds: Binding<Int64?>) {
        self.title = title
        self.placeholder = placeholder
        _milliseconds = milliseconds
        _input = State(initialValue: milliseconds.wrappedValue.map(formatMilliseconds) ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption2.weight(.medium)).foregroundStyle(.secondary)
            TextField(placeholder, text: $input)
                .font(.subheadline.monospacedDigit())
                .keyboardType(.numbersAndPunctuation)
                .textFieldStyle(.plain)
                .focused($isFocused)
                .onChange(of: input) { _, value in
                    milliseconds = value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? nil
                        : parseMilliseconds(value)
                }
                .onChange(of: isFocused) { _, focused in
                    guard !focused else { return }
                    input = milliseconds.map(formatMilliseconds) ?? ""
                }
                .padding(.horizontal, 8)
                .frame(height: 34)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
        .frame(maxWidth: .infinity)
    }
}

private struct CompactTimeValue: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption2.weight(.medium)).foregroundStyle(.secondary)
            Text(value)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .padding(.horizontal, 8)
                .frame(maxWidth: .infinity, minHeight: 34, alignment: .leading)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
        .frame(maxWidth: .infinity)
    }
}

private struct IconButton: View {
    let base64: String?
    let title: String
    var size: CGFloat = 42
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 9, style: .continuous).fill(.quaternary)
                if let base64, let data = Data(base64Encoded: base64), let image = UIImage(data: data) {
                    Image(uiImage: image).resizable().scaledToFit().padding(3)
                } else {
                    Image(systemName: "photo").foregroundStyle(.secondary)
                }
            }
            .frame(width: size, height: size)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

#Preview("New run") {
    NavigationStack {
        RunConfigurationEditorScreen(model: ChronoSplitAppModel(), configurationId: nil)
    }
}
