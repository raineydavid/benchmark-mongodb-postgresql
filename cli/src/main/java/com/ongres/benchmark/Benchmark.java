package com.ongres.benchmark;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class Benchmark implements AutoCloseable {

  private final ReadWriteLock closedLock = new ReentrantReadWriteLock();
  private final Lock readClosedLock = closedLock.readLock();
  private final Lock writeClosedLock = closedLock.writeLock();
  private final AtomicInteger running = new AtomicInteger(0);
  private volatile boolean closed = false;

  /**
   * Setup and cleanup the database.
   */
  public abstract void setup();

  /**
   * Run a single client iteration.
   */
  public void run() {
    readClosedLock.lock();
    try {
      if (closed) {
        return;
      }
    } finally {
      readClosedLock.unlock();
    }
    running.incrementAndGet();
    try {
      iteration();
    } finally {
      running.decrementAndGet();
    }
  }

  /**
   * Run a single client iteration.
   */
  protected abstract void iteration();

  @Override
  public final void close() throws Exception {
    writeClosedLock.lock();
    try {
      closed = true;
      while (running.get() != 0) {
        TimeUnit.MICROSECONDS.sleep(20);
      }
      internalClose();
    } finally {
      writeClosedLock.unlock();
    }
  }

  /**
   * Close internal benchmark resources.
   */
  protected abstract void internalClose() throws Exception;
}
