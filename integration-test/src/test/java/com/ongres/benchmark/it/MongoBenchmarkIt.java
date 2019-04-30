
package com.ongres.benchmark.it;

import com.ongres.benchmark.App;
import com.ongres.junit.docker.Container;
import com.ongres.junit.docker.ContainerParam;
import com.ongres.junit.docker.DockerContainer;
import com.ongres.junit.docker.DockerExtension;
import com.ongres.junit.docker.Mount;
import com.ongres.junit.docker.Port;
import com.ongres.junit.docker.WaitFor;
import com.ongres.junit.docker.WhenReuse;
import com.spotify.docker.client.exceptions.DockerException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

@DockerExtension({
    @DockerContainer(
      alias = "mongo",
      image = "mongo:4",
      ports = { @Port(internal = 27017) },
      arguments = { "bash", "-c", 
          "(docker-entrypoint.sh mongod --replSet rs0) &"
              + " while ! mongo --eval 'rs.initiate()'"
              + "   > /dev/null 2>&1; do sleep 1; done;"
              + " for i in 1 2 3; do"
              + "   while ! mongo --eval 'db.getCollectionNames()'"
              + "     > /dev/null 2>&1; do sleep 1; done;"
              + " sleep 1; done;"
              + " echo 'MongoDB is ready!';"
              + " seq -s ' ' 10000000 10000910;"
              + " wait" },
      waitFor = @WaitFor("MongoDB is ready!"),
      mounts = { @Mount(value = "@/log4j2.xml", path = "/resources") },
      whenReuse = WhenReuse.ALWAYS)
})
public class MongoBenchmarkIt {

  private static final Logger logger = LogManager.getLogger();

  @Test
  public void benchmarkTest(@ContainerParam("mongo") Container mongo) throws Exception {
    setupBenchmark(mongo);
    App.test(
        "--benchmark-target", "mongo", 
        "--parallelism", "4", 
        "--target-database-port", "" + mongo.getPort(27017),
        "--target-database-user", "",
        "--target-database-name", "test",
        "--metrics", "PT10S",
        "--metrics-reporter", "log",
        "--transactions", "1000");
  }

  private void setupBenchmark(Container mongo) throws DockerException, InterruptedException {
    mongo.execute("bash", "-c", "cat /resources/aircrafts.txt | sed 's/;/,/g' | sed 's/\\\\N//g'"
        + " | mongoimport -d test -c aircraft --drop --type csv"
        + " --fields name,icao,iata,capacity,country --ignoreBlanks")
        .forEach(line -> logger.info(line));
    mongo.execute("bash", "-c", "cat /resources/schedule.txt | sed 's/;/,/g' | sed 's/\\\\N//g'"
        + " | mongoimport -d test -c schedule --drop --type csv"
        + " --fields from_airport,to_airport,valid_from,valid_until,days"
        + ",departure,arrival,flight,aircraft,duration --ignoreBlanks")
        .forEach(line -> logger.info(line));
    mongo.execute("mongo", "--eval", "db.getCollection(\"seat\").drop()")
        .forEach(line -> logger.info(line));
    mongo.execute("mongo", "--eval", "db.createCollection(\"seat\")")
        .forEach(line -> logger.info(line));
    mongo.execute("mongo", "--eval", "db.getCollection(\"payment\").drop()")
        .forEach(line -> logger.info(line));
    mongo.execute("mongo", "--eval", "db.createCollection(\"payment\")")
        .forEach(line -> logger.info(line));
    mongo.execute("mongo", "--eval", "db.getCollection(\"audit\").drop()")
        .forEach(line -> logger.info(line));
    mongo.execute("mongo", "--eval", "db.createCollection(\"audit\")")
        .forEach(line -> logger.info(line));
  }
}
