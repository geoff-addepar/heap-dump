package com.addepar.heapdump.inspect.struct;

import com.google.common.collect.Range;

public interface HeapRegion extends G1OffsetTableContigSpace {

  default Range<Long> getLiveRange() {
    return Range.closedOpen(_bottom(), _top());
  }
}
