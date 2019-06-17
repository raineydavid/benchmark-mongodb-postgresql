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
import com.google.common.collect.ImmutableMap;
import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.TransactionOptions;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.ongres.benchmark.config.model.Config;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jooq.lambda.Unchecked;

public class MongoFlightBenchmark extends Benchmark {

  private static final int MAX_SCHEDULE_ID = 14185;

  private final Logger logger = LogManager.getLogger();

  private final AtomicLong idGenerator = new AtomicLong(0);
  private final Random random = new Random();
  private final MongoClient client;
  private final MongoDatabase database;
  private final Config config;

  private MongoFlightBenchmark(MongoClient client, MongoDatabase database, Config config) {
    super();
    this.client = client;
    this.database = database;
    this.config = config;
  }

  /**
   * Create an instance of {@class MongoFlightBenchmark}.
   */
  public static MongoFlightBenchmark create(MongoClient client,
      Config config) {
    Preconditions.checkArgument(config.getBookingSleep() >= 0);
    Preconditions.checkArgument(config.getDayRange() > 0);
    MongoDatabase database = client.getDatabase(config.getTarget().getDatabase().getName());
    return new MongoFlightBenchmark(client,
        database,
        config);
  }

  @Override
  public void setup() {
    Unchecked.runnable(this::setupDatabase).run();
  }

  @Override
  protected void iteration() {
    if (config.isDisableTransaction()) {
      Unchecked.runnable(this::userOperationWithoutTransaction).run();
    } else {
      Unchecked.runnable(this::userOperation).run();
    }
  }

  private Object generateUserId() {
    Object userId = idGenerator.getAndIncrement();
    return userId;
  }

  private synchronized Object randomScheduleId() {
    Object scheduleId = random.nextInt(MAX_SCHEDULE_ID);
    return scheduleId;
  }

  private void setupDatabase() throws Exception {
    logger.info("Cleanup");
    database.getCollection("aircraft").drop();
    database.getCollection("schedule").drop();
    database.getCollection("seat").drop();
    database.getCollection("payment").drop();
    database.getCollection("audit").drop();
    CSVFormat csvFormat = CSVFormat.newFormat(';')
        .withNullString("\\N");
    logger.info("Importing aircraft");
    database.createCollection("aircraft");
    MongoCollection<Document> aircraft = database.getCollection("aircraft");
    CSVParser.parse(
        MongoFlightBenchmark.class.getResourceAsStream("/aircrafts.txt"), 
        StandardCharsets.UTF_8, csvFormat
        .withHeader("name", "icao", "iata", "capacity", "country"))
        .forEach(record -> aircraft.insertOne(new Document(
            record.toMap().entrySet().stream()
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())))));
    aircraft.createIndex(Indexes.ascending("iata"));
    logger.info("Importing schedule");
    database.createCollection("schedule");
    MongoCollection<Document> schedule = database.getCollection("schedule");
    AtomicInteger scheduleId = new AtomicInteger(0);
    CSVParser.parse(
        MongoFlightBenchmark.class.getResourceAsStream("/schedule.txt"), 
        StandardCharsets.UTF_8, csvFormat
        .withHeader("from_airport", "to_airport", "valid_from", "valid_until", "days",
            "departure", "arrival", "flight", "aircraft", "duration"))
        .forEach(record -> schedule.insertOne(new Document(
            Stream.concat(
                ImmutableMap.of("schedule_id", scheduleId.getAndIncrement()).entrySet().stream(),
                record.toMap().entrySet().stream())
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())))));
    schedule.createIndex(Indexes.ascending("schedule_id"));
    logger.info("Creating seat, payment and audit");
    database.createCollection("seat");
    database.createCollection("payment");
    database.createCollection("audit");
  }

  private void userOperation() throws Exception {
    try (ClientSession session = client.startSession(
        ClientSessionOptions.builder()
        .causallyConsistent(!config.isMongoNotCasuallyConsistent())
        .build())) {
      session.startTransaction(TransactionOptions.builder()
          .readPreference(config.getMongoReadPreferenceAsReadPreference())
          .readConcern(config.getMongoReadConcernAsReadConcern())
          .writeConcern(config.getMongoWriteConcernAsWriteConcern())
          .build());
      try {
        final Document userSchedule = getUserSchedule(session);
        final Object userId = generateUserId();
        final Instant now = Instant.now();
        final Timestamp currentTimestamp = Timestamp.from(now);
        final Date day = Date.valueOf(LocalDate.now().plus(
            now.toEpochMilli() % config.getDayRange(), ChronoUnit.DAYS));
        TimeUnit.SECONDS.sleep(config.getBookingSleep());
        insertSeat(session, userSchedule, userId, currentTimestamp);
        insertPayment(session, userSchedule, userId, currentTimestamp);
        insertAudit(session, userSchedule, day, currentTimestamp);
        session.commitTransaction();
      } catch (Exception ex) {
        try {
          session.abortTransaction();
        } catch (Exception abortEx) {
          logger.error(abortEx);
        }
        if (ex instanceof MongoCommandException
            && (((MongoCommandException) ex).hasErrorLabel(
                MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                || ((MongoCommandException) ex).hasErrorLabel(
                    MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
          throw new RetryUserOperationException(ex);
        }
        throw ex;
      }
    }
  }

  private void userOperationWithoutTransaction() throws Exception {
    final Document userSchedule = getUserSchedule();
    final Object userId = generateUserId();
    final Instant now = Instant.now();
    final Timestamp currentTimestamp = Timestamp.from(now);
    final Date day = Date.valueOf(LocalDate.now().plus(
        now.toEpochMilli() % config.getDayRange(), ChronoUnit.DAYS));
    TimeUnit.SECONDS.sleep(config.getBookingSleep());
    insertSeat(userSchedule, userId, currentTimestamp);
    insertPayment(userSchedule, userId, currentTimestamp);
    insertAudit(userSchedule, day, currentTimestamp);
  }

  private Document getUserSchedule(ClientSession session) {
    AggregateIterable<Document> schedules = database.getCollection("schedule")
        .aggregate(session,
            getUserScheduleAggregate());
    return schedules.first();
  }

  private Document getUserSchedule() {
    AggregateIterable<Document> schedules = database.getCollection("schedule")
        .aggregate(
            getUserScheduleAggregate());
    return schedules.first();
  }

  private List<Bson> getUserScheduleAggregate() {
    return Arrays.asList(
        Aggregates.match(Filters.eq("schedule_id", randomScheduleId())),
        Aggregates.lookup("aircraft", "aircraft", "iata", "aircraft"),
        Aggregates.project(new Document()
            .append("schedule_id", 1)
            .append("duration", 1)
            .append("capacity", "$aircraft.capacity")));
  }

  private void insertSeat(ClientSession session, Document userSchedule,
      Object userId, Timestamp currentTimestamp) {
    database.getCollection("seat").insertOne(session, 
        createSeat(userSchedule, userId, currentTimestamp));
  }

  private void insertSeat(Document userSchedule,
      Object userId, Timestamp currentTimestamp) {
    database.getCollection("seat").insertOne(createSeat(userSchedule, userId, currentTimestamp));
  }

  private Document createSeat(Document userSchedule, Object userId, Timestamp currentTimestamp) {
    return new Document()
        .append("user_id", userId)
        .append("schedule_id", userSchedule.get("schedule_id"))
        .append("date", currentTimestamp);
  }

  private void insertPayment(ClientSession session, Document userSchedule,
      Object userId, Timestamp currentTimestamp) {
    database.getCollection("payment").insertOne(session, 
        createPayment(userSchedule, userId, currentTimestamp));
  }

  private void insertPayment(Document userSchedule,
      Object userId, Timestamp currentTimestamp) {
    database.getCollection("payment").insertOne(
        createPayment(userSchedule, userId, currentTimestamp));
  }

  private Document createPayment(Document userSchedule, Object userId, Timestamp currentTimestamp) {
    return new Document()
        .append("user_id", userId)
        .append("amount", Optional.ofNullable(userSchedule.getString("duration"))
            .map(d -> d.split(":"))
            .map(s -> Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]))
            .map(d -> Math.max(42, d * 42))
            .orElse(42))
        .append("date", currentTimestamp);
  }

  private void insertAudit(ClientSession session, Document userSchedule,
      Date day, Timestamp currentTimestamp) {
    database.getCollection("audit").updateOne(session, 
        auditToUpdate(userSchedule, day), 
        auditUpdate(currentTimestamp),
        auditUpdateOptions());
  }

  private void insertAudit(Document userSchedule,
      Date day, Timestamp currentTimestamp) {
    database.getCollection("audit").updateOne(
        auditToUpdate(userSchedule, day), 
        auditUpdate(currentTimestamp),
        auditUpdateOptions());
  }
  
  private Document auditToUpdate(Document userSchedule, Date day) {
    return new Document()
        .append("schedule_id", userSchedule.get("schedule_id"))
        .append("day", day);
  }

  private Document auditUpdate(Timestamp currentTimestamp) {
    return new Document()
    .append("$set", new Document().append("date", currentTimestamp))
    .append("$inc", new Document().append("seats_occupied", 1));
  }

  private UpdateOptions auditUpdateOptions() {
    return new UpdateOptions().upsert(true);
  }

  @Override
  protected void internalClose() throws Exception {
    client.close();
  }
}
