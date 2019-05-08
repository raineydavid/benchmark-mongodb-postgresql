package com.ongres.benchmark.config.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Target {

  @JsonProperty
  private TargetDatabase database = new TargetDatabase();
  
  public TargetDatabase getDatabase() {
    return database;
  }

  public void setDatabase(TargetDatabase database) {
    this.database = database;
  }

}
