package com.ongres.benchmark.config.model;

import org.apache.logging.log4j.Level;

public enum LogLevel {
  OFF(Level.OFF),
  FATAL(Level.FATAL),
  ERROR(Level.ERROR),
  INFO(Level.INFO),
  WARN(Level.WARN),
  DEBUG(Level.DEBUG),
  TRACE(Level.TRACE),
  ALL(Level.ALL);

  private final Level logLevel;

  private LogLevel(Level logLevel) {
    this.logLevel = logLevel;
  }

  public Level getLogLevel() {
    return logLevel;
  }
}
