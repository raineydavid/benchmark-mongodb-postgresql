/*-
 *  § 
 * benchmark: command-line
 *    
 * Copyright (C) 2019 OnGres, Inc.
 *    
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * § §
 */

package com.ongres.benchmark;

import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;
import com.codahale.metrics.Timer;
import com.codahale.metrics.jmx.JmxReporter;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.google.common.base.Preconditions;

import java.io.Closeable;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramReservoir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsManager {

  private static final Logger logger = LoggerFactory.getLogger(MetricsManager.class);

  private static final MetricRegistry METRIC_REGISTRY = new MetricRegistry();
  
  private MetricsManager() {
    METRIC_REGISTRY.register("memory", new MemoryUsageGaugeSet());
  }

  public static MetricRegistry getMetricRegistry() {
    return METRIC_REGISTRY;
  }

  /**
   * Register a meter.
   */
  public static Meter meter(Metric metric) {
    Preconditions.checkArgument(metric.getType() == Meter.class);
    return METRIC_REGISTRY.meter(metric.getName());
  }

  /**
   * Register a timer.
   */
  public static Timer timer(Metric metric) {
    Preconditions.checkArgument(metric.getType() == Timer.class);
    return METRIC_REGISTRY.timer(metric.getName(), 
        () -> new Timer(new HdrHistogramReservoir()));
  }

  /**
   * Register or get a histogram.
   */
  public static Histogram histogram(Metric metric) {
    Preconditions.checkArgument(metric.getType() == Histogram.class);
    return METRIC_REGISTRY.histogram(metric.getName(), 
        () -> new Histogram(new HdrHistogramReservoir()));
  }

  /**
   * Start a slf4j reporter.
   */
  public static Closeable startSlf4jReporter(long period, TimeUnit unit, MetricFilter filter) {
    Slf4jReporter reporter = Slf4jReporter.forRegistry(METRIC_REGISTRY)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .outputTo(logger)
        .withLoggingLevel(LoggingLevel.INFO)
        .filter(filter)
        .build();
    reporter.start(period, unit);
    return reporter;
  }
  
  /**
   * Start a CSV reporter.
   */
  public static Closeable startCsvReporter(long period, TimeUnit unit, MetricFilter filter) {
    final CsvReporter reporter = CsvReporter.forRegistry(METRIC_REGISTRY)
        .formatFor(Locale.US)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .filter(filter)
        .build(Paths.get(".").toFile());
    reporter.start(period, unit);
    return reporter;
  }

  /**
   * Start JMX reporter.
   */
  public static Closeable startJmxResporter() {
    JmxReporter reporter = JmxReporter.forRegistry(METRIC_REGISTRY).build();
    reporter.start();
    return reporter;
  }
}
