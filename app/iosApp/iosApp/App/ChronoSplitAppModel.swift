import ChronoSplitIosApp
import Foundation
import Observation

@Observable
final class ChronoSplitAppModel {
    private enum DefaultsKey {
        static let relayURL = "relayURL"
        static let legacyRelayToken = "relayToken"
        static let relayEnabled = "relayEnabled"
    }

    private enum KeychainAccount {
        static let relayToken = "mobileAuthToken"
    }

    let session: IosMobileSession
    var timer: TimerAppState
    var configuration = ConfigurationAppState()
    var relay: RelayAppState
    private(set) var history = HistoryAppState()

    private var editingConfigurationId: String?
    private var shouldLoadCurrentConfiguration = true
    @ObservationIgnored
    private let watchConnectivityEnabled: Bool
    @ObservationIgnored
    private lazy var watchConnectivity = PhoneWatchConnectivityController(model: self)

    init(isPreview: Bool = ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1") {
        watchConnectivityEnabled = !isPreview
        let databasePath: String
        if isPreview {
            databasePath = FileManager.default.temporaryDirectory
                .appendingPathComponent("chronosplit-preview-history.db").path
        } else {
            let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("ChronoSplit", isDirectory: true)
            try? FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
            databasePath = support.appendingPathComponent("history.db").path
        }

        let createdSession = IosMobileSession(databasePath: databasePath)
        session = createdSession
        let primaryActionTitle = createdSession.primaryActionTitle()
        let pauseActionTitle = createdSession.pauseActionTitle()
        timer = TimerAppState(
            primaryActionTitle: primaryActionTitle,
            pauseActionTitle: pauseActionTitle,
            isRunning: createdSession.isRunning(),
            isPaused: pauseActionTitle == "Resume",
            hasConfigurations: createdSession.hasConfigurations()
        )
        relay = Self.loadRelayState(isPreview: isPreview)
        createdSession.observeAttemptDetails { [weak self] details in
            self?.history.details = details
        }
        prepareConfigurationEditor()
        if relay.isConfigured {
            relay.connection = .connecting
            session.configureRelay(baseUrl: relay.url, authToken: relay.token)
            refreshRelayConnection(after: 0.25)
        }
        if watchConnectivityEnabled {
            _ = watchConnectivity
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.reloadConfigurations()
            self?.publishWatchState()
        }
    }

    func primaryAction() {
        session.primaryAction()
        updateActionTitles()
    }

    func togglePause() {
        session.togglePause()
        updateActionTitles()
    }

    func reset() {
        session.reset()
        updateActionTitles()
    }

    @discardableResult
    func applyConfiguration() -> Bool {
        configuration.saveError = nil
        let gameName = normalizedGameName(configuration.runDraft.gameName ?? configuration.runDraft.title)
        configuration.runDraft.gameName = gameName
        configuration.runDraft.title = gameName
        let data: Data
        do {
            data = try JSONEncoder().encode(configuration.runDraft)
        } catch {
            configuration.saveError = error.localizedDescription
            return false
        }
        guard let json = String(data: data, encoding: .utf8),
              session.saveConfigurationDraftJson(content: json, createNew: editingConfigurationId == nil)
        else {
            configuration.saveError = String(localized: "Could not save the run configuration.")
            return false
        }
        shouldLoadCurrentConfiguration = false
        editingConfigurationId = session.currentConfigurationId()
        configuration.selectedId = editingConfigurationId ?? ""
        reloadConfigurations()
        loadCurrentConfiguration()
        return true
    }

    func prepareConfigurationEditor(forceReload: Bool = false) {
        reloadConfigurations()
        guard forceReload || shouldLoadCurrentConfiguration else { return }
        guard !configuration.summaries.isEmpty else { return }
        editingConfigurationId = session.currentConfigurationId()
        configuration.selectedId = editingConfigurationId ?? ""
        loadCurrentConfiguration()
        shouldLoadCurrentConfiguration = false
    }

    func reloadConfigurations() {
        configuration.summaries = session.currentConfigurationSummaries()
        timer.hasConfigurations = !configuration.summaries.isEmpty
        if !configuration.summaries.contains(where: { $0.id == configuration.selectedId }) {
            configuration.selectedId = session.currentConfigurationId()
        }
    }

    func selectConfiguration(_ id: String) {
        guard session.selectConfiguration(id: id) else { return }
        shouldLoadCurrentConfiguration = false
        editingConfigurationId = id
        configuration.selectedId = id
        loadCurrentConfiguration()
        reloadConfigurations()
    }

    func startNewConfiguration() {
        shouldLoadCurrentConfiguration = false
        editingConfigurationId = nil
        configuration.selectedId = ""
        configuration.runDraft = .empty
    }

    func copyConfiguration(id: String) {
        guard session.doCopyConfiguration(id: id) else { return }
        editingConfigurationId = session.currentConfigurationId()
        configuration.selectedId = editingConfigurationId ?? ""
        reloadConfigurations()
        loadCurrentConfiguration()
    }

    func deleteConfiguration(id: String) {
        guard session.deleteConfiguration(id: id) else { return }
        reloadConfigurations()
        configuration.selectedId = session.currentConfigurationId()
        editingConfigurationId = configuration.selectedId
        loadCurrentConfiguration()
    }

    func addSegment() {
        let number = configuration.runDraft.segments.count + 1
        configuration.runDraft.segments.append(.init(id: "segment-\(number)", name: "Segment \(number)"))
    }

    func removeSegment(id: String) {
        guard configuration.runDraft.segments.count > 1 else { return }
        configuration.runDraft.segments.removeAll { $0.id == id }
    }

    func setRunIcon(_ data: Data) {
        configuration.runDraft.iconPngBase64 = data.base64EncodedString()
    }

    func setSegmentIcon(_ data: Data, id: String) {
        guard let index = configuration.runDraft.segments.firstIndex(where: { $0.id == id }) else { return }
        configuration.runDraft.segments[index].iconPngBase64 = data.base64EncodedString()
    }

    func importLayout(from url: URL) {
        let accessGranted = url.startAccessingSecurityScopedResource()
        defer {
            if accessGranted { url.stopAccessingSecurityScopedResource() }
        }

        do {
            guard let content = String(data: try Data(contentsOf: url), encoding: .utf8) else {
                configuration.layoutImportError = String(localized: "The selected layout is not UTF-8 text.")
                return
            }
            guard session.importLayout(layoutJson: content) else {
                configuration.layoutImportError = String(localized: "The selected file is not a supported .ls1l layout.")
                return
            }
            configuration.layoutImportError = nil
        } catch {
            configuration.layoutImportError = error.localizedDescription
        }
    }

    func importRun(from url: URL) {
        let accessGranted = url.startAccessingSecurityScopedResource()
        defer {
            if accessGranted { url.stopAccessingSecurityScopedResource() }
        }

        do {
            guard let content = String(data: try Data(contentsOf: url), encoding: .utf8) else {
                configuration.runImportError = String(localized: "The selected run is not UTF-8 text.")
                return
            }
            guard session.importRun(runXml: content) else {
                configuration.runImportError = String(localized: "The selected file is not a supported .lss run.")
                return
            }
            configuration.runImportError = nil
            shouldLoadCurrentConfiguration = false
            editingConfigurationId = session.currentConfigurationId()
            configuration.selectedId = editingConfigurationId ?? ""
            reloadConfigurations()
            loadCurrentConfiguration()
        } catch {
            configuration.runImportError = error.localizedDescription
        }
    }

    private func loadCurrentConfiguration() {
        if let data = session.currentConfigurationDraftJson().data(using: .utf8),
           var decoded = try? JSONDecoder().decode(RunConfigurationDraft.self, from: data) {
            if decoded.gameName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false {
                decoded.gameName = decoded.title
            }
            configuration.runDraft = decoded
        }
        loadLayoutDraft()
        updateActionTitles()
    }

    func loadLayoutDraft() {
        guard let data = session.currentLayoutDraftJson().data(using: .utf8),
              let decoded = try? JSONDecoder().decode(LayoutSettingsDraft.self, from: data)
        else { return }
        configuration.layoutDraft = decoded
    }

    @discardableResult
    func saveLayoutDraft() -> Bool {
        configuration.saveError = nil
        let data: Data
        do {
            data = try JSONEncoder().encode(configuration.layoutDraft)
        } catch {
            configuration.saveError = error.localizedDescription
            return false
        }
        guard let json = String(data: data, encoding: .utf8),
              session.saveLayoutDraftJson(content: json)
        else {
            configuration.saveError = String(localized: "Could not save the layout.")
            return false
        }
        return true
    }

    func previewLayoutDraft(_ draft: LayoutSettingsDraft? = nil) {
        guard let data = try? JSONEncoder().encode(draft ?? configuration.layoutDraft),
              let json = String(data: data, encoding: .utf8)
        else { return }
        _ = session.previewLayoutDraftJson(content: json)
    }

    func previewRunDraft(_ draft: RunConfigurationDraft? = nil) {
        guard let data = try? JSONEncoder().encode(draft ?? configuration.runDraft),
              let json = String(data: data, encoding: .utf8)
        else { return }
        _ = session.previewConfigurationDraftJson(content: json)
    }

    func endRunBoardPreview() {
        session.clearRunBoardPreview()
    }

    func exportedRun() -> TextExportDocument {
        TextExportDocument(text: session.exportCurrentRun())
    }

    func exportedLayout() -> TextExportDocument {
        TextExportDocument(text: session.exportCurrentLayout())
    }

    func connectRelay() {
        relay.errorMessage = nil
        do {
            try KeychainStore.set(relay.token, for: KeychainAccount.relayToken)
        } catch {
            relay.errorMessage = error.localizedDescription
            return
        }
        session.configureRelay(baseUrl: relay.url, authToken: relay.token)
        relay.isConfigured = true
        relay.connection = .connecting
        UserDefaults.standard.set(relay.url, forKey: DefaultsKey.relayURL)
        UserDefaults.standard.set(true, forKey: DefaultsKey.relayEnabled)
        UserDefaults.standard.removeObject(forKey: DefaultsKey.legacyRelayToken)
        refreshRelayConnection(after: 0.25)
        refreshRelayConnection(after: 1.5)
        publishWatchState()
    }

    func syncRelay() {
        session.syncRelay()
        refreshRelayConnection()
        publishWatchState()
    }

    func ensureBackendIntegration() {
        guard relay.isConfigured else {
            publishWatchState()
            return
        }
        if !session.isRelayConnected() {
            relay.connection = .connecting
            session.configureRelay(baseUrl: relay.url, authToken: relay.token)
        } else {
            session.syncRelay()
        }
        refreshRelayConnection(after: 0.35)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [weak self] in
            self?.publishWatchState()
        }
    }

    func watchPayload() -> [String: Any] {
        let currentConfigurations = session.currentConfigurationSummaries()
        let configurationPayload: [[String: Any]] = currentConfigurations.map {
            ["id": $0.id, "title": $0.title, "segmentCount": $0.segmentCount]
        }
        guard !currentConfigurations.isEmpty else {
            return [
                "hasConfiguration": false,
                "configurationId": "",
                "configurationTitle": "No configurations",
                "segmentName": "Open ChronoSplit on iPhone",
                "segmentIndex": 0,
                "segmentCount": 0,
                "status": "READY",
                "elapsedMilliseconds": 0,
                "capturedAtEpochMilliseconds": Int64(Date().timeIntervalSince1970 * 1_000),
                "deltaMilliseconds": 0,
                "hasDelta": false,
                "primaryActionTitle": "Start",
                "pauseActionTitle": "Pause",
                "relayConfigured": relay.isConfigured,
                "relayConnected": false,
                "configurations": configurationPayload,
            ]
        }

        let state = session.watchRunState()
        return [
            "hasConfiguration": true,
            "configurationId": state.configurationId,
            "configurationTitle": state.configurationTitle,
            "segmentName": state.segmentName,
            "segmentIndex": state.segmentIndex,
            "segmentCount": state.segmentCount,
            "status": state.status,
            "elapsedMilliseconds": state.elapsedMilliseconds,
            "capturedAtEpochMilliseconds": state.capturedAtEpochMilliseconds,
            "deltaMilliseconds": state.deltaMilliseconds,
            "hasDelta": state.hasDelta,
            "primaryActionTitle": state.primaryActionTitle,
            "pauseActionTitle": state.pauseActionTitle,
            "relayConfigured": relay.isConfigured,
            "relayConnected": state.relayConnected,
            "configurations": configurationPayload,
        ]
    }

    func isReadyForWatchCommands() -> Bool {
        session.isReadyForRemoteCommands()
    }

    private func publishWatchState() {
        guard watchConnectivityEnabled else { return }
        watchConnectivity.publish()
    }

    private func refreshRelayConnection(after delay: TimeInterval = 0) {
        let update = { [weak self] in
            guard let self, relay.isConfigured else { return }
            relay.connection = RelayConnectionStatus(rawValue: session.relayConnectionState()) ?? .disconnected
        }
        if delay == 0 {
            update()
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: update)
        }
    }

    private static func loadRelayState(isPreview: Bool) -> RelayAppState {
        guard !isPreview else { return .defaults }
        let defaults = UserDefaults.standard
        let legacyToken = defaults.string(forKey: DefaultsKey.legacyRelayToken)
        let storedToken = KeychainStore.string(for: KeychainAccount.relayToken)
        var token = storedToken ?? legacyToken ?? ""
        if storedToken == nil, let legacyToken {
            do {
                try KeychainStore.set(legacyToken, for: KeychainAccount.relayToken)
                defaults.removeObject(forKey: DefaultsKey.legacyRelayToken)
            } catch {
                token = legacyToken
            }
        }
        return RelayAppState(
            url: defaults.string(forKey: DefaultsKey.relayURL) ?? RelayAppState.defaults.url,
            token: token,
            isConfigured: defaults.bool(forKey: DefaultsKey.relayEnabled),
            connection: .disconnected,
            errorMessage: nil
        )
    }

    private func updateActionTitles() {
        timer.primaryActionTitle = session.primaryActionTitle()
        timer.pauseActionTitle = session.pauseActionTitle()
        timer.isRunning = session.isRunning()
        timer.isPaused = timer.pauseActionTitle == "Resume"
        timer.hasConfigurations = session.hasConfigurations()
        publishWatchState()
    }
}

struct TimerAppState {
    var primaryActionTitle: String
    var pauseActionTitle: String
    var isRunning: Bool
    var isPaused: Bool
    var hasConfigurations: Bool
}

struct ConfigurationAppState {
    var runDraft = RunConfigurationDraft.empty
    var layoutDraft = LayoutSettingsDraft.defaults
    var summaries: [ConfigurationSummary] = []
    var selectedId = ""
    var layoutImportError: String?
    var runImportError: String?
    var saveError: String?
}

struct RelayAppState {
    var url: String
    var token: String
    var isConfigured: Bool
    var connection: RelayConnectionStatus
    var errorMessage: String?

    static let defaults = RelayAppState(
        url: "http://127.0.0.1:8080",
        token: "",
        isConfigured: false,
        connection: .disconnected,
        errorMessage: nil
    )
}

enum RelayConnectionStatus: String {
    case disconnected = "DISCONNECTED"
    case connecting = "CONNECTING"
    case connected = "CONNECTED"
    case authenticationFailed = "AUTHENTICATION_FAILED"
    case sessionBusy = "SESSION_BUSY"
}

struct HistoryAppState {
    var details: [AttemptDetail] = []
}

struct RunConfigurationDraft: Codable, Equatable {
    var id: String?
    var title: String
    var gameName: String?
    var categoryName: String?
    var iconPngBase64: String?
    var attemptCount: Int
    var offsetMilliseconds: Int64
    var segments: [SegmentConfigurationDraft]

    static let empty = RunConfigurationDraft(
        id: nil,
        title: "New Run",
        gameName: "New Run",
        categoryName: nil,
        iconPngBase64: nil,
        attemptCount: 0,
        offsetMilliseconds: 0,
        segments: (1...3).map { .init(id: "segment-\($0)", name: "Segment \($0)") },
    )
}

private func normalizedGameName(_ value: String) -> String {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? "New Run" : trimmed
}

struct SegmentConfigurationDraft: Codable, Identifiable, Equatable {
    var id: String
    var name: String
    var iconPngBase64: String?
    var splitTimeMilliseconds: Int64?
    var bestSegmentMilliseconds: Int64?
}

struct LayoutSettingsDraft: Codable, Equatable {
    var titleEnabled: Bool
    var showGameName: Bool
    var showCategoryName: Bool
    var showAttemptCount: Bool
    var previousSegmentEnabled: Bool
    var showThinSeparators: Bool
    var fillWithBlankSpace: Bool
    var alwaysShowLastSplit: Bool
    var showColumnLabels: Bool
    var visualSplitCount: Int?
    var splitPreviewCount: Int
    var splitTimeAccuracy: String
    var deltaTimeAccuracy: String
    var timerAccuracy: String
    var segmentTimer: Bool
    var timerGradient: Bool

    static let defaults = LayoutSettingsDraft(
        titleEnabled: true, showGameName: true, showCategoryName: true,
        showAttemptCount: true, previousSegmentEnabled: false,
        showThinSeparators: true, fillWithBlankSpace: true,
        alwaysShowLastSplit: true, showColumnLabels: false,
        visualSplitCount: nil, splitPreviewCount: 0,
        splitTimeAccuracy: "HUNDREDTHS", deltaTimeAccuracy: "HUNDREDTHS",
        timerAccuracy: "HUNDREDTHS", segmentTimer: false, timerGradient: true,
    )
}
