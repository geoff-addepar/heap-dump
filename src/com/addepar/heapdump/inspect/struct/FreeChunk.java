package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;
import com.addepar.heapdump.inspect.Hotspot;

public interface FreeChunk extends HotspotStruct {

  @FieldType("size_t")
  long _size();

  @FieldType("FreeChunk*")
  long _next();

  @FieldType("FreeChunk*")
  long _prev();

  default long size(Hotspot hotspot) {
    if (hotspot.useCompressedOops()) {
      return (_size() >>> hotspot.getConstants().getMarkOopSizeShift()) * hotspot.getConstants().getHeapWordSize();
    } else {
      return _size() * hotspot.getConstants().getHeapWordSize();
    }
  }

  default boolean isFreeChunk(Hotspot hotspot) {
    if (hotspot.useCompressedOops()) {
      long markOop = hotspot.getAddressSpace().getPointer(getAddress());
      return (markOop & hotspot.getConstants().getMarkOopCmsMask()) != 0;
    } else {
      return (_prev() & 0x1) == 0x1;
    }
  }
}
