package com.ongres.benchmark.jdbc;

import java.util.Properties;

public interface PoolConfiguration {
  public String getJdbcUrl();
  
  public Properties getJdbcDriverProperties();
  
  public boolean isAutoCommit();
  
  public String getInitSql();
  
  public int getTransactionIsolationLevel();
}
