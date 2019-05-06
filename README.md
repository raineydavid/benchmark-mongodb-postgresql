# benchmark

## Table of content

* [What is it?](#what-is-it)
* [Architecture](#architecture)
* [How to build](#how-to-build)
* [Maven Profiles](#maven-profiles)
    * [Integration tests](#integration-tests)
* [How to run it](#how-to-run-it)

## What is it?

This tool will perform a benchmark against configured database using existing or provided scripts.

## Architecture

## How to build

Java 8 JDK and Maven are required to build this project (you can replace all `mvn` commands with `./mvnw` that will launch a provided wrapper that will download and install Maven for ease of use).

Run following command:

```
mvn clean package
```

The command will compile source code and generate an uber-JAR archive in ```cli/target/benchmark-<version>.jar``

## Maven Profiles

- Safer: Slower but safer profile used to look for errors before pushing to SCM 

```
mvn verify -P safer
```

### Integration tests

The integration test suite requires that Docker is installed on the system and available to the user. 
To launch the integrations tests run the following command:

```
mvn verify -P integration
```

To run integration tests with Java debugging enabled on port 8000:

```
mvn verify -P integration -Dmaven.failsafe.debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000"
```

## How to run it

Go to the root folder of the project and run the following commands:

```
java -jar cli/target/benchmark-<version>.jar
```

