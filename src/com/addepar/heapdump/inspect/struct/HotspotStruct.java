package com.addepar.heapdump.inspect.struct;

/**
 * Base for all struct types
 */
public interface HotspotStruct {

  long getAddress();

  void setAddress(long address);
}
