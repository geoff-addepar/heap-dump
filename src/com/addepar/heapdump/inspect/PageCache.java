package com.addepar.heapdump.inspect;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class PageCache extends LinkedHashMap<Long, ByteBuffer> {

  private static final int MAX_ENTRIES = 1000;

  @Override
  protected boolean removeEldestEntry(Map.Entry<Long, ByteBuffer> eldest) {
    return size() > MAX_ENTRIES;
  }
}
