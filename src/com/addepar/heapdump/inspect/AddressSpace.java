package com.addepar.heapdump.inspect;

import java.nio.ByteBuffer;

public class AddressSpace {
  private static final int PAGE_SIZE = 0x10000;
  private static final int PAGE_MASK = PAGE_SIZE - 1;

  private final Inferior inferior;
  private final PageCache cache;

  public AddressSpace(Inferior inferior) {
    this.inferior = inferior;
    this.cache = new PageCache();
  }

  public final long getPointer(long address) {
    if (inferior.getPointerSize() == 8) {
      return getLong(address);
    } else {
      return getInt(address) & 0xFFFFFFFFL;
    }
  }

  public byte getByte(long address) {
    return getPage(address).get(pageOffset(address));
  }

  public boolean getBoolean(long address) {
    return getByte(address) != 0;
  }

  public char getChar(long address) {
    checkAlignment(address, 2);
    return getPage(address).getChar(pageOffset(address));
  }

  public short getShort(long address) {
    checkAlignment(address, 2);
    return getPage(address).getShort(pageOffset(address));
  }

  public int getInt(long address) {
    checkAlignment(address, 4);
    return getPage(address).getInt(pageOffset(address));
  }

  public long getLong(long address) {
    checkAlignment(address, 8);
    return getPage(address).getLong(pageOffset(address));
  }

  public String getAsciiString(long address) {
    if (address == 0) {
      return null;
    }

    long base = pageBase(address);
    int offset = pageOffset(address);

    StringBuilder builder = new StringBuilder();
    ByteBuffer buffer = getPage(base);
    while (true) {
      byte ch = buffer.get(offset++);
      if (ch == 0) {
        break;
      }
      builder.append((char) ch);
      if (offset == PAGE_SIZE) {
        offset = 0;
        base += PAGE_SIZE;
        buffer = getPage(base);
      }
    }
    return builder.toString();
  }

  private ByteBuffer getPage(long address) {
    long pageBase = pageBase(address);
    ByteBuffer buffer = cache.get(pageBase);
    if (buffer == null) {
      buffer = inferior.read(pageBase, PAGE_SIZE);
      cache.put(pageBase, buffer);
    }
    return buffer;
  }

  private long pageBase(long address) {
    return address & ~PAGE_MASK;
  }

  private int pageOffset(long address) {
    return (int) address & PAGE_MASK;
  }

  private void checkAlignment(long address, long alignment) {
    if ((address & (alignment - 1)) != 0) {
      throw new IllegalArgumentException("Address " + Long.toHexString(address)
          + " is not aligned on a multiple of " + alignment);
    }
  }
}
