package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;
import com.addepar.heapdump.inspect.Hotspot;

public interface GenCollectedHeap extends CollectedHeap {

  @FieldType("int")
  int _n_gens();

  default Generation generation(int index, Hotspot hotspot) {
    long genAddress = getAddress() + hotspot.getStructs().offsetOf("GenCollectedHeap", "_gens");
    long gen = hotspot.getAddressSpace().getPointer(genAddress + index * hotspot.getAddressSpace().getPointerSize());
    return hotspot.getStructs().structAt(gen, Generation.class);
  }
}
