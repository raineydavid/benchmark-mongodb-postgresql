package com.ongres.benchmark.config.picocli;

import java.time.Duration;
import java.time.format.DateTimeParseException;

import picocli.CommandLine.ITypeConverter;

public class DurationConverter implements ITypeConverter<String> {

  @Override
  public String convert(String value) throws Exception {
    try {
      Duration.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(ex);
    }
    return value;
  }

}
