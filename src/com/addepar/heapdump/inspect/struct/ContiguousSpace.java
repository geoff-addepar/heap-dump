package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;
import com.google.common.collect.Range;

public interface ContiguousSpace extends Space {

  @FieldType("HeapWord*")
  long _top();

  default Range<Long> getLiveRange() {
    return Range.closedOpen(_bottom(), _top());
  }
}
