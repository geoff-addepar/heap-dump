package com.addepar.heapdump.inspect.inferior;

import java.nio.ByteBuffer;

public interface Inferior {
  ByteBuffer read(long address, int size);

  int getPointerSize();

  long lookupSymbol(String symbolName);

  long lookupVtable(String typeName);

  void detach();
}
