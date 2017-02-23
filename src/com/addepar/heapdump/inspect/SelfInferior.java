package com.addepar.heapdump.inspect;

import com.sun.jna.Pointer;

import java.nio.ByteBuffer;

public class SelfInferior implements Inferior {
  private final int pointerSize; // either 4 or 8, indicating 32-bit or 64-bit respectively

  public SelfInferior() {
    switch (System.getProperty("os.arch")) {
      case "amd64":
        this.pointerSize = 8;
        break;

      default:
        throw new IllegalStateException("Unrecognized architecture " + System.getProperty("os.arch"));
    }
  }

  @Override
  public ByteBuffer read(long address, int size) {
    return new Pointer(address).getByteBuffer(0, size);
  }

  @Override
  public int getPointerSize() {
    return pointerSize;
  }

  @Override
  public void detach() {
    // do nothing
  }
}
