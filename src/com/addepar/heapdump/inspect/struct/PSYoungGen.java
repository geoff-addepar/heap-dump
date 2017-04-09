package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface PSYoungGen extends DynamicHotspotStruct {

  @FieldType("MutableSpace*")
  MutableSpace _eden_space();

  @FieldType("MutableSpace*")
  MutableSpace _from_space();

}
