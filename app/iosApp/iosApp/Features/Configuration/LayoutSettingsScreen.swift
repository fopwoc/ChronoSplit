import SwiftUI
import UniformTypeIdentifiers

struct LayoutSettingsScreen: View {
    @Bindable var model: ChronoSplitAppModel
    @Environment(\.dismiss) private var dismiss
    @State private var isImporting = false
    @State private var isExporting = false
    @State private var isPreviewActive = false
    @State private var hasLoadedDraft = false

    var body: some View {
        Form {
            Section("Preview") {
                SharedRunBoard(session: model.session, onSegmentClick: nil)
                    .frame(height: 300)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }

            Section("Settings") {
                NavigationLink {
                    LayoutComponentsScreen(model: model)
                } label: {
                    Label("Title and splits", systemImage: "list.bullet.rectangle")
                }
                NavigationLink {
                    LayoutTimerScreen(model: model)
                } label: {
                    Label("Timer display", systemImage: "timer")
                }
            }

            Section("Layout files") {
                Button("Import .ls1l", systemImage: "arrow.down.doc") { isImporting = true }
                Button("Export .ls1l", systemImage: "square.and.arrow.up") { isExporting = true }
            }
        }
        .navigationTitle("Layout")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if !hasLoadedDraft {
                model.loadLayoutDraft()
                hasLoadedDraft = true
            }
            isPreviewActive = true
            model.previewLayoutDraft()
        }
        .onChange(of: model.configuration.layoutDraft) { _, _ in
            guard isPreviewActive else { return }
            model.previewLayoutDraft()
        }
        .onDisappear {
            isPreviewActive = false
            model.endRunBoardPreview()
        }
        .fileImporter(
            isPresented: $isImporting,
            allowedContentTypes: [UTType(filenameExtension: "ls1l") ?? .json]
        ) { result in
            switch result {
            case .success(let url):
                model.importLayout(from: url)
                model.loadLayoutDraft()
            case .failure(let error):
                model.configuration.layoutImportError = error.localizedDescription
            }
        }
        .fileExporter(
            isPresented: $isExporting,
            document: model.exportedLayout(),
            contentType: .json,
            defaultFilename: "layout.ls1l"
        ) { _ in }
        .alert("Could not import layout", isPresented: importErrorBinding) {
            Button("OK", role: .cancel) { model.configuration.layoutImportError = nil }
        } message: {
            Text(model.configuration.layoutImportError ?? String(localized: "Unknown import error"))
        }
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    if model.saveLayoutDraft() {
                        model.endRunBoardPreview()
                        dismiss()
                    }
                }
            }
        }
        .alert("Could not save layout", isPresented: saveErrorBinding) {
            Button("OK", role: .cancel) { model.configuration.saveError = nil }
        } message: {
            Text(model.configuration.saveError ?? String(localized: "Unknown save error"))
        }
    }

    private var importErrorBinding: Binding<Bool> {
        Binding(
            get: { model.configuration.layoutImportError != nil },
            set: { if !$0 { model.configuration.layoutImportError = nil } }
        )
    }

    private var saveErrorBinding: Binding<Bool> {
        Binding(
            get: { model.configuration.saveError != nil },
            set: { if !$0 { model.configuration.saveError = nil } }
        )
    }
}

private struct LayoutComponentsScreen: View {
    @Bindable var model: ChronoSplitAppModel

    var body: some View {
        Form {
            Section("Title") {
                Toggle("Show title", isOn: $model.configuration.layoutDraft.titleEnabled)
                Toggle("Game name", isOn: $model.configuration.layoutDraft.showGameName)
                Toggle("Category", isOn: $model.configuration.layoutDraft.showCategoryName)
                Toggle("Attempt count", isOn: $model.configuration.layoutDraft.showAttemptCount)
            }
            Section("Splits") {
                Toggle("Previous / live segment", isOn: $model.configuration.layoutDraft.previousSegmentEnabled)
                Toggle("Thin separators", isOn: $model.configuration.layoutDraft.showThinSeparators)
                Toggle("Fill blank rows", isOn: $model.configuration.layoutDraft.fillWithBlankSpace)
                Toggle("Always show final split", isOn: $model.configuration.layoutDraft.alwaysShowLastSplit)
                Toggle("Column labels", isOn: $model.configuration.layoutDraft.showColumnLabels)
                TextField(
                    "Visible split count (automatic when empty)",
                    text: optionalInteger($model.configuration.layoutDraft.visualSplitCount)
                )
                .keyboardType(.numberPad)
                Stepper(
                    "Preview splits: \(model.configuration.layoutDraft.splitPreviewCount)",
                    value: $model.configuration.layoutDraft.splitPreviewCount,
                    in: 0...20
                )
            }
        }
        .navigationTitle("Title and splits")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LayoutTimerScreen: View {
    @Bindable var model: ChronoSplitAppModel

    var body: some View {
        Form {
            Section("Split timing") {
                Picker("Split time accuracy", selection: $model.configuration.layoutDraft.splitTimeAccuracy) {
                    AccuracyOptions()
                }
                Picker("Delta accuracy", selection: $model.configuration.layoutDraft.deltaTimeAccuracy) {
                    AccuracyOptions()
                }
            }
            Section("Timer") {
                Toggle("Segment timer", isOn: $model.configuration.layoutDraft.segmentTimer)
                Toggle("Gradient", isOn: $model.configuration.layoutDraft.timerGradient)
                Picker("Accuracy", selection: $model.configuration.layoutDraft.timerAccuracy) {
                    AccuracyOptions()
                }
            }
        }
        .navigationTitle("Timer display")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct AccuracyOptions: View {
    var body: some View {
        Text("Seconds").tag("SECONDS")
        Text("Tenths").tag("TENTHS")
        Text("Hundredths").tag("HUNDREDTHS")
        Text("Milliseconds").tag("MILLISECONDS")
    }
}

#Preview("Layout") {
    NavigationStack {
        LayoutSettingsScreen(model: ChronoSplitAppModel())
    }
}
