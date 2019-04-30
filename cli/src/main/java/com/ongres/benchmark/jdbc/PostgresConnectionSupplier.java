package com.ongres.benchmark.jdbc;

import java.sql.Connection;
import java.util.Properties;

import org.postgresql.PGProperty;

public class PostgresConnectionSupplier extends AbstractPoolableConnectionSupplier {

  /**
   * Connection supplier that configure properly the connection for applier.
   */
  public PostgresConnectionSupplier(Properties jdbcProperties) {
    super(jdbcProperties);
  }

  @Override
  protected String getJdbcUrl(Properties jdbcDriverProperties) {
    return "jdbc:postgresql://"
        + PGProperty.PG_HOST.get(jdbcDriverProperties) + ":"
        + PGProperty.PG_PORT.get(jdbcDriverProperties) + "/"
        + PGProperty.PG_DBNAME.get(jdbcDriverProperties);
  }

  @Override
  protected void setupJdbcDriver(Properties jdbcDriverProperties) {
    PGProperty.ASSUME_MIN_SERVER_VERSION.set(jdbcDriverProperties, "9.4");
    PGProperty.REWRITE_BATCHED_INSERTS.set(jdbcDriverProperties, "true");
    PGProperty.APPLICATION_NAME.set(jdbcDriverProperties, "benchmark");
    jdbcDriverProperties.setProperty("stringType", "unspecified");
  }

  @Override
  public boolean isAutoCommit() {
    return false;
  }

  @Override
  public String getInitSql() {
    return "SET TIME ZONE 'UTC'";
  }

  @Override
  public int getTransactionIsolationLevel() {
    return Connection.TRANSACTION_REPEATABLE_READ;
  }

}
