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

func parseMilliseconds(_ value: String) -> Int64? {
    let normalized = value.replacingOccurrences(of: ",", with: ".")
    let parts = normalized.split(separator: ":", omittingEmptySubsequences: false)
    guard (1...3).contains(parts.count),
          parts.allSatisfy({ !$0.isEmpty }),
          let seconds = Double(parts[parts.count - 1]),
          seconds.isFinite,
          seconds >= 0,
          (parts.count == 1 || seconds < 60)
    else { return nil }

    let minutes: Int64
    if parts.count > 1 {
        guard let parsedMinutes = Int64(parts[parts.count - 2]),
              parsedMinutes >= 0,
              (parts.count == 2 || parsedMinutes < 60)
        else { return nil }
        minutes = parsedMinutes
    } else {
        minutes = 0
    }

    let hours: Int64
    if parts.count > 2 {
        guard let parsedHours = Int64(parts[0]), parsedHours >= 0 else { return nil }
        hours = parsedHours
    } else {
        hours = 0
    }

    let milliseconds = Double(hours * 3_600 + minutes * 60) * 1_000 + seconds * 1_000
    guard milliseconds <= Double(Int64.max) else { return nil }
    return Int64(milliseconds.rounded())
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
