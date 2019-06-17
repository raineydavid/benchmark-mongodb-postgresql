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
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.jooq.lambda.Unchecked;
import org.postgresql.copy.CopyManager;
import org.postgresql.jdbc.PgConnection;
import org.postgresql.util.PSQLException;

public class PostgresFlightBenchmark extends Benchmark {

  private static final int MAX_SCHEDULE_ID = 14185;

  private final Logger logger = LogManager.getLogger();

  private final AtomicLong idGenerator = new AtomicLong();
  private final Random random = new Random();
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
  public void setup() {
    Unchecked.runnable(this::databaseSetup).run();
  }

  @Override
  protected void iteration() {
    Unchecked.runnable(this::userOperation).run();
  }

  private Object generateUserId() {
    Object userId = idGenerator.getAndIncrement();
    return userId;
  }

  private synchronized Object randomScheduleId() {
    Object scheduleId = random.nextInt(MAX_SCHEDULE_ID);
    return scheduleId;
  }

  private void databaseSetup() throws Exception {
    try (Connection connection = connectionSupplier.get();
        Statement statement = connection.createStatement()) {
      logger.info("Cleanup schema");
      statement.execute("drop table if exists audit");
      statement.execute("drop table if exists payment");
      statement.execute("drop table if exists seat");
      statement.execute("drop table if exists schedule");
      statement.execute("drop sequence if exists schedule_id");
      statement.execute("drop table if exists aircraft");
      logger.info("Creating schema");
      statement.execute("create extension if not exists \"uuid-ossp\"");
      statement.execute("create table aircraft ("
          + "name text, "
          + "icao text, "
          + "iata text, "
          + "capacity integer, "
          + "country text)");
      statement.execute("create table schedule ("
          + "from_airport text, "
          + "to_airport text, "
          + "valid_from text, "
          + "valid_until text, "
          + "days integer, "
          + "departure text, "
          + "arrival text, "
          + "flight text, "
          + "aircraft text, "
          + "duration text)");
      statement.execute("create table seat ("
          + "user_id bigint not null,"
          + "schedule_id int not null,"
          + "day date not null,"
          + "date timestamp without time zone,"
          + "primary key (user_id,schedule_id,day))");
      statement.execute("create table payment ("
          + "user_id bigint,"
          + "amount money,"
          + "date timestamp without time zone)");
      statement.execute("create table audit ("
          + "schedule_id int not null,"
          + "day date not null,"
          + "date timestamp without time zone,"
          + "seats_occupied int,"
          + "primary key (schedule_id,day))");
      if (!config.isDisableTransaction()) {
        connection.commit();
      }
      logger.info("Importing data");
      PgConnection pgConnection = connection.unwrap(PgConnection.class);
      CopyManager copyManager = pgConnection.getCopyAPI();
      copyManager.copyIn("copy aircraft from stdin"
          + " with csv header delimiter ';' null '\\N'", 
          PostgresFlightBenchmark.class.getResourceAsStream("/aircrafts.txt"));
      copyManager.copyIn("copy schedule from stdin"
          + " with csv header delimiter ';' null '\\N'", 
          PostgresFlightBenchmark.class.getResourceAsStream("/schedule.txt"));
      if (!config.isDisableTransaction()) {
        connection.commit();
      }
      statement.execute("create sequence schedule_id minvalue 0");
      statement.execute("alter table schedule add column schedule_id int"
          + " primary key default nextval('schedule_id')");
      statement.execute("alter table seat add"
          + " foreign key (schedule_id) references schedule(schedule_id)");
      statement.execute("alter table audit add"
          + " foreign key (schedule_id) references schedule(schedule_id)");
      if (!config.isDisableTransaction()) {
        connection.commit();
      }
    }
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
        if (!config.isDisableTransaction()) {
          connection.commit();
        }
      } catch (Exception ex) {
        if (!config.isDisableTransaction()) {
          try {
            connection.rollback();
          } catch (Exception abortEx) {
            logger.error(abortEx);
          }
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
            + " from schedule "
            + " inner join aircraft on (schedule.aircraft = aircraft.iata)"
            + " where schedule.schedule_id = " + randomScheduleId()
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
  protected void internalClose() throws Exception {
    connectionSupplier.close();
  }
}
