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

package com.ongres.benchmark.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractPoolableConnectionSupplier
    implements PoolConfiguration, ConnectionSupplier {

  private static final Logger logger = LogManager.getLogger();

  protected final String jdbcUrl;
  protected final Properties jdbcDriverProperties;

  protected AbstractPoolableConnectionSupplier(Properties jdbcDriverProperties) {
    this.jdbcUrl = getJdbcUrl(jdbcDriverProperties);
    this.jdbcDriverProperties = buildJdbcDriverProperties(jdbcDriverProperties);
  }

  private Properties buildJdbcDriverProperties(Properties jdbcDriverProperties) {
    Properties properties = new Properties();
    properties.putAll(jdbcDriverProperties);
    setupJdbcDriver(properties);
    return properties;
  }

  protected abstract void setupJdbcDriver(Properties jdbcDriverProperties);

  protected abstract String getJdbcUrl(Properties jdbcDriverProperties);

  @Override
  public String getJdbcUrl() {
    return getJdbcUrl(jdbcDriverProperties);
  }

  @Override
  public Properties getJdbcDriverProperties() {
    return jdbcDriverProperties;
  }

  @Override
  public Connection get() {
    while (true) {
      try {
        Connection connection = DriverManager.getConnection(
            this.jdbcUrl, this.jdbcDriverProperties);
        connection.setAutoCommit(isAutoCommit());
        connection.setTransactionIsolation(getTransactionIsolationLevel());
        try (Statement statement = connection.createStatement()) {
          statement.execute(getInitSql());
          if (!isAutoCommit()) {
            connection.commit();
          }
        }
        return connection;
      } catch (SQLRecoverableException | SQLTransientException ex) {
        logger.warn("Retrying on recoverable error", ex);
        continue;
      } catch (SQLException ex) {
        throw new ConnectionException(ex);
      }
    }
  }

  @Override
  public void close() throws IOException {
  }

  /**
   * Wrapper for SQLException to RuntimeException.
   */
  static final class ConnectionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConnectionException(Throwable cause) {
      super(cause);
    }
  }
}
