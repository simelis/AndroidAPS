package app.aaps.core.interfaces.logging

enum class LTag(val tag: String, val defaultValue: Boolean = true, val requiresRestart: Boolean = false) {
    CORE("CORE"),
    APS("APS"),
    AUTOSENS("AUTOSENS", defaultValue = false),
    AUTOMATION("AUTOMATION"),
    AUTOTUNE("AUTOTUNE", defaultValue = false),
    BGSOURCE("BGSOURCE"),
    CONFIGBUILDER("CONFIGBUILDER"),
    CONSTRAINTS("CONSTRAINTS"),
    DATABASE("DATABASE"),
    EVENTS("EVENTS", defaultValue = false, requiresRestart = true),
    GARMIN("GARMIN"),
    GLUCOSE("GLUCOSE", defaultValue = false),
    // TEMPORARY test switch, not a log tag: the settle pass of the secondary graphs (see SecondaryGraphCompose).
    // Shown in the log settings so it can be switched on a phone. Remove with the graph diagnostics.
    GRAPH_SETTLE("GRAPH_SETTLE", defaultValue = true),
    HTTP("HTTP"),
    LOCATION("LOCATION"),
    NOTIFICATION("NOTIFICATION"),
    NSCLIENT("NSCLIENT"),
    NSCLIENT_SYNC("NSCLIENT_SYNC", defaultValue = false),
    OHUPLOADER("OHUPLOADER"),
    PUMP("PUMP"),
    PUMPBTCOMM("PUMPBTCOMM", defaultValue = true),
    PUMPCOMM("PUMPCOMM"),
    PUMPEMULATOR("PUMPEMULATOR", defaultValue = false),
    PUMPQUEUE("PUMPQUEUE"),
    PROFILE("PROFILE"),
    SMS("SMS"),
    TIDEPOOL("TIDEPOOL"),
    UI("UI", defaultValue = false),
    WEAR("WEAR"),
    WIDGET("WIDGET"),
    WORKER("WORKER"),
    XDRIP("XDRIP"),
    XDRIP_SYNC("XDRIP_SYNC", defaultValue = false)
}