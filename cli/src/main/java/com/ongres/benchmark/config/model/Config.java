package com.ongres.benchmark.config.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.ongres.benchmark.Metric;
import com.ongres.benchmark.config.model.target.Target;
import com.ongres.benchmark.config.picocli.DurationConverter;
import com.ongres.benchmark.config.picocli.LogLevelConverter;
import com.ongres.benchmark.config.picocli.PicocliArrayList;

import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import picocli.CommandLine.Option;

public class Config {

  private Target target = new Target();

  @Option(names = {"--log-level"},
      description = "Set logging level (all, debug, error, fatal, info, off, trace, warn)",
      required = false, 
      converter = LogLevelConverter.class)
  private String logLevel;
  
  @Option(names = {"--parallelism"}, 
      description = "Specify the parallelism that will be used to write to target")
  private int parallelism = Runtime.getRuntime().availableProcessors();
  
  @Option(names = {"--benchmark-target"}, 
      description = "Specify the benchmark target: postgres or mongo")
  private String targetType = "postgres";

  @Option(names = {"--duration"}, 
      description = "Set benchmark duration", 
      required = false,
      converter = DurationConverter.class)
  private String duration;

  @Option(names = {"--transactions"}, 
      description = "Set benchmark transactions that will be executed", 
      required = false)
  private Integer transactions;

  @Option(names = {"--booking-sleep"}, 
      description = "Set booking sleep before inserting seat table", 
      required = true)
  private int bookingSleep = 0;

  @Option(names = {"--day-range"}, 
      description = "Set day range when inserting / updating audit table", 
      required = true)
  private int dayRange = 1;

  @Option(names = {"--metrics"}, 
      description = "Set metrics period", 
      required = false,
      converter = DurationConverter.class)
  private String metrics;

  @Option(names = {"--metrics-reporter"}, 
      description = "Set metrics reporter", 
      required = true)
  private String metricsReporter = MetricReporterType.JXM.name();

  @Option(names = {"--metrics-filter"}, 
      description = "Set metrics filter for log and csv",
      required = false, split = ",")
  private PicocliArrayList<String> metricsFilter;

  @Option(names = {"--min-connections"}, 
      description = "Set min # of idle connections to the target database", 
      required = true)
  private int minConnections = 1;

  @Option(names = {"--max-connections"}, 
      description = "Set max # of connections to the target database", 
      required = true)
  private int maxConnections = 20;

  @Option(names = {"--connection-wait-timeout"}, 
      description = "Establishment wait timeout for connection to the target database", 
      required = true,
      converter = DurationConverter.class)
  private String connectionWaitTimeout = "PT3S";

  @Option(names = {"--connection-idle-timeout"}, 
      description = "Idle timeout for connection to the target database", 
      required = true,
      converter = DurationConverter.class)
  private String connectionIdleTimeout = "PT60S";

  @Option(names = {"--mongo-not-casually-consistent"}, 
      description = "Set mongo casually consistent sessions", 
      required = true)
  private boolean mongoNotCasuallyConsistent = false;

  @Option(names = {"--mongo-read-preference"}, 
      description = "Set mongo read preference", 
      required = true)
  private String mongoReadPreference = "PRIMARY";

  @Option(names = {"--mongo-read-concern"}, 
      description = "Set mongo read concern", 
      required = true)
  private String mongoReadConcern = "SNAPSHOT";

  @Option(names = {"--mongo-write-concern"}, 
      description = "Set mongo write concern", 
      required = true)
  private String mongoWriteConcern = "MAJORITY";

  @Option(names = {"--sql-isolation-level"}, 
      description = "Set SQL transaction isolation level", 
      required = true)
  private String sqlIsolationLevel = "REPEATABLE_READ";

  public Target getTarget() {
    return target;
  }

  public void setTarget(Target target) {
    this.target = target;
  }

  public String getLogLevel() {
    return logLevel;
  }

  @JsonIgnore
  public LogLevel getLogLevelAsEnum() {
    return LogLevel.valueOf(logLevel.toUpperCase(Locale.US));
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public int getParallelism() {
    return parallelism;
  }

  public void setParallelism(int parallelism) {
    this.parallelism = parallelism;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  public String getDuration() {
    return duration;
  }

  @JsonIgnore
  public Optional<Duration> getDurationAsDuration() {
    return Optional.ofNullable(duration)
        .map(m -> Duration.parse(m));
  }

  public void setDuration(String duration) {
    this.duration = duration;
  }

  public Integer getTransactions() {
    return transactions;
  }

  public void setTransactions(Integer transactions) {
    this.transactions = transactions;
  }

  public String getMetrics() {
    return metrics;
  }

  @JsonIgnore
  public Optional<Duration> getMetricsAsDuration() {
    return Optional.ofNullable(metrics)
        .map(m -> Duration.parse(m));
  }

  public void setMetrics(String metrics) {
    this.metrics = metrics;
  }

  public String getMetricsReporter() {
    return metricsReporter;
  }

  @JsonIgnore
  public MetricReporterType getMetricsReporterAsEnum() {
    return MetricReporterType.valueOf(metricsReporter.toUpperCase(Locale.US));
  }

  public void setMetricsReporter(String metricsReporter) {
    this.metricsReporter = metricsReporter;
  }

  public PicocliArrayList<String> getMetricsFilter() {
    return metricsFilter;
  }

  /**
   * Get metrics filter.
   */
  @JsonIgnore
  public ImmutableList<String> getMetricsFilterAsImmutableList() {
    if (metricsFilter == null) {
      return ImmutableList.of();
    }
    return metricsFilter.stream()
        .map(metric -> getMetricName(metric))
        .collect(ImmutableList.toImmutableList());
  }

  private String getMetricName(String name) {
    String loverCaseName = name.toLowerCase(Locale.US);
    for (Metric type : Metric.values()) {
      if (loverCaseName.equals(type.getName())) {
        return loverCaseName;
      }
    }
    
    throw new IllegalArgumentException("Invalid metric " + name + ". Valid metrics are: "
        + Arrays.asList(Metric.values()).stream()
          .map(type -> type.getName())
          .collect(Collectors.joining(", ")));
  }

  /**
   * Set metrics filter.
   */
  public void setMetricsFilter(PicocliArrayList<String> metricsFilter) {
    if (metricsFilter != null && metricsFilter.isEmpty()) {
      this.metricsFilter = null;
    } else {
      this.metricsFilter = metricsFilter;
    }
  }

  public int getBookingSleep() {
    return bookingSleep;
  }

  public void setBookingSleep(int bookingSleep) {
    this.bookingSleep = bookingSleep;
  }

  public int getDayRange() {
    return dayRange;
  }

  public void setDayRange(int dayRange) {
    this.dayRange = dayRange;
  }

  public int getMinConnections() {
    return minConnections;
  }

  public void setMinConnections(int minConnections) {
    this.minConnections = minConnections;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  public String getConnectionWaitTimeout() {
    return connectionWaitTimeout;
  }

  @JsonIgnore
  public Duration getConnectionWaitTimeoutAsDuration() {
    return Duration.parse(connectionWaitTimeout);
  }

  public void setConnectionWaitTimeout(String connectionWaitTimeout) {
    this.connectionWaitTimeout = connectionWaitTimeout;
  }

  public String getConnectionIdleTimeout() {
    return connectionIdleTimeout;
  }

  @JsonIgnore
  public Duration getConnectionIdleTimeoutAsDuration() {
    return Duration.parse(connectionWaitTimeout);
  }

  public void setConnectionIdleTimeout(String connectionIdleTimeout) {
    this.connectionIdleTimeout = connectionIdleTimeout;
  }

  public boolean isMongoNotCasuallyConsistent() {
    return mongoNotCasuallyConsistent;
  }

  public void setMongoNotCasuallyConsistent(boolean mongoNotCasuallyConsistent) {
    this.mongoNotCasuallyConsistent = mongoNotCasuallyConsistent;
  }

  public String getMongoReadPreference() {
    return mongoReadPreference;
  }

  /**
   * Return write concern as {@code ReadPreference}.
   */
  @JsonIgnore
  public ReadPreference getMongoReadPreferenceAsReadPreference() {
    String mongoReadPreference = this.mongoReadPreference.replaceAll("[ -]", "_");
    if (mongoReadPreference.equalsIgnoreCase("NEAREST")) {
      return ReadPreference.nearest();
    }
    if (mongoReadPreference.equalsIgnoreCase("PRIMARY")) {
      return ReadPreference.primary();
    }
    if (mongoReadPreference.equalsIgnoreCase("SECONDARY")) {
      return ReadPreference.secondary();
    }
    if (mongoReadPreference.equalsIgnoreCase("SECONDARY_PREFERRED")) {
      return ReadPreference.secondaryPreferred();
    }
    if (mongoReadPreference.equalsIgnoreCase("PRIMARY_PREFERRED")) {
      return ReadPreference.primaryPreferred();
    }
    throw new IllegalArgumentException(this.mongoReadPreference);
  }

  public void setMongoReadPreference(String mongoReadPreference) {
    this.mongoReadPreference = mongoReadPreference;
  }

  public String getMongoReadConcern() {
    return mongoReadConcern;
  }

  /**
   * Return write concern as {@code ReadConcern}.
   */
  @JsonIgnore
  public ReadConcern getMongoReadConcernAsReadConcern() {
    if (mongoReadConcern.equalsIgnoreCase("AVAILABLE")) {
      return ReadConcern.AVAILABLE;
    }
    if (mongoReadConcern.equalsIgnoreCase("MAJORITY")) {
      return ReadConcern.MAJORITY;
    }
    if (mongoReadConcern.equalsIgnoreCase("DEFAULT")) {
      return ReadConcern.DEFAULT;
    }
    if (mongoReadConcern.equalsIgnoreCase("LINEARIZABLE")) {
      return ReadConcern.LINEARIZABLE;
    }
    if (mongoReadConcern.equalsIgnoreCase("SNAPSHOT")) {
      return ReadConcern.SNAPSHOT;
    }
    throw new IllegalArgumentException(mongoReadConcern);
  }

  public void setMongoReadConcern(String mongoReadConcern) {
    this.mongoReadConcern = mongoReadConcern;
  }

  public String getMongoWriteConcern() {
    return mongoWriteConcern;
  }

  /**
   * Return write concern as {@code WriteConcern}.
   */
  @JsonIgnore
  public WriteConcern getMongoWriteConcernAsWriteConcern() {
    if (mongoWriteConcern.equalsIgnoreCase("ACKNOWLEDGED")) {
      return WriteConcern.ACKNOWLEDGED;
    }
    if (mongoWriteConcern.equalsIgnoreCase("MAJORITY")) {
      return WriteConcern.MAJORITY;
    }
    if (mongoWriteConcern.equalsIgnoreCase("JOURNALED")) {
      return WriteConcern.JOURNALED;
    }
    if (mongoWriteConcern.equalsIgnoreCase("UNACKNOWLEDGED")) {
      return WriteConcern.UNACKNOWLEDGED;
    }
    if (mongoWriteConcern.equalsIgnoreCase("W1")) {
      return WriteConcern.W1;
    }
    if (mongoWriteConcern.equalsIgnoreCase("W2")) {
      return WriteConcern.W2;
    }
    if (mongoWriteConcern.equalsIgnoreCase("W3")) {
      return WriteConcern.W3;
    }
    throw new IllegalArgumentException(mongoWriteConcern);
  }

  public void setMongoWriteConcern(String mongoWriteConcern) {
    this.mongoWriteConcern = mongoWriteConcern;
  }

  public String getSqlIsolationLevel() {
    return sqlIsolationLevel;
  }

  /**
   * Return SQL isolation level as int.
   */
  @JsonIgnore
  public int getSqlIsolationLevelAsInt() {
    String jdbcIsolationLevel = this.sqlIsolationLevel.replaceAll("[ -]", "_");
    if (jdbcIsolationLevel.equalsIgnoreCase("NONE")) {
      return Connection.TRANSACTION_NONE;
    }
    if (jdbcIsolationLevel.equalsIgnoreCase("READ_UNCOMMITTED")) {
      return Connection.TRANSACTION_READ_UNCOMMITTED;
    }
    if (jdbcIsolationLevel.equalsIgnoreCase("READ_COMMITTED")) {
      return Connection.TRANSACTION_READ_COMMITTED;
    }
    if (jdbcIsolationLevel.equalsIgnoreCase("REPEATABLE_READ")) {
      return Connection.TRANSACTION_REPEATABLE_READ;
    }
    if (jdbcIsolationLevel.equalsIgnoreCase("SERIALIZABLE")) {
      return Connection.TRANSACTION_SERIALIZABLE;
    }
    throw new IllegalArgumentException(this.sqlIsolationLevel);
  }

  public void setSqlIsolationLevel(String sqlIsolationLevel) {
    this.sqlIsolationLevel = sqlIsolationLevel;
  }
}
