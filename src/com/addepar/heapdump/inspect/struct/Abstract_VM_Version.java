package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface Abstract_VM_Version extends HotspotStruct {

  @FieldType("int")
  int _reserve_for_allocation_prefetch();
}
