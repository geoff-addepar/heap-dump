package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface MutableSpace extends ImmutableSpace {

  @FieldType("HeapWord*")
  long _top();
}
