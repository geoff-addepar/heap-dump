package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface G1OffsetTableContigSpace extends Space {

  @FieldType("HeapWord*")
  long _top();
}
