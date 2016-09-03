package com.addepar.heapdump.debugger;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.DebuggerException;
import sun.jvm.hotspot.debugger.NotInHeapException;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.debugger.UnalignedAddressException;
import sun.jvm.hotspot.debugger.UnmappedAddressException;

/**
 * Mostly copied from LinuxAddress
 */
public class SelfAddress implements Address {
  protected SelfDebugger debugger;
  protected long addr;

  public SelfAddress(SelfDebugger debugger, long addr) {
    this.debugger = debugger;
    this.addr = addr;
  }

  public boolean equals(Object arg) {
    if (!(arg instanceof SelfAddress)) {
      return false;
    }
    SelfAddress other = (SelfAddress) arg;
    return this.addr == other.addr;
  }

  public int hashCode() {
    return (int)this.addr;
  }

  public String toString() {
    return this.debugger.addressValueToString(this.addr);
  }

  public long getCIntegerAt(long offset, long numBytes, boolean isUnsigned) throws
      UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readCInteger(this.addr + offset, numBytes, isUnsigned);
  }

  public Address getAddressAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readAddress(this.addr + offset);
  }

  public Address getCompOopAddressAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readCompOopAddress(this.addr + offset);
  }

  public Address getCompKlassAddressAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readCompKlassAddress(this.addr + offset);
  }

  public boolean getJBooleanAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJBoolean(this.addr + offset);
  }

  public byte getJByteAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJByte(this.addr + offset);
  }

  public char getJCharAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJChar(this.addr + offset);
  }

  public double getJDoubleAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJDouble(this.addr + offset);
  }

  public float getJFloatAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJFloat(this.addr + offset);
  }

  public int getJIntAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJInt(this.addr + offset);
  }

  public long getJLongAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJLong(this.addr + offset);
  }

  public short getJShortAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return this.debugger.readJShort(this.addr + offset);
  }

  public OopHandle getOopHandleAt(long offset) throws UnalignedAddressException, UnmappedAddressException,
      NotInHeapException {
    return this.debugger.readOopHandle(this.addr + offset);
  }

  public OopHandle getCompOopHandleAt(long offset) throws UnalignedAddressException, UnmappedAddressException, NotInHeapException {
    return this.debugger.readCompOopHandle(this.addr + offset);
  }

  public void setCIntegerAt(long offset, long numBytes, long value) {
    throw new DebuggerException("Unimplemented");
  }

  public void setAddressAt(long offset, Address value) {
    throw new DebuggerException("Unimplemented");
  }

  public void setJBooleanAt(long offset, boolean value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setJByteAt(long offset, byte value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setJCharAt(long offset, char value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setJDoubleAt(long offset, double value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setJFloatAt(long offset, float value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setJIntAt(long offset, int value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setJLongAt(long offset, long value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setJShortAt(long offset, short value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public void setOopHandleAt(long offset, OopHandle value) throws UnmappedAddressException, UnalignedAddressException {
    throw new DebuggerException("Unimplemented");
  }

  public Address addOffsetTo(long offset) throws UnsupportedOperationException {
    long value = this.addr + offset;
    return value == 0L?null:new SelfAddress(this.debugger, value);
  }

  public OopHandle addOffsetToAsOopHandle(long offset) throws UnsupportedOperationException {
    long value = this.addr + offset;
    return value == 0L?null:new SelfOopHandle(this.debugger, value);
  }

  public long minus(Address arg) {
    return arg == null?this.addr:this.addr - ((SelfAddress)arg).addr;
  }

  public boolean lessThan(Address a) {
    if(a == null) {
      return false;
    } else {
      SelfAddress arg = (SelfAddress)a;
      return Long.compareUnsigned(this.addr, arg.addr) < 0;
    }
  }

  public boolean lessThanOrEqual(Address a) {
    if(a == null) {
      return false;
    } else {
      SelfAddress arg = (SelfAddress)a;
      return Long.compareUnsigned(this.addr, arg.addr) <= 0;
    }
  }

  public boolean greaterThan(Address a) {
    if(a == null) {
      return true;
    } else {
      SelfAddress arg = (SelfAddress)a;
      return Long.compareUnsigned(this.addr, arg.addr) > 0;
    }
  }

  public boolean greaterThanOrEqual(Address a) {
    if(a == null) {
      return true;
    } else {
      SelfAddress arg = (SelfAddress)a;
      return Long.compareUnsigned(this.addr, arg.addr) >= 0;
    }
  }

  public Address andWithMask(long mask) throws UnsupportedOperationException {
    long value = this.addr & mask;
    return value == 0L?null:new SelfAddress(this.debugger, value);
  }

  public Address orWithMask(long mask) throws UnsupportedOperationException {
    long value = this.addr | mask;
    return value == 0L?null:new SelfAddress(this.debugger, value);
  }

  public Address xorWithMask(long mask) throws UnsupportedOperationException {
    long value = this.addr ^ mask;
    return value == 0L?null:new SelfAddress(this.debugger, value);
  }

  long getValue() {
    return this.addr;
  }
}
