package com.addepar.heapdump.inspect;

import com.addepar.heapdump.debugger.ElfSymbolLookup;
import com.addepar.heapdump.inspect.struct.Universe;

public class SelfInspector {

  public static void main(String[] args) {
    ElfSymbolLookup symbolLookup = new ElfSymbolLookup();
    Inferior inferior = new SelfInferior();
    AddressSpace space = new AddressSpace(inferior);
    HotspotStructs hotspotStructs = new HotspotStructs(space, symbolLookup);

    Universe universe = hotspotStructs.staticStruct(Universe.class);
    System.out.println("Is GC active? " + universe._collectedHeap()._is_gc_active());
  }
}
