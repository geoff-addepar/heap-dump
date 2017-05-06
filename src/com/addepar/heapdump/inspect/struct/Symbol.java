package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;
import com.addepar.heapdump.inspect.Hotspot;

import java.nio.charset.StandardCharsets;

public interface Symbol extends HotspotStruct {

  @FieldType("unsigned short")
  short _length();

  default String getStringValue(Hotspot hotspot) {
    int length = Short.toUnsignedInt(_length());
    long base = getAddress() + hotspot.getStructs().offsetOf("Symbol", "_body");
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = hotspot.getAddressSpace().getByte(base + i);
    }
    return new String(result, StandardCharsets.UTF_8);
  }
}
