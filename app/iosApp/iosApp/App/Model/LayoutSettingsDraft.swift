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
