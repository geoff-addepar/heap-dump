package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.Inferior;
import com.addepar.heapdump.inspect.inferior.SelfInferior;
import com.addepar.heapdump.inspect.struct.PSOldGen;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;
import com.addepar.heapdump.inspect.struct.Universe;

import java.io.IOException;

public class SelfInspector {

  public static void main(String[] args) throws IOException {
    Inferior inferior = new SelfInferior();
    AddressSpace space = new AddressSpace(inferior);
    HotspotTypes types = new HotspotTypes(space);
    HotspotConstants constants = new HotspotConstants(space);
    HotspotStructs hotspotStructs = new HotspotStructs(space, types, constants);

    Universe universe = hotspotStructs.staticStruct(Universe.class);
    System.out.println("Is GC active? " + universe._collectedHeap()._is_gc_active());

    System.out.println("HeapWordSize=" + constants.getHeapWordSize());

    System.out.println("ArrayKlass's superclass is " + types.getType("ArrayKlass").getSuperclassName());

    System.out.println("The type of heap is " + hotspotStructs.getDynamicType(universe._collectedHeap()).getTypeName());

    ParallelScavengeHeap psheap = universe._collectedHeap().dynamicCast(ParallelScavengeHeap.class);
    if (psheap != null) {
      System.out.println("The bottom of eden space is " + Long.toHexString(psheap._young_gen()._eden_space()._bottom()));
      System.out.println("The top of eden space is " + Long.toHexString(psheap._young_gen()._eden_space()._top()));
      System.out.println("The end of eden space is " + Long.toHexString(psheap._young_gen()._eden_space()._end()));
      System.out.println("The type of old gen is " + hotspotStructs.getDynamicType(psheap._old_gen()));
    }

    System.out.println("Can heap be cast to PSOldGen? "
        + (universe._collectedHeap().dynamicCast(PSOldGen.class) != null ? "yes" : "no"));
    System.out.println("Is heap instance of PSOldGen? "
        + (universe._collectedHeap().isInstanceOf(PSOldGen.class) ? "yes" : "no"));

    System.out.println("Can heap be cast to ParallelScavengeHeap? "
        + (universe._collectedHeap().dynamicCast(ParallelScavengeHeap.class) != null ? "yes" : "no"));
    System.out.println("Is heap instance of ParallelScavengeHeap? "
        + (universe._collectedHeap().isInstanceOf(ParallelScavengeHeap.class) ? "yes" : "no"));

    System.out.println("The fields of ParallelScavengeHeap are " + hotspotStructs.getFields("ParallelScavengeHeap"));

    HotspotHeap heap = new HotspotHeap(new Hotspot(inferior));
    System.out.println("Live ranges are: " + heap.collectLiveRegions());
  }
}
