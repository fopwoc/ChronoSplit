import Foundation
import Observation
import WatchConnectivity

enum WatchCommand: String {
    case primary
    case pause
    case rerun
    case selectConfiguration
    case ensureIntegration
}

enum WatchRunStatus: String {
    case ready = "READY"
    case running = "RUNNING"
    case paused = "PAUSED"
    case finished = "FINISHED"
}

struct WatchConfiguration: Identifiable, Equatable {
    let id: String
    let title: String
    let segmentCount: Int
}

struct WatchRunState: Equatable {
    var hasConfiguration = false
    var configurationId = ""
    var configurationTitle = String(localized: "Open ChronoSplit on iPhone")
    var segmentName = String(localized: "Waiting for run")
    var segmentIndex = 0
    var segmentCount = 0
    var status = WatchRunStatus.ready
    var elapsedMilliseconds: Int64 = 0
    var capturedAtEpochMilliseconds: Int64 = 0
    var deltaMilliseconds: Int64?
    var relayConfigured = false
    var relayConnected = false

    func elapsed(at date: Date) -> Int64 {
        guard status == .running else { return elapsedMilliseconds }
        return elapsedMilliseconds + max(0, Int64(date.timeIntervalSince1970 * 1_000) - capturedAtEpochMilliseconds)
    }

    func delta(at date: Date) -> Int64? {
        guard let deltaMilliseconds else { return nil }
        guard status == .running else { return deltaMilliseconds }
        return deltaMilliseconds + max(0, Int64(date.timeIntervalSince1970 * 1_000) - capturedAtEpochMilliseconds)
    }
}

@Observable
final class WatchSessionModel: NSObject, WCSessionDelegate {
    private(set) var state = WatchRunState()
    private(set) var configurations: [WatchConfiguration] = []
    private(set) var isPhoneReachable = false
    private(set) var commandError: String?

    @ObservationIgnored
    private let session: WCSession?

    override init() {
        session = WCSession.isSupported() ? .default : nil
        super.init()
        session?.delegate = self
        session?.activate()
    }

    func perform(_ command: WatchCommand, configurationId: String? = nil) {
        guard let session, session.isReachable else {
            commandError = String(localized: "Open ChronoSplit on iPhone")
            return
        }

        var message: [String: Any] = ["command": command.rawValue]
        if let configurationId {
            message["configurationId"] = configurationId
        }
        commandError = nil
        session.sendMessage(message) { [weak self] reply in
            self?.apply(reply)
        } errorHandler: { [weak self] error in
            DispatchQueue.main.async {
                self?.commandError = error.localizedDescription
            }
        }
    }

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: (any Error)?,
    ) {
        DispatchQueue.main.async { [weak self] in
            self?.isPhoneReachable = session.isReachable
            if !session.applicationContext.isEmpty {
                self?.apply(session.applicationContext)
            }
            if session.isReachable {
                self?.perform(.ensureIntegration)
            }
        }
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        DispatchQueue.main.async { [weak self] in
            self?.isPhoneReachable = session.isReachable
            if session.isReachable {
                self?.perform(.ensureIntegration)
            }
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        apply(applicationContext)
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        apply(message)
    }

    private func apply(_ payload: [String: Any]) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            if let error = payload["error"] as? String {
                commandError = localizedCommandError(error)
                return
            }
            var next = state
            let rawConfigurations = payload["configurations"] as? [[String: Any]]
            next.hasConfiguration = payload.bool(
                "hasConfiguration",
                fallback: rawConfigurations?.isEmpty == false
            )
            next.configurationId = payload.string("configurationId", fallback: next.configurationId)
            next.configurationTitle = payload.string("configurationTitle", fallback: next.configurationTitle)
            next.segmentName = payload.string("segmentName", fallback: next.segmentName)
            next.segmentIndex = payload.int("segmentIndex", fallback: next.segmentIndex)
            next.segmentCount = payload.int("segmentCount", fallback: next.segmentCount)
            next.status = WatchRunStatus(
                rawValue: payload.string("status", fallback: next.status.rawValue)
            ) ?? next.status
            next.elapsedMilliseconds = payload.int64("elapsedMilliseconds", fallback: next.elapsedMilliseconds)
            next.capturedAtEpochMilliseconds = payload.int64(
                "capturedAtEpochMilliseconds",
                fallback: next.capturedAtEpochMilliseconds
            )
            next.deltaMilliseconds = payload.bool("hasDelta", fallback: false)
                ? payload.int64("deltaMilliseconds", fallback: 0)
                : nil
            next.relayConfigured = payload.bool("relayConfigured", fallback: next.relayConfigured)
            next.relayConnected = payload.bool("relayConnected", fallback: next.relayConnected)
            state = next

            if let rawConfigurations {
                configurations = rawConfigurations.compactMap { item in
                    guard let id = item["id"] as? String, let title = item["title"] as? String else { return nil }
                    return WatchConfiguration(
                        id: id,
                        title: title,
                        segmentCount: item.int("segmentCount", fallback: 0)
                    )
                }
            }
        }
    }

    private func localizedCommandError(_ error: String) -> String {
        switch error {
            case "Unknown watch command": String(localized: "Unknown watch command")
            case "iPhone session unavailable": String(localized: "iPhone session unavailable")
            case "iPhone session is not ready": String(localized: "iPhone session is not ready")
            default: error
        }
    }
}

private extension Dictionary where Key == String, Value == Any {
    func string(_ key: String, fallback: String) -> String {
        self[key] as? String ?? fallback
    }

    func int(_ key: String, fallback: Int) -> Int {
        (self[key] as? NSNumber)?.intValue ?? fallback
    }

    func int64(_ key: String, fallback: Int64) -> Int64 {
        (self[key] as? NSNumber)?.int64Value ?? fallback
    }

    func bool(_ key: String, fallback: Bool) -> Bool {
        (self[key] as? NSNumber)?.boolValue ?? fallback
    }
}
