package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface ThreadLocalAllocBuffer extends HotspotStruct {

  @FieldType("HeapWord*")
  long _start();

  @FieldType("HeapWord*")
  long _top();

  @FieldType("HeapWord*")
  long _end();
}
