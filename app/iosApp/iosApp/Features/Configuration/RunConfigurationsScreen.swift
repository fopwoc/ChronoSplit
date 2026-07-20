import ChronoSplitIosApp
import SwiftUI
import UniformTypeIdentifiers

struct RunConfigurationsScreen: View {
    @Bindable var model: ChronoSplitAppModel
    @State private var isImportingRun = false
    @State private var isExportingRun = false
    @State private var editorRoute: ConfigurationEditorRoute?

    var body: some View {
        List {
            Section("Run files") {
                Button("Import .lss", systemImage: "arrow.down.doc") { isImportingRun = true }
                Button("Export .lss", systemImage: "square.and.arrow.up") { isExportingRun = true }
                    .disabled(model.configuration.selectedId.isEmpty)
            }

            Section("Configurations") {
                if model.configuration.summaries.isEmpty {
                    ContentUnavailableView(
                        "No Configurations",
                        systemImage: "flag.checkered",
                        description: Text("Create a run with + or import a .lss file.")
                    )
                }
                ForEach(model.configuration.summaries, id: \.id) { configuration in
                    Button {
                        editorRoute = ConfigurationEditorRoute(configurationId: configuration.id)
                    } label: {
                        ConfigurationListRow(
                            configuration: configuration,
                            isActive: configuration.id == model.configuration.selectedId
                        )
                    }
                    .buttonStyle(.plain)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button("Delete", systemImage: "trash", role: .destructive) {
                            model.deleteConfiguration(id: configuration.id)
                        }
                        Button("Copy", systemImage: "doc.on.doc") {
                            model.copyConfiguration(id: configuration.id)
                        }
                        .tint(ChronoTheme.accent)
                    }
                }
            }
        }
        .navigationTitle("Run configurations")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.prepareConfigurationEditor(forceReload: true) }
        .navigationDestination(item: $editorRoute) { route in
            RunConfigurationEditorScreen(model: model, configurationId: route.configurationId)
        }
        .fileImporter(isPresented: $isImportingRun, allowedContentTypes: [.xml, .data]) { result in
            switch result {
            case .success(let url):
                model.importRun(from: url)
                model.prepareConfigurationEditor(forceReload: true)
            case .failure(let error):
                model.configuration.runImportError = error.localizedDescription
            }
        }
        .fileExporter(
            isPresented: $isExportingRun,
            document: model.exportedRun(),
            contentType: .xml,
            defaultFilename: "\(model.configuration.runDraft.gameName ?? model.configuration.runDraft.title).lss"
        ) { _ in }
        .alert("Could not import run", isPresented: importErrorBinding) {
            Button("OK", role: .cancel) { model.configuration.runImportError = nil }
        } message: {
            Text(model.configuration.runImportError ?? String(localized: "Unknown import error"))
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("New configuration", systemImage: "plus") {
                    editorRoute = ConfigurationEditorRoute(configurationId: nil)
                }
            }
        }
    }

    private var importErrorBinding: Binding<Bool> {
        Binding(
            get: { model.configuration.runImportError != nil },
            set: { if !$0 { model.configuration.runImportError = nil } }
        )
    }
}

private struct ConfigurationEditorRoute: Identifiable, Hashable {
    let id = UUID()
    let configurationId: String?
}

private struct ConfigurationListRow: View {
    let configuration: ConfigurationSummary
    let isActive: Bool

    private var name: String {
        let game = configuration.gameName?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let game, !game.isEmpty { return game }
        return configuration.title
    }

    var body: some View {
        HStack(spacing: 12) {
            ConfigurationIcon(base64: configuration.iconPngBase64)
            VStack(alignment: .leading, spacing: 3) {
                Text(name).font(.body.weight(.semibold)).foregroundStyle(.primary)
                HStack(spacing: 6) {
                    if let category = configuration.categoryName, !category.isEmpty {
                        Text(category)
                        Text("·")
                    }
                    Text("\(configuration.segmentCount) splits")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            if isActive {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(ChronoTheme.accent)
            }
            Image(systemName: "chevron.forward")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .contentShape(Rectangle())
        .padding(.vertical, 3)
    }
}

private struct ConfigurationIcon: View {
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

#Preview("Run configurations") {
    NavigationStack {
        RunConfigurationsScreen(model: ChronoSplitAppModel())
    }
}
