package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;
import com.addepar.heapdump.inspect.Hotspot;

public interface oopDesc extends HotspotStruct {

  @FieldType("markOop")
  long _mark();

  @FieldType("Klass*")
  Klass _metadata__klass();

  @FieldType("narrowOop")
  int _metadata__compressed_klass();

  default Klass getKlass(Hotspot hotspot) {
    if (hotspot.useCompressedKlassPointers()) {
      long klassAddress = hotspot.decompressKlassPointer(_metadata__compressed_klass());
      return hotspot.getStructs().structAt(klassAddress, Klass.class);
    } else {
      return _metadata__klass();
    }
  }

  default long getObjectSize(Hotspot hotspot) {
    Klass klass = getKlass(hotspot);
    return klass.getObjectSize(this, hotspot);
  }
}
