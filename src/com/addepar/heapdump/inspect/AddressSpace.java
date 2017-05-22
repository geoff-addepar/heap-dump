package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.AddressNotMappedException;
import com.addepar.heapdump.inspect.inferior.Inferior;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AddressSpace {
  private static final int PAGE_SIZE = 0x1000; // has to be less than or equal to hardware page size
  private static final int PAGE_MASK = PAGE_SIZE - 1;
  private static final int MAX_CACHE_ENTRIES = 5000;

  private final Inferior inferior;
  private final Long2ObjectLinkedOpenHashMap<ByteBuffer> cache;

  public AddressSpace(Inferior inferior) {
    this.inferior = inferior;
    this.cache = new Long2ObjectLinkedOpenHashMap<>();
    for (int i = 0; i < MAX_CACHE_ENTRIES; i++) {
      // put dummy entries in the cache, with hasRemaining() = true so that it reloads on first use
      cache.put(i, ByteBuffer.allocate(PAGE_SIZE).order(ByteOrder.nativeOrder()));
    }
  }

  public long getPointer(long address) {
    if (inferior.getPointerSize() == 8) {
      return getLong(address);
    } else {
      return getInt(address) & 0xFFFFFFFFL;
    }
  }

  public byte getByte(long address) {
    ByteBuffer buf = getPage(address);
    try {
      return buf.get(pageOffset(address));
    } catch (IndexOutOfBoundsException e) {
      throw new AddressNotMappedException(address);
    }
  }

  public boolean getBoolean(long address) {
    return getByte(address) != 0;
  }

  public char getChar(long address) {
    checkAlignment(address, 2);
    ByteBuffer buf = getPage(address);
    try {
      return buf.getChar(pageOffset(address));
    } catch (IndexOutOfBoundsException e) {
      throw new AddressNotMappedException(address);
    }
  }

  public short getShort(long address) {
    checkAlignment(address, 2);
    ByteBuffer buf = getPage(address);
    try {
      return buf.getShort(pageOffset(address));
    } catch (IndexOutOfBoundsException e) {
      throw new AddressNotMappedException(address);
    }
  }

  public int getInt(long address) {
    checkAlignment(address, 4);
    ByteBuffer buf = getPage(address);
    try {
      return buf.getInt(pageOffset(address));
    } catch (IndexOutOfBoundsException e) {
      throw new AddressNotMappedException(address);
    }
  }

  public long getLong(long address) {
    checkAlignment(address, 8);
    ByteBuffer buf = getPage(address);
    try {
      return buf.getLong(pageOffset(address));
    } catch (IndexOutOfBoundsException e) {
      throw new AddressNotMappedException(address);
    }
  }

  /**
   * Careful, this expects to be passed the address of a string pointer, i.e. a char**
   */
  public String getAsciiString(long addressOfStringPointer) {
    long address = getPointer(addressOfStringPointer);
    if (address == 0) {
      return null;
    }

    long base = pageBase(address);
    int offset = pageOffset(address);

    StringBuilder builder = new StringBuilder();
    ByteBuffer buffer = getPage(base);
    while (true) {
      try {
        byte ch = buffer.get(offset++);
        if (ch == 0) {
          break;
        }
        builder.append((char) ch);
      } catch (IndexOutOfBoundsException e) {
        throw new AddressNotMappedException(base + offset);
      }
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
    ByteBuffer buffer = cache.getAndMoveToLast(pageBase);

    // hasRemaining() is used as a flag that indicates that a read has not yet been done.
    if (buffer == null || buffer.hasRemaining()) {
      if (buffer == null) {
        buffer = cache.removeFirst();
      }

      buffer.clear();
      inferior.read(pageBase, buffer);

      // An incomplete read indicates that a page was not mapped. We need to set the limit to 0 so that bounds checks
      // will happen appropriately. Setting the limit to 0 also ensures that hasRemaining() returns false.
      if (buffer.hasRemaining()) {
        buffer.limit(0);
      }

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

  public long lookupSymbol(String symbolName) {
    return inferior.lookupSymbol(symbolName);
  }

  public long lookupVtable(String typeName) {
    return inferior.lookupVtable(typeName);
  }

  public int getPointerSize() {
    return inferior.getPointerSize();
  }

  public boolean isMapped(long address) {
    return inferior.isMapped(address);
  }

  public void reset() {
    ObjectIterator<Long2ObjectMap.Entry<ByteBuffer>> it = cache.long2ObjectEntrySet().fastIterator();
    while (it.hasNext()) {
      it.next().getValue().clear(); // set position less than limit, make hasRemaining() return true so that it reloads
    }

    inferior.reset();
  }
}
