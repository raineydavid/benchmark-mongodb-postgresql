package com.ongres.benchmark.jdbc;

import java.sql.Connection;

final class EmptyConnectionSupplier implements ConnectionSupplier {
  @Override
  public Connection get() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    // Do nothing because this is an empty connection.
  }
}
