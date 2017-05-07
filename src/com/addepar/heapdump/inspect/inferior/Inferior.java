package com.addepar.heapdump.inspect.inferior;

import java.nio.ByteBuffer;

public interface Inferior {
  void read(long address, ByteBuffer buffer);

  boolean isMapped(long address);

  int getPointerSize();

  long lookupSymbol(String symbolName);

  long lookupVtable(String typeName);

  void detach();
}
