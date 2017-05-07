package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface Threads extends HotspotStruct {

  @FieldType("JavaThread*")
  JavaThread _thread_list();
}
