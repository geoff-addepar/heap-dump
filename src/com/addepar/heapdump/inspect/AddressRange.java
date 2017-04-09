package com.addepar.heapdump.inspect;

/**
 * A range of addresses: [start, end)
 */
public class AddressRange {

  private final long start;
  private final long end;

  public AddressRange(long start, long end) {
    this.start = start;
    this.end = end;
  }

  public long getStart() {
    return start;
  }

  public long getEnd() {
    return end;
  }

  @Override
  public String toString() {
    return Long.toHexString(start) + "-" + Long.toHexString(end);
  }
}
