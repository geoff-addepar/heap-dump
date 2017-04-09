package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.Inferior;
import com.addepar.heapdump.inspect.inferior.SelfInferior;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;
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

    System.out.println("ArrayKlass's superclass is " + types.getType("ArrayKlass").getSuperclassName());

    System.out.println("The type of heap is " + hotspotStructs.getDynamicType(universe._collectedHeap()).getTypeName());

    ParallelScavengeHeap psheap = hotspotStructs.dynamicCast(universe._collectedHeap(), ParallelScavengeHeap.class);
    if (psheap != null) {
      System.out.println("The top of eden space is " + Long.toHexString(psheap._young_gen()._eden_space()._top()));
    }

    System.out.println("The fields of ParallelScavengeHeap are " + hotspotStructs.getFields("ParallelScavengeHeap"));
  }
}
