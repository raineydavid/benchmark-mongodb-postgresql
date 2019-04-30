package com.ongres.benchmark.config.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.ongres.benchmark.Metric;
import com.ongres.benchmark.config.model.target.Target;
import com.ongres.benchmark.config.picocli.DurationConverter;
import com.ongres.benchmark.config.picocli.LogLevelConverter;
import com.ongres.benchmark.config.picocli.PicocliArrayList;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import picocli.CommandLine.Option;

public class Config {

  @JsonProperty
  private Target target = new Target();

  @Option(names = {"--log-level"},
      description = "Set logging level (all, debug, error, fatal, info, off, trace, warn)",
      required = false, 
      converter = LogLevelConverter.class)
  @JsonProperty
  private String logLevel = LogLevel.INFO.name().toLowerCase(Locale.US);
  
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

  @Option(names = {"--metrics"}, 
      description = "Set metrics period", 
      required = false,
      converter = DurationConverter.class)
  @JsonProperty
  private String metrics;

  @Option(names = {"--metrics-reporter"}, 
      description = "Set metrics reporter", 
      required = true)
  @JsonProperty
  private String metricsReporter = MetricReporterType.JXM.name();

  @Option(names = {"--metrics-filter"}, 
      description = "Set metrics filter for log and csv",
      required = false, split = ",")
  @JsonProperty
  private PicocliArrayList<String> metricsFilter;
  
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
}
