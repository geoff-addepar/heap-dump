package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.AddressField;
import com.addepar.heapdump.inspect.FieldType;

public interface Flag extends HotspotStruct {

  @FieldType("const char*")
  String _type();

  @FieldType("const char*")
  String _name();

  @AddressField
  long _addr();

  @FieldType("Flag*")
  Flag flags();

  @FieldType("size_t")
  long numFlags();
}
