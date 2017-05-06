package com.addepar.heapdump.inspect.inferior;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class SelfInferior implements Inferior {
  private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0).order(ByteOrder.nativeOrder());
  private FileChannel selfMem;
  private final SelfSymbolLookup symbolLookup;
  private final int pointerSize; // either 4 or 8, indicating 32-bit or 64-bit respectively

  public SelfInferior() throws IOException {
    switch (System.getProperty("os.arch")) {
      case "amd64":
        this.pointerSize = 8;
        break;

      default:
        throw new IllegalStateException("Unrecognized architecture " + System.getProperty("os.arch"));
    }

    selfMem = FileChannel.open(Paths.get("/proc/self/mem"), StandardOpenOption.READ);
    symbolLookup = new SelfSymbolLookup();
  }

  @Override
  public ByteBuffer read(long address, int size) {
    ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.nativeOrder());
    try {
      selfMem.read(buf, address);
    } catch (IOException e) {
      return EMPTY_BUFFER;
    }
    return buf;
  }

  @Override
  public int getPointerSize() {
    return pointerSize;
  }

  @Override
  public long lookupSymbol(String symbolName) {
    return symbolLookup.lookup(symbolName);
  }

  @Override
  public long lookupVtable(String typeName) {
     return symbolLookup.lookup("_ZTV" + typeName.length() + typeName) + 2 * pointerSize;
  }

  @Override
  public void detach() {
    try {
      selfMem.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
