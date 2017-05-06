package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface Universe extends HotspotStruct {
  @FieldType("CollectedHeap*")
  CollectedHeap _collectedHeap();

  @FieldType("address")
  long _narrow_klass__base();

  @FieldType("int")
  int _narrow_klass__shift();
}
