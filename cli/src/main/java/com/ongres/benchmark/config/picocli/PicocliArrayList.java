package com.ongres.benchmark.config.picocli;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class PicocliArrayList<E> extends ArrayList<E> {

  private static final long serialVersionUID = 1L;

  public PicocliArrayList() {
    super();
  }

  public PicocliArrayList(Collection<? extends E> c) {
    super(c);
  }

  public PicocliArrayList(int initialCapacity) {
    super(initialCapacity);
  }

  @Override
  public String toString() {
    return stream()
        .map(e -> e.toString())
        .collect(Collectors.joining(","));
  }

}
