package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface ConcurrentMarkSweepGeneration extends Generation {

  @FieldType("CompactibleFreeListSpace*")
  CompactibleFreeListSpace _cmsSpace();
}
