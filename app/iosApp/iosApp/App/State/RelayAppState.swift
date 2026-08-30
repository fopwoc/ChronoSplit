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
