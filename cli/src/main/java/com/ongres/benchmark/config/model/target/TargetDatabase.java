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

package com.ongres.benchmark.config.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ongres.benchmark.config.model.Database;
import com.ongres.benchmark.config.picocli.PositiveIntegerConverter;

import picocli.CommandLine.Option;

public class TargetDatabase implements Database {

  @Option(names = {"--target-database-host"},
      description = "Host address of the target database to apply the changes to",
      required = true)
  @JsonProperty
  private String host = "localhost";

  @Option(names = {"--target-database-port"},
      description = "Port of the target database to apply the changes to",
      required = true,
      converter = PositiveIntegerConverter.class)
  @JsonProperty
  private int port = 5432;

  @Option(names = {"--target-database-name"}, 
      description = "Target database name to apply the changes to",
      required = true)
  @JsonProperty
  private String name = "postgres";

  @Option(names = {"--target-database-user"},
      description = "User to connect to the target database to apply the changes to",
      required = true)
  @JsonProperty
  private String user = "postgres";

  @Option(names = {"--target-database-password"},
      description = "Password for the selected user of the target database"
          + " to apply the changes to",
      required = true)
  @JsonProperty
  private String password = "";

  @Override
  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  @Override
  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  @Override
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

}
