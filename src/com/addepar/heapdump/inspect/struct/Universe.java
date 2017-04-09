package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface Universe extends HotspotStruct {
  @FieldType("CollectedHeap*")
  CollectedHeap _collectedHeap();
}
