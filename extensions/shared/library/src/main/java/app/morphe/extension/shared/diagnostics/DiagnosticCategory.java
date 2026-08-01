package app.morphe.extension.shared.diagnostics;

public enum DiagnosticCategory {
    CRASH_REPORTS("crash"),
    FOLLOW("follow"),
    DOWNLOADS("downloads"),
    FEED_AND_NAVIGATION("feed"),
    FEATURE_GATE_LAB("feature_gate"),
    SETTINGS("settings"),
    PATCH_ERRORS("errors"),
    OTHER("other");

    public final String value;

    DiagnosticCategory(String value) {
        this.value = value;
    }
}
