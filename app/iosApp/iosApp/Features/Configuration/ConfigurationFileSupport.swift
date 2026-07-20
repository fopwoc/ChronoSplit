import SwiftUI
import UniformTypeIdentifiers

struct TextExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText, .xml, .json] }
    let text: String

    init(text: String) {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        text = configuration.file.regularFileContents
            .flatMap { String(data: $0, encoding: .utf8) } ?? ""
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

func optionalText(_ binding: Binding<String?>) -> Binding<String> {
    Binding(
        get: { binding.wrappedValue ?? "" },
        set: { binding.wrappedValue = $0.isEmpty ? nil : $0 }
    )
}

func optionalInteger(_ binding: Binding<Int?>) -> Binding<String> {
    Binding(
        get: { binding.wrappedValue.map(String.init) ?? "" },
        set: { binding.wrappedValue = Int($0) }
    )
}

func parseMilliseconds(_ value: String) -> Int64? {
    let parts = value.split(separator: ":")
    guard let seconds = Double(parts.last ?? "") else { return nil }
    let minutes = parts.count > 1 ? Int64(parts[parts.count - 2]) ?? 0 : 0
    let hours = parts.count > 2 ? Int64(parts[parts.count - 3]) ?? 0 : 0
    return Int64(Double(hours * 3_600 + minutes * 60) * 1_000 + seconds * 1_000)
}

func formatMilliseconds(_ value: Int64) -> String {
    let minutes = value / 60_000
    let seconds = value / 1_000 % 60
    let tenths = value % 1_000 / 100
    return "\(minutes):\(String(format: "%02lld", seconds)).\(tenths)"
}

func withSecurityAccess(_ url: URL, body: () -> Void) {
    let granted = url.startAccessingSecurityScopedResource()
    defer { if granted { url.stopAccessingSecurityScopedResource() } }
    body()
}
