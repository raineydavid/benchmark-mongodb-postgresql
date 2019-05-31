package com.ongres.benchmark;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

public enum Metric {
  
  ITERATIONS("iterations", Meter.class),
  RETRY("retry", Meter.class),
  RESPONSE_TIME("response-time", Timer.class);
  
  private final String name;
  private final Class<? extends com.codahale.metrics.Metric> type;
  
  private Metric(String name, Class<? extends com.codahale.metrics.Metric> type) {
    this.name = name;
    this.type = type;
  }
  
  public String getName() {
    return name;
  }
  
  public Class<? extends com.codahale.metrics.Metric> getType() {
    return type;
  }
}
