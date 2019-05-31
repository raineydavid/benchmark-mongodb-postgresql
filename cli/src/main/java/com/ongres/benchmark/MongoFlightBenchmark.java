package com.ongres.benchmark;

import com.google.common.base.Preconditions;
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
import com.mongodb.client.model.UpdateOptions;
import com.ongres.benchmark.BenchmarkRunner.Benchmark;
import com.ongres.benchmark.config.model.Config;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.jooq.lambda.Unchecked;

public class MongoFlightBenchmark implements Benchmark, AutoCloseable {

  private final Logger logger = LogManager.getLogger();

  private final AtomicLong idGenerator = new AtomicLong(0);
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
  public void iteration() {
    Unchecked.runnable(this::userOperation).run();
  }

  private Object generateUserId() {
    Object userId = idGenerator.getAndIncrement();
    return userId;
  }

  private void setupDatabase() throws Exception {
    CSVFormat csvFormat = CSVFormat.newFormat(';')
        .withNullString("\\N");
    database.getCollection("aircraft").drop();
    database.createCollection("aircraft");
    MongoCollection<Document> aircraft = database.getCollection("aircraft");
    CSVParser.parse(
        MongoFlightBenchmark.class.getResourceAsStream("/aircrafts.txt"), 
        StandardCharsets.UTF_8, csvFormat
        .withHeader("name", "icao", "iata", "capacity", "country"))
        .forEach(record -> aircraft.insertOne(new Document(record.toMap().entrySet().stream()
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())))));
    database.getCollection("schedule").drop();
    database.createCollection("schedule");
    MongoCollection<Document> schedule = database.getCollection("schedule");
    CSVParser.parse(
        MongoFlightBenchmark.class.getResourceAsStream("/schedule.txt"), 
        StandardCharsets.UTF_8, csvFormat
        .withHeader("from_airport", "to_airport", "valid_from", "valid_until", "days",
            "departure", "arrival", "flight", "aircraft", "duration"))
        .forEach(record -> schedule.insertOne(new Document(record.toMap().entrySet().stream()
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())))));
    database.getCollection("seat").drop();
    database.createCollection("seat");
    database.getCollection("payment").drop();
    database.createCollection("payment");
    database.getCollection("audit").drop();
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

  private Document getUserSchedule(ClientSession clientSession) {
    AggregateIterable<Document> schedules = database.getCollection("schedule")
        .aggregate(clientSession,
            Arrays.asList(
                Aggregates.sample(1),
                Aggregates.lookup("aircraft", "aircraft", "iata", "aircraft"),
                Aggregates.project(new Document()
                    .append("_id", 1)
                    .append("duration", 1)
                    .append("capacity", "$aircraft.capacity"))));
    return schedules.first();
  }

  private void insertSeat(ClientSession session, Document userSchedule,
      Object userId, Timestamp currentTimestamp) {
    database.getCollection("seat").insertOne(session, new Document()
        .append("user_id", userId)
        .append("schedule_id", userSchedule.getObjectId("_id"))
        .append("date", currentTimestamp));
  }

  private void insertPayment(ClientSession session, Document userSchedule,
      Object userId, Timestamp currentTimestamp) {
    database.getCollection("payment").insertOne(session, new Document()
        .append("user_id", userId)
        .append("amount", Optional.ofNullable(userSchedule.getString("duration"))
            .map(d -> d.split(":"))
            .map(s -> Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]))
            .map(d -> Math.max(42, d * 42))
            .orElse(42))
        .append("date", currentTimestamp));
  }

  private void insertAudit(ClientSession session, Document userSchedule,
      Date day, Timestamp currentTimestamp) {
    database.getCollection("audit").updateOne(session, 
        new Document()
        .append("schedule_id", userSchedule.getObjectId("_id"))
        .append("day", day), 
        new Document()
        .append("$set", new Document().append("date", currentTimestamp))
        .append("$inc", new Document().append("seats_occupied", 1)),
        new UpdateOptions().upsert(true));
  }

  @Override
  public void close() throws Exception {
    client.close();
  }
}
