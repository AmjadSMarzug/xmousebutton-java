package io.github.amjadsmarzug.xmousebutton.config;

/** Thrown when a configuration file cannot be read or contains an invalid line. */
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
