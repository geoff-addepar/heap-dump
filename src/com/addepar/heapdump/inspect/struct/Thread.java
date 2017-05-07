package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface Thread extends HotspotStruct {

  @FieldType("ThreadLocalAllocBuffer")
  ThreadLocalAllocBuffer _tlab();
}
