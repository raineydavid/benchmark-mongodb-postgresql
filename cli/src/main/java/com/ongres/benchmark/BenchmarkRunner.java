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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

public class BenchmarkRunner implements Runnable, AutoCloseable {

  private final Benchmark benchmark;
  private final Meter transactionMeter = MetricsManager.meter(Metric.ITERATIONS);
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
        benchmark.run();
        break;
      } catch (RetryUserOperationException ex) {
        retryMeter.mark();
        continue;
      }
    }
  }

  @Override
  public void close() throws Exception {
    benchmark.close();
  }

}
