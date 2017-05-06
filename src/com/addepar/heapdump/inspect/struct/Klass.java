package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;
import com.addepar.heapdump.inspect.Hotspot;

public interface Klass extends DynamicHotspotStruct {

  @FieldType("Klass*")
  Klass _super();

  @FieldType("Symbol*")
  Symbol _name();

  @FieldType("jint")
  int _layout_helper();

  default String getName(Hotspot hotspot) {
    return _name().getStringValue(hotspot);
  }

  default long getObjectSize(oopDesc oop, Hotspot hotspot) {
    // We could try to dynamic cast this object to a more appropriate type, but it's easier to just look at the
    // layout helper to find the size
    int layoutHelper = _layout_helper();
    int tag = layoutHelper >> hotspot.getConstants().getLayoutHelperArrayTagShift();

    if (tag == 0) {
      // It's a regular object instance. The layout helper gives the size in bytes, except we need to mask off the
      // slow-path-allocation bit
      return layoutHelper & ~hotspot.getConstants().getLayoutHelperInstanceSlowPathBit();
    } else {
      // It's an array. The layout helper contains the header size and element size.
      long headerSize = (layoutHelper >> hotspot.getConstants().getLayoutHelperHeaderSizeShift())
          & hotspot.getConstants().getLayoutHelperHeaderSizeMask();
      long elementSize = 1L << ((layoutHelper >> hotspot.getConstants().getLayoutHelperLog2ElementSizeShift())
          & hotspot.getConstants().getLayoutHelperLog2ElementSizeMask());
      int numElements = hotspot.getAddressSpace().getInt(oop.getAddress() + hotspot.arrayLengthOffset());
      return headerSize + elementSize * numElements;
    }
  }
}
