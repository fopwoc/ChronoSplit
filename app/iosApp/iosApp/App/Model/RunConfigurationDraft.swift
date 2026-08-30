import Foundation

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

struct SegmentConfigurationDraft: Codable, Identifiable, Equatable {
    var id: String
    var name: String
    var iconPngBase64: String?
    var splitTimeMilliseconds: Int64?
    var bestSegmentMilliseconds: Int64?
}

func normalizedGameName(_ value: String) -> String {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? "New Run" : trimmed
}
