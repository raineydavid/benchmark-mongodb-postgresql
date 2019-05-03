package com.ongres.benchmark;

import com.google.common.base.Preconditions;
import com.ongres.benchmark.config.model.Config;
import com.ongres.benchmark.jdbc.ConnectionSupplier;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.jooq.lambda.Unchecked;
import org.postgresql.util.PSQLException;

public class PostgresFlightBenchmark implements Runnable, AutoCloseable {

  private final Logger logger = LogManager.getLogger();

  private final AtomicLong idGenerator = new AtomicLong();
  private final ConnectionSupplier connectionSupplier;
  private final Config config;

  private PostgresFlightBenchmark(ConnectionSupplier connectionSupplier, Config config) {
    super();
    this.connectionSupplier = connectionSupplier;
    this.config = config;
  }

  /**
   * Create an instance of {@class MongoFlightBenchmark}.
   */
  public static PostgresFlightBenchmark create(ConnectionSupplier connectionSupplier,
      Config config) {
    Preconditions.checkArgument(config.getBookingSleep() >= 0);
    Preconditions.checkArgument(config.getDayRange() > 0);
    return new PostgresFlightBenchmark(connectionSupplier, config);
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
        final Document userSchedule = getUserSchedule(connection);
        final Object userId = generateUserId();
        final Instant now = Instant.now();
        final Timestamp currentTimestamp = Timestamp.from(now);
        final Date day = Date.valueOf(LocalDate.now().plus(
            now.toEpochMilli() % config.getDayRange(), ChronoUnit.DAYS));
        TimeUnit.SECONDS.sleep(config.getBookingSleep());
        insertSeat(connection, userSchedule, userId, day, currentTimestamp);
        insertPayment(connection, userSchedule, userId, currentTimestamp);
        insertAudit(connection, userSchedule, day, currentTimestamp);
        connection.commit();
      } catch (Exception ex) {
        try {
          connection.rollback();
        } catch (Exception abortEx) {
          logger.error(abortEx);
        }
        if (ex instanceof PSQLException
            && (((PSQLException) ex).getSQLState().equals("40001"))) {
          throw new RetryUserOperationException(ex);
        }
        if (ex instanceof PSQLException) {
          throw new RuntimeException("PSQLException: " 
              + ex.getMessage() + " (" + ((PSQLException) ex).getSQLState() + ")", ex);
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
      Object userId, Date day, Timestamp currentTimestamp) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "insert into seat (user_id,schedule_id,day,date) values (?,?,?,?)")) {
      statement.setString(1, userId.toString());
      statement.setString(2, userSchedule.getString("_id"));
      statement.setDate(3, day);
      statement.setTimestamp(4, currentTimestamp);
      statement.executeUpdate();
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
      statement.executeUpdate();
    }
  }

  private void insertAudit(Connection connection, Document userSchedule,
      Date day, Timestamp currentTimestamp) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "insert into audit (schedule_id,day,seats_occupied,date) values (?,?,1,?)"
        + " on conflict (schedule_id,day)"
        + " do update set seats_occupied = audit.seats_occupied + 1, date = ?")) {
      statement.setString(1, userSchedule.getString("_id"));
      statement.setDate(2, day);
      statement.setTimestamp(3, currentTimestamp);
      statement.setTimestamp(4, currentTimestamp);
      statement.executeUpdate();
    }
  }

  @Override
  public void close() throws Exception {
    connectionSupplier.close();
  }
}
