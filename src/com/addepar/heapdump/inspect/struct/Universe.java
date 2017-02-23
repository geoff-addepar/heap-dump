package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface Universe {
  @FieldType("CollectedHeap*")
  CollectedHeap _collectedHeap();
}
