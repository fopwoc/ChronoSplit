import SwiftUI

struct ConfigurationScreen: View {
    let model: ChronoSplitAppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section("Run data") {
                NavigationLink {
                    RunConfigurationsScreen(model: model)
                } label: {
                    SettingsDestination(
                        title: String(localized: "Run configurations"),
                        subtitle: String(localized: "Runs, splits, icons, history, and .lss files"),
                        systemImage: "list.bullet.rectangle"
                    )
                }
            }

            Section("Appearance") {
                NavigationLink {
                    LayoutSettingsScreen(model: model)
                } label: {
                    SettingsDestination(
                        title: String(localized: "Layout"),
                        subtitle: String(localized: "Board components, timing display, and .ls1l files"),
                        systemImage: "rectangle.3.group"
                    )
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Done") { dismiss() }
            }
        }
    }
}

private struct SettingsDestination: View {
    let title: String
    let subtitle: String
    let systemImage: String

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.body.weight(.semibold))
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        } icon: {
            Image(systemName: systemImage)
                .foregroundStyle(ChronoTheme.accent)
        }
    }
}

#Preview("Settings") {
    NavigationStack {
        ConfigurationScreen(model: ChronoSplitAppModel())
    }
}
