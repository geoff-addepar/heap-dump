package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface PSOldGen extends DynamicHotspotStruct {

  @FieldType("MutableSpace*")
  MutableSpace _object_space();
}
