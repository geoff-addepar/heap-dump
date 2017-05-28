package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface DefNewGeneration extends Generation {

  @FieldType("EdenSpace*")
  ContiguousSpace _eden_space();

  @FieldType("ContiguousSpace*")
  ContiguousSpace _from_space();
}
