package com.ongres.benchmark.jdbc;

import com.ongres.benchmark.jdbc.AbstractPoolableConnectionSupplier.ConnectionException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HikariConnectionSupplier implements ConnectionSupplier {

  protected static final Logger logger = LogManager.getLogger();

  private final HikariDataSource dataSource;

  public HikariConnectionSupplier(PoolConfiguration poolConfiguration) {
    this(poolConfiguration, buildDefaultConfig());
  }

  /**
   * Create a {@code HikariConnectionSupplier} with {@code HikariConfig}.
   */
  public HikariConnectionSupplier(PoolConfiguration poolConfiguration,
      HikariConfig config) {
    config.setJdbcUrl(Objects.requireNonNull(poolConfiguration.getJdbcUrl()));
    config.setDataSourceProperties(poolConfiguration.getJdbcDriverProperties());
    config.setAutoCommit(poolConfiguration.isAutoCommit());
    TransactionIsolationLevel transactionIsolationLevel = TransactionIsolationLevel.fromInteger(
        poolConfiguration.getTransactionIsolationLevel());
    config.setTransactionIsolation(transactionIsolationLevel.name());
    config.setConnectionInitSql(poolConfiguration.getInitSql());

    this.dataSource = new HikariDataSource(config);
  }

  private static HikariConfig buildDefaultConfig() {
    HikariConfig config = new HikariConfig();
    config.setInitializationFailTimeout(Duration.ofSeconds(30).toMillis());
    config.setConnectionTimeout(Duration.ofSeconds(3).toMillis());
    config.setIdleTimeout(Duration.ofSeconds(60).toMillis());
    config.setMinimumIdle(1);
    config.setMaximumPoolSize(20);
    return config;
  }

  @Override
  public Connection get() {
    try {
      return dataSource.getConnection();
    } catch (SQLException ex) {
      throw new ConnectionException(ex);
    }
  }

  @Override
  public void close() {
    dataSource.close();
  }
  
  private enum TransactionIsolationLevel {
    TRANSACTION_NONE(0),
    TRANSACTION_READ_UNCOMMITTED(1),
    TRANSACTION_READ_COMMITTED(2),
    TRANSACTION_REPEATABLE_READ(4),
    TRANSACTION_SERIALIZABLE(8);
    
    private final int level;

    private TransactionIsolationLevel(int level) {
      this.level = level;
    }
    
    private static TransactionIsolationLevel fromInteger(int level) {
      for (TransactionIsolationLevel transactionIsolationLevel : TransactionIsolationLevel
          .values()) {
        if (transactionIsolationLevel.level == level) {
          return transactionIsolationLevel;
        }
      }
      throw new IllegalArgumentException("Unknown transaction level " + level);
    }
  }
}
