package com.ongres.benchmark;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import reactor.core.publisher.BaseSubscriber;

public class BenchmarkSubscriber extends BaseSubscriber<Object> implements Future<Void> {
  
  private final CompletableFuture<Void> future = new CompletableFuture<Void>();
  private final Runnable runnable;
  private final Meter transactionMeter = MetricsManager.meter(Metric.TRANSACTIONS);
  private final Timer responseTimer = MetricsManager.timer(Metric.RESPONSE_TIME);
  
  public BenchmarkSubscriber(Runnable runnable) {
    super();
    this.runnable = runnable;
  }

  @Override
  protected void hookOnNext(Object value) {
    responseTimer.time(runnable);
    transactionMeter.mark();
  }

  @Override
  protected void hookOnComplete() {
    future.complete(null);
  }

  @Override
  protected void hookOnError(Throwable throwable) {
    future.completeExceptionally(throwable);
  }

  @Override
  protected void hookOnCancel() {
    future.cancel(true);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCancelled() {
    return future.isCancelled();
  }

  @Override
  public boolean isDone() {
    return future.isDone();
  }

  @Override
  public Void get() throws InterruptedException, ExecutionException {
    return future.get();
  }

  @Override
  public Void get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(timeout, unit);
  }
}
