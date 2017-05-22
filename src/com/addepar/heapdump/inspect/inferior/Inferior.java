package com.addepar.heapdump.inspect.inferior;

import java.nio.ByteBuffer;

public interface Inferior {
  void read(long address, ByteBuffer buffer);

  boolean isMapped(long address);

  /**
   * Reload any cached data that may change while the inferior is running, such as address space maps
   */
  void reset();

  int getPointerSize();

  long lookupSymbol(String symbolName);

  long lookupVtable(String typeName);

  void detach();
}
