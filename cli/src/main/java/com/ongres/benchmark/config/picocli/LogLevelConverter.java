package com.ongres.benchmark.config.picocli;

import com.ongres.benchmark.config.model.LogLevel;

import java.util.Locale;

import picocli.CommandLine.ITypeConverter;

public class LogLevelConverter implements ITypeConverter<String> {

  @Override
  public String convert(String value) throws Exception {
    LogLevel.valueOf(value.toUpperCase(Locale.US));
    return value;
  }

}
