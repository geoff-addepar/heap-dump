package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.Hotspot;

public interface arrayOopDesc extends oopDesc {

  static int length(oopDesc arrayOopDesc, Hotspot hotspot) {
    if (arrayOopDesc.getKlass(hotspot)._layout_helper() >= 0) {
      throw new IllegalStateException("Tried to get the array length of a non-array object");
    }
    return hotspot.getAddressSpace().getInt(arrayOopDesc.getAddress() + hotspot.arrayLengthOffset());
  }
}
