package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.AddressRange;
import com.addepar.heapdump.inspect.FieldType;

public interface MutableSpace extends ImmutableSpace {

  @FieldType("HeapWord*")
  long _top();

  default AddressRange getLiveRange() {
    return new AddressRange(_bottom(), _top());
  }
}
