import ChronoSplitIosApp

struct ConfigurationAppState {
    var runDraft = RunConfigurationDraft.empty
    var layoutDraft = LayoutSettingsDraft.defaults
    var summaries: [ConfigurationSummary] = []
    var selectedId = ""
    var layoutImportError: String?
    var runImportError: String?
    var saveError: String?
}
