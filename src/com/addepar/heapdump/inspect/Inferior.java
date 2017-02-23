package com.addepar.heapdump.inspect;

import java.nio.ByteBuffer;

public interface Inferior {
  ByteBuffer read(long address, int size);

  int getPointerSize();

  void detach();
}
