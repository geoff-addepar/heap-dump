package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface HeapRegionManager extends HotspotStruct {

  @FieldType("G1HeapRegionTable")
  G1HeapRegionTable _regions();
}
