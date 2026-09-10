package dev.skidfuscator.protocol.modal;

import org.apache.log4j.Level;

/**
 * The type Severity to translate in and out of different loggers.
 */
public enum Severity {
    /**
     * The constant DEBUG.
     */
    DEBUG("Debug", -1),
    /**
     * The constant INFO.
     */
    INFO("Info", 0),
    /**
     * The constant WARN.
     */
    WARN("Warn", 5),
    /**
     * The constant ERROR.
     */
    ERROR("Error", 25),
    /**
     * The constant FATAL.
     */
    FATAL("Fatal", 100);

    private final String name;
    private final int severity;

    /**
     * Instantiates a new Severity.
     *
     * @param name     the name
     * @param severity the severity
     */
    Severity(String name, int severity) {
        this.name = name;
        this.severity = severity;
    }

    /**
     * Gets the severity name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets severity.
     *
     * @return the severity
     */
    public int getSeverity() {
        return severity;
    }

    public Level toApacheLog4j() {
        switch (this) {
            case DEBUG: return Level.DEBUG;
            case INFO: return Level.INFO;
            case WARN: return Level.WARN;
            case ERROR: return Level.ERROR;
            case FATAL: return Level.FATAL;
            default: return Level.ALL;
        }
    }

    public static Severity fromApacheLog4j(final Level level) {
        switch (level.toInt()) {
            case Level.DEBUG_INT: return DEBUG;
            case Level.WARN_INT: return WARN;
            case Level.ERROR_INT: return ERROR;
            case Level.FATAL_INT: return FATAL;
            default: return INFO;
        }
    }
}
