package com.ongres.benchmark.jdbc;

import java.io.Closeable;
import java.sql.Connection;
import java.util.function.Supplier;

public interface ConnectionSupplier extends Supplier<Connection>, Closeable {
  public static final ConnectionSupplier EMPTY = new EmptyConnectionSupplier();
}
