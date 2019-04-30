package com.ongres.benchmark;

import com.google.common.base.Preconditions;
import com.ongres.benchmark.jdbc.ConnectionSupplier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.jooq.lambda.Unchecked;

public class PostgresFlightBenchmark implements Runnable, AutoCloseable {

  private final Logger logger = LogManager.getLogger();

  private final AtomicLong idGenerator = new AtomicLong();
  private final ConnectionSupplier connectionSupplier;

  private PostgresFlightBenchmark(ConnectionSupplier connectionSupplier) {
    super();
    this.connectionSupplier = connectionSupplier;
  }

  /**
   * Create an instance of {@class MongoFlightBenchmark}.
   */
  public static PostgresFlightBenchmark create(ConnectionSupplier connectionSupplier) {
    return new PostgresFlightBenchmark(connectionSupplier);
  }

  @Override
  public void run() {
    Unchecked.runnable(this::userOperation).run();
  }

  private Object generateUserId() {
    Object userId = idGenerator.getAndIncrement();
    return userId;
  }

  private void userOperation() throws Exception {
    try (Connection connection = connectionSupplier.get()) {
      try {
        Document userSchedule = getUserSchedule(connection);
        Object userId = generateUserId();
        Timestamp currentTimestamp = Timestamp.from(Instant.now());
        insertSeat(connection, userSchedule, userId, currentTimestamp);
        insertPayment(connection, userSchedule, userId, currentTimestamp);
        insertAudit(connection, userSchedule, currentTimestamp);
        connection.commit();
      } catch (Exception ex) {
        try {
          connection.rollback();
        } catch (Exception abortEx) {
          logger.error(abortEx);
        }
        throw ex;
      }
    }
  }

  private Document getUserSchedule(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select schedule_id, duration, capacity"
            + " from schedule tablesample system(10)"
            + " inner join aircraft on (schedule.aircraft = aircraft.iata)"
            + " limit 1")) {
      Preconditions.checkState(resultSet.next());
      return new Document()
                  .append("_id", resultSet.getString("schedule_id"))
                  .append("duration", resultSet.getString("duration"))
                  .append("capacity", resultSet.getString("capacity"));
    }
  }

  private void insertSeat(Connection connection, Document userSchedule,
      Object userId, Timestamp currentTimestamp) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "insert into seat (user_id,schedule_id,date) values (?,?,?)")) {
      statement.setString(1, userId.toString());
      statement.setString(2, userSchedule.getString("_id"));
      statement.setTimestamp(3, currentTimestamp);
    }
  }

  private void insertPayment(Connection connection, Document userSchedule,
      Object userId, Timestamp currentTimestamp) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "insert into payment (user_id,amount,date) values (?,?,?)")) {
      statement.setString(1, userId.toString());
      statement.setInt(2, Optional.ofNullable(userSchedule.getString("duration"))
          .map(d -> d.split(":"))
          .map(s -> Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]))
          .map(d -> Math.max(42, d * 42))
          .orElse(42));
      statement.setTimestamp(3, currentTimestamp);
    }
  }

  private void insertAudit(Connection connection, Document userSchedule,
      Timestamp currentTimestamp) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "insert into audit (schedule_id,seats_occupied,date) values (?,1,?)"
        + " on conflict do update set seats_occupied = seats_occupied + 1, date = ?")) {
      statement.setString(1, userSchedule.getString("_id"));
      statement.setTimestamp(2, currentTimestamp);
      statement.setTimestamp(3, currentTimestamp);
    }
  }

  @Override
  public void close() throws Exception {
    connectionSupplier.close();
  }
}
