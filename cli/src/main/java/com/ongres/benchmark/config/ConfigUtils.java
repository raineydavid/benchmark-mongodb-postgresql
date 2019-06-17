package com.ongres.benchmark.config;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ongres.benchmark.config.model.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.AccessControlException;
import java.util.Set;

public class ConfigUtils {

  private ConfigUtils() {
  }

  /**
   * Parse the YAML configuration file.
   */
  public static Config parseConfig(File configFile) throws IOException {
    try (InputStream inputStream = new FileInputStream(configFile)) {
      return parseConfig(inputStream);
    }
  }

  /**
   * Parse the YAML configuration file.
   */
  public static Config parseConfig(InputStream inputStream) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    configMapper(mapper);

    return mapper.readValue(inputStream, Config.class);
  }

  /**
   * Generate YAML configuration file.
   */
  public static void generateConfig(OutputStream outputStream, Config config) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    configMapper(mapper);
    mapper.writeValue(outputStream, config);
  }

  /**
   * Check the Permissions of the Configuration File.
   */
  public static void checkConfigFilePermissions(Path configFile) throws IOException {
    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(configFile);
    byte owner = 0;
    byte group = 0;
    byte others = 0;
    for (PosixFilePermission perm : perms) {
      switch (perm) {
        case OWNER_EXECUTE:
          owner += 0b001;
          break;
        case OWNER_WRITE:
          owner += 0b010;
          break;
        case OWNER_READ:
          owner += 0b100;
          break;
        case GROUP_EXECUTE:
          group += 0b001;
          break;
        case GROUP_WRITE:
          group += 0b010;
          break;
        case GROUP_READ:
          group += 0b100;
          break;
        case OTHERS_EXECUTE:
          others += 0b001;
          break;
        case OTHERS_WRITE:
          others += 0b010;
          break;
        case OTHERS_READ:
          others += 0b100;
          break;
        default:
          break;
      }
    }

    if (group > 0 || others > 0) {
      System.out.println("\033[33m@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\033[0m");
      System.out.println("\033[33m@ WARNING: UNPROTECTED CONFIGURATION FILE! @\033[0m");
      System.out.println("\033[33m@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\033[0m");

      System.out.printf("Permissions %s%s%s for '%s' are too open.%n", owner, group, others,
          configFile);

      System.out
          .println("It is required that your configuration file are NOT accessible by others.");
      System.out
          .printf("Command to set the permissions: \033[32mchmod 600 %s\033[0m%n%n", configFile);
      System.out.println("The application cannot continue.");

      throw new AccessControlException("Wrong permissions on configuration file.");
    }
  }

  private static void configMapper(ObjectMapper objectMapper) {
    objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
    objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    objectMapper.setSerializationInclusion(Include.NON_EMPTY);
    objectMapper.enable(Feature.ALLOW_COMMENTS);
    objectMapper.enable(Feature.ALLOW_YAML_COMMENTS);
  }
}
