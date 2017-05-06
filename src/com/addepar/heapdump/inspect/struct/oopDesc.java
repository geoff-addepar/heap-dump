package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;
import com.addepar.heapdump.inspect.Hotspot;

public interface oopDesc extends HotspotStruct {

  @FieldType("markOop")
  long _mark();

  @FieldType("Klass*")
  long _metadata__klass();

  @FieldType("narrowOop")
  int _metadata__compressed_klass();

  /**
   * Get the Klass of this heap object. If the <code>klass</code> is non-null, re-use it. Note that dynamic type checks
   * are NOT performed, so passing an ArrayKlass or other subclass of Klass may return an invalid object.
   */
  default Klass getKlass(Hotspot hotspot, Klass klass) {
    long klassAddress;
    if (hotspot.useCompressedKlassPointers()) {
      klassAddress = hotspot.decompressKlassPointer(_metadata__compressed_klass());
    } else {
      klassAddress = _metadata__klass();
    }

    if (klass != null) {
      klass.setAddress(klassAddress);
      return klass;
    } else {
      return hotspot.getStructs().structAt(klassAddress, Klass.class);
    }
  }

  default long getObjectSize(Hotspot hotspot, Klass klass) {
    return klass.getObjectSize(this, hotspot);
  }
}
