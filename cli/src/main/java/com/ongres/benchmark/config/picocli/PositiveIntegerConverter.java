package com.ongres.benchmark.config.picocli;

import com.google.common.base.Preconditions;

import picocli.CommandLine.ITypeConverter;

public class PositiveIntegerConverter implements ITypeConverter<Integer> {

  @Override
  public Integer convert(String value) throws Exception {
    int positiveValue = Integer.parseInt(value);
    Preconditions.checkArgument(positiveValue >= 0,
        positiveValue + " is not greater or equals to 0");
    return positiveValue;
  }
}
