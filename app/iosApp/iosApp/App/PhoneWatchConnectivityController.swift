import Foundation
import WatchConnectivity

final class PhoneWatchConnectivityController: NSObject, WCSessionDelegate {
    private enum WatchCommand: String {
        case primary
        case pause
        case rerun
        case selectConfiguration
        case ensureIntegration
    }

    private weak var model: ChronoSplitAppModel?
    private let session: WCSession?

    init(model: ChronoSplitAppModel) {
        self.model = model
        session = WCSession.isSupported() ? .default : nil
        super.init()
        session?.delegate = self
        session?.activate()
    }

    func publish() {
        guard let session, session.activationState == .activated, let payload = model?.watchPayload() else {
            return
        }
        try? session.updateApplicationContext(payload)
    }

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: (any Error)?,
    ) {
        guard activationState == .activated else { return }
        DispatchQueue.main.async { [weak self] in
            self?.model?.ensureBackendIntegration()
            self?.publish()
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        guard session.isReachable else { return }
        DispatchQueue.main.async { [weak self] in
            self?.model?.ensureBackendIntegration()
            self?.publish()
        }
    }

    func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any],
        replyHandler: @escaping ([String: Any]) -> Void,
    ) {
        DispatchQueue.main.async { [weak self] in
            self?.handle(message, replyHandler: replyHandler)
        }
    }

    private func handle(
        _ message: [String: Any],
        attempt: Int = 0,
        replyHandler: @escaping ([String: Any]) -> Void,
    ) {
        guard let model else {
            replyHandler(["error": "iPhone session unavailable"])
            return
        }
        guard let rawCommand = message["command"] as? String,
              let command = WatchCommand(rawValue: rawCommand)
        else {
            replyHandler(["error": "Unknown watch command"])
            return
        }
        let requiresRun = command != .ensureIntegration
        if requiresRun && !model.isReadyForWatchCommands() && attempt < 20 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
                self?.handle(message, attempt: attempt + 1, replyHandler: replyHandler)
            }
            return
        }
        guard !requiresRun || model.isReadyForWatchCommands() else {
            replyHandler(["error": "iPhone session is not ready"])
            return
        }

        switch command {
            case .primary:
                model.primaryAction()
            case .pause:
                model.togglePause()
            case .rerun:
                model.reset()
            case .selectConfiguration:
                if let id = message["configurationId"] as? String {
                    model.selectConfiguration(id)
                }
            case .ensureIntegration:
                model.ensureBackendIntegration()
        }

        let payload = model.watchPayload()
        replyHandler(payload)
        publish()
    }
}
