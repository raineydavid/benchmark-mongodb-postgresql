package com.ongres.benchmark;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

public class BenchmarkRunner implements Runnable {

  private final Benchmark benchmark;
  private final Meter transactionMeter = MetricsManager.meter(Metric.TRANSACTIONS);
  private final Meter retryMeter = MetricsManager.meter(Metric.RETRY);
  private final Timer responseTimer = MetricsManager.timer(Metric.RESPONSE_TIME);
  
  public BenchmarkRunner(Benchmark benchmark) {
    super();
    this.benchmark = benchmark;
  }

  public void setup() {
    benchmark.setup();
  }

  @Override
  public void run() {
    responseTimer.time(this::runWithRetry);
    transactionMeter.mark();
  }

  private void runWithRetry() {
    while (true) {
      try {
        benchmark.iteration();
        break;
      } catch (RetryUserOperationException ex) {
        retryMeter.mark();
        continue;
      }
    }
  }

  public interface Benchmark {
    /**
     * Setup and cleanup the database.
     */
    public void setup();

    /**
     * Run a single client iteration.
     */
    public void iteration();
  }
}
