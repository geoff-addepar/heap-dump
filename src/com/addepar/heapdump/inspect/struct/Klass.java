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

  /**
   * Returns the size in bytes.
   */
  default long getObjectSize(oopDesc oop, Hotspot hotspot) {
    // See oopDesc::size_given_klass for why it works this way
    int layoutHelper = _layout_helper();

    if (layoutHelper > 0) {
      // It's an instance (non-array) oop
      if ((layoutHelper & hotspot.getConstants().getLayoutHelperInstanceSlowPathBit()) == 0) {
        // The slow path bit is not set, so the layoutHelper contains the exact size (this is the fast path and covers
        // most common objects)
        return layoutHelper;
      } else {
        // This might be an instance of java.lang.Class. Since a Class contains its static fields, each one is a
        // different size. The Class contains an oop_size field that indicates how big it is.
        if ("InstanceMirrorKlass".equals(hotspot.getTypes().getDynamicType(getAddress()).getTypeName())) {
          int sizeInWords = hotspot.getAddressSpace().getInt(oop.getAddress() + hotspot.getClassOopSizeOffset());
          return sizeInWords * hotspot.getConstants().getHeapWordSize();
        } else {
          return layoutHelper & ~hotspot.getConstants().getLayoutHelperInstanceSlowPathBit();
        }
      }
    } else if (layoutHelper < 0) {
      // It's an array. The layout helper contains the header size and element size.
      long headerSize = (layoutHelper >> hotspot.getConstants().getLayoutHelperHeaderSizeShift())
          & hotspot.getConstants().getLayoutHelperHeaderSizeMask();
      long log2ElementSize = (layoutHelper >> hotspot.getConstants().getLayoutHelperLog2ElementSizeShift())
          & hotspot.getConstants().getLayoutHelperLog2ElementSizeMask();
      // The number of elements is stored at the beginning of the array object
      int numElements = hotspot.getAddressSpace().getInt(oop.getAddress() + hotspot.arrayLengthOffset());
      // This should align up to MinObjAlignmentInBytes but I don't see how to get that constant
      return hotspot.alignUp(headerSize + (numElements << log2ElementSize), hotspot.getAddressSpace().getPointerSize());
    } else {
      // The hotspot source code seems to imply this could happen, but I honestly don't understand how it's possible,
      // given the current Oop hierarchy.
      throw new RuntimeException("Zero size object??? klass=" + hotspot.getStructs().getDynamicType(this));
    }
  }
}
