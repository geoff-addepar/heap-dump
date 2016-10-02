package com.addepar.heapdump;

import java.util.HashMap;
import java.util.Map;

/**
 * A utility that uses up all the memory in the heap, so that I can take core dumps while the garbage collector is
 * running
 */
public class MemoryHog implements Runnable {
  private Map<String, String> map = new HashMap<>();

  public void run() {
    int i = 0;
    while (true) {
      map.put("key" + i, "value" + i);

      // slowly remove old keys so that we stress the GC rather than instantly cause an OutOfMemoryError
      map.remove("key" + (i / 2));
      i++;
    }
  }

  public static void main(String[] args) {
    new MemoryHog().run();
  }
}
