package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.Inferior;
import com.addepar.heapdump.inspect.inferior.SelfInferior;
import com.addepar.heapdump.inspect.struct.Universe;

public class SelfInspector {

  public static void main(String[] args) {
    Inferior inferior = new SelfInferior();
    AddressSpace space = new AddressSpace(inferior);
    HotspotTypes types = new HotspotTypes(space);
    HotspotStructs hotspotStructs = new HotspotStructs(space, types);

    Universe universe = hotspotStructs.staticStruct(Universe.class);
    System.out.println("Is GC active? " + universe._collectedHeap()._is_gc_active());

    HotspotConstants constants = new HotspotConstants(space);
    System.out.println("HeapWordSize=" + constants.getHeapWordSize());

    System.out.println("ArrayKlass's superclass is " + types.getType("ArrayKlass").getSuperclass());
  }
}
