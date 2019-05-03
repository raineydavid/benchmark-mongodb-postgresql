package com.ongres.benchmark;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

public class BenchmarkRunner implements Runnable {
  
  private final Runnable runnable;
  private final Meter transactionMeter = MetricsManager.meter(Metric.TRANSACTIONS);
  private final Meter retryMeter = MetricsManager.meter(Metric.RETRY);
  private final Timer responseTimer = MetricsManager.timer(Metric.RESPONSE_TIME);
  
  public BenchmarkRunner(Runnable runnable) {
    super();
    this.runnable = runnable;
  }

  @Override
  public void run() {
    responseTimer.time(this::runWithRetry);
    transactionMeter.mark();
  }

  private void runWithRetry() {
    while (true) {
      try {
        runnable.run();
        break;
      } catch (RetryUserOperationException ex) {
        retryMeter.mark();
        continue;
      }
    }
  }
}
