package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface OneContigSpaceCardGeneration extends Generation {

  @FieldType("ContiguousSpace*")
  ContiguousSpace _the_space();
}
