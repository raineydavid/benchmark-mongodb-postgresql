
package com.ongres.benchmark.it;

import com.ongres.benchmark.App;
import com.ongres.junit.docker.Container;
import com.ongres.junit.docker.ContainerParam;
import com.ongres.junit.docker.DockerContainer;
import com.ongres.junit.docker.DockerExtension;
import com.ongres.junit.docker.Port;
import com.ongres.junit.docker.WaitFor;
import com.ongres.junit.docker.WhenReuse;

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
      whenReuse = WhenReuse.ALWAYS)
})
public class MongoBenchmarkIt {

  @Test
  public void benchmarkTest(@ContainerParam("mongo") Container mongo) throws Exception {
    App.test(
        "--benchmark-target", "mongo", 
        "--parallelism", "20", 
        "--day-range", "1", 
        "--booking-sleep", "0", 
        "--target-database-port", "" + mongo.getPort(27017),
        "--target-database-user", "",
        "--target-database-name", "test",
        "--metrics", "PT10S",
        "--metrics-reporter", "log",
        "--duration", "PT60S");
  }
}
