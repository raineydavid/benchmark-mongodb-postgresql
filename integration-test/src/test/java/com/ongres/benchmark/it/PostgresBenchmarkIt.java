
package com.ongres.benchmark.it;

import com.ongres.benchmark.App;
import com.ongres.benchmark.jdbc.ConnectionSupplier;
import com.ongres.benchmark.jdbc.HikariConnectionSupplier;
import com.ongres.benchmark.jdbc.PostgresConnectionSupplier;
import com.ongres.junit.docker.Container;
import com.ongres.junit.docker.ContainerParam;
import com.ongres.junit.docker.DockerContainer;
import com.ongres.junit.docker.DockerExtension;
import com.ongres.junit.docker.Mount;
import com.ongres.junit.docker.Port;
import com.ongres.junit.docker.WaitFor;
import com.ongres.junit.docker.WhenReuse;
import com.spotify.docker.client.exceptions.DockerException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.postgresql.PGProperty;

@DockerExtension({
    @DockerContainer(
        alias = "postgres",
        image = "postgres:11",
        ports = { @Port(internal = 5432) },
        arguments = { "bash", "-c", 
            "(docker-entrypoint.sh postgres) &"
                + " for i in 1 2 3; do"
                + "   while ! psql -h localhost -U postgres"
                + "     -c 'SELECT 1' > /dev/null 2>&1; do sleep 1; done;"
                + " sleep 1; done;"
                + " echo 'PostgreSQL is ready!';"
                + " seq -s ' ' 10000000 10000910;"
                + " wait" },
        waitFor = @WaitFor("PostgreSQL is ready!"),
        mounts = { @Mount(value = "@/log4j2.xml", path = "/resources") },
        whenReuse = WhenReuse.ALWAYS)
})
public class PostgresBenchmarkIt {

  private static final Logger logger = LogManager.getLogger();

  @Test
  public void benchmarkTest(@ContainerParam("postgres") Container postgres) throws Exception {
    setupBenchmark(postgres);
    App.test(
        "--benchmark-target", "postgres", 
        "--parallelism", "20", 
        "--day-range", "30", 
        "--booking-sleep", "0", 
        "--connection-wait-timeout", "PT0S", 
        "--target-database-port", "" + postgres.getPort(5432),
        "--metrics", "PT10S",
        "--metrics-reporter", "log",
        "--transactions", "300000");
  }

  private void setupBenchmark(Container postgres)
      throws DockerException, InterruptedException, SQLException, IOException {
    Properties jdbcProperties = new Properties();
    PGProperty.PG_HOST.set(jdbcProperties, "localhost");
    PGProperty.PG_PORT.set(jdbcProperties, postgres.getPort(5432));
    PGProperty.PG_DBNAME.set(jdbcProperties, "postgres");
    PGProperty.USER.set(jdbcProperties, "postgres");
    PGProperty.PASSWORD.set(jdbcProperties, "");
    try (ConnectionSupplier connectionSupplier =
        new HikariConnectionSupplier(new PostgresConnectionSupplier(jdbcProperties));
        Connection connection = connectionSupplier.get();
        Statement statement = connection.createStatement()) {
      logger.info("Cleanup schema");
      statement.execute("drop table if exists audit");
      statement.execute("drop table if exists payment");
      statement.execute("drop table if exists seat");
      statement.execute("drop table if exists schedule");
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
          + "schedule_id uuid not null,"
          + "day date not null,"
          + "date timestamp without time zone,"
          + "primary key (user_id,schedule_id,day))");
      statement.execute("create table payment ("
          + "user_id bigint,"
          + "amount money,"
          + "date timestamp without time zone)");
      statement.execute("create table audit ("
          + "schedule_id uuid not null,"
          + "day date not null,"
          + "date timestamp without time zone,"
          + "seats_occupied int,"
          + "primary key (schedule_id,day))");
      connection.commit();
      logger.info("Importing data");
      postgres.execute("psql", "-U", "postgres", "-c", 
          "\\copy aircraft from '/resources/aircrafts.txt'"
              + " with csv header delimiter ';' null '\\N'")
        .forEach(line -> logger.info(line));
      postgres.execute("psql", "-U", "postgres", "-c", 
          "\\copy schedule from '/resources/schedule.txt'"
              + " with csv header delimiter ';' null '\\N'")
        .forEach(line -> logger.info(line));
      connection.commit();
      statement.execute("alter table schedule add column schedule_id uuid"
          + " primary key default uuid_generate_v4()");
      statement.execute("alter table seat add"
          + " foreign key (schedule_id) references schedule(schedule_id)");
      statement.execute("alter table audit add"
          + " foreign key (schedule_id) references schedule(schedule_id)");
      connection.commit();
    }
  }
}
