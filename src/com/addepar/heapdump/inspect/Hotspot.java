package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.Inferior;
import com.addepar.heapdump.inspect.struct.Universe;

/**
 * Top-level representation of a Hotspot VM
 */
public class Hotspot {

  private final HotspotConstants constants;

  private final HotspotStructs structs;

  private final HotspotTypes types;

  private final Universe universe;

  public Hotspot(Inferior inferior) {
    AddressSpace addressSpace = new AddressSpace(inferior);
    constants = new HotspotConstants(addressSpace);
    types = new HotspotTypes(addressSpace);
    structs = new HotspotStructs(addressSpace, types);
    universe = structs.staticStruct(Universe.class);
  }

  public HotspotHeap getHeap() {
    return new HotspotHeap(this);
  }
}
