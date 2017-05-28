package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface G1CollectedHeap extends CollectedHeap {

  @FieldType("HeapRegionManager")
  HeapRegionManager _hrm();
}
