import SwiftUI

struct RelayScreen: View {
    @Bindable var model: ChronoSplitAppModel

    var body: some View {
        Form {
            Section {
                LabeledContent("Status") {
                    Label(
                        statusTitle,
                        systemImage: statusIcon,
                    )
                    .foregroundStyle(statusColor)
                }
            } header: {
                Text("Connection")
            } footer: {
                Text("ChronoSplit remains the source of truth. The relay mirrors the latest run for remote displays.")
            }

            Section("Relay server") {
                TextField("http://127.0.0.1:8080", text: $model.relay.url)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .textContentType(.URL)

                SecureField("Mobile auth token (optional)", text: $model.relay.token)
            }

            Section {
                Button {
                    model.connectRelay()
                } label: {
                    Label("Connect relay", systemImage: "bolt.horizontal.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .controlSize(.large)
                .tint(ChronoTheme.accent)
                .chronoPrimaryButtonStyle()
            }
        }
        .navigationTitle("Integration")
        .navigationBarTitleDisplayMode(.large)
        .alert("Could not save credentials", isPresented: credentialErrorBinding) {
            Button("OK", role: .cancel) { model.relay.errorMessage = nil }
        } message: {
            Text(model.relay.errorMessage ?? String(localized: "Unknown credential error"))
        }
    }

    private var statusTitle: String {
        guard model.relay.isConfigured else { return String(localized: "Not set") }
        switch model.relay.connection {
        case .connected: return String(localized: "Connected")
        case .connecting: return String(localized: "Connecting")
        case .authenticationFailed: return String(localized: "Authentication failed")
        case .sessionBusy: return String(localized: "Session busy")
        case .disconnected: return String(localized: "Disconnected")
        }
    }

    private var statusIcon: String {
        switch model.relay.connection {
        case .connected: return "checkmark.circle.fill"
        case .connecting: return "arrow.trianglehead.2.clockwise.rotate.90"
        case .authenticationFailed: return "key.slash.fill"
        case .sessionBusy: return "person.2.fill"
        case .disconnected: return "circle.dashed"
        }
    }

    private var statusColor: Color {
        switch model.relay.connection {
        case .connected: return ChronoTheme.mint
        case .connecting, .sessionBusy: return ChronoTheme.warning
        case .authenticationFailed: return ChronoTheme.danger
        case .disconnected: return .secondary
        }
    }

    private var credentialErrorBinding: Binding<Bool> {
        Binding(
            get: { model.relay.errorMessage != nil },
            set: { if !$0 { model.relay.errorMessage = nil } }
        )
    }
}

#Preview("Integration") {
    NavigationStack {
        RelayScreen(model: ChronoSplitAppModel())
    }
}
