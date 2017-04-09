package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface ParallelScavengeHeap extends CollectedHeap {

  @FieldType("PSYoungGen*")
  PSYoungGen _young_gen();

  @FieldType("PSOldGen*")
  PSOldGen _old_gen();
}
