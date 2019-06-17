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
