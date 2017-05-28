package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface G1HeapRegionTable extends HotspotStruct {

  @FieldType("address")
  long _base();

  @FieldType("size_t")
  long _length();
}
