package com.addepar.heapdump.debugger;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class SelfOopHandle extends SelfAddress implements OopHandle {
  public SelfOopHandle(SelfDebugger debugger, long addr) {
    super(debugger, addr);
  }

  public boolean equals(Object arg) {
    if (!(arg instanceof SelfOopHandle)) {
      return false;
    }
    SelfOopHandle other = (SelfOopHandle) arg;
    return this.addr == other.addr;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(addr);
  }

  public Address addOffsetTo(long offset) throws UnsupportedOperationException {
    throw new UnsupportedOperationException("addOffsetTo not applicable to OopHandles (interior object pointers not allowed)");
  }

  public Address andWithMask(long mask) throws UnsupportedOperationException {
    throw new UnsupportedOperationException("andWithMask not applicable to OopHandles (i.e., anything but C addresses)");
  }

  public Address orWithMask(long mask) throws UnsupportedOperationException {
    throw new UnsupportedOperationException("orWithMask not applicable to OopHandles (i.e., anything but C addresses)");
  }

  public Address xorWithMask(long mask) throws UnsupportedOperationException {
    throw new UnsupportedOperationException("xorWithMask not applicable to OopHandles (i.e., anything but C addresses)");
  }
}
