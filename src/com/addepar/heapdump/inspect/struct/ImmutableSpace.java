package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface ImmutableSpace extends DynamicHotspotStruct {

  @FieldType("HeapWord*")
  long _bottom();

  @FieldType("HeapWord*")
  long _end();
}
