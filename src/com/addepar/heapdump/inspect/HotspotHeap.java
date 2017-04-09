package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.struct.CollectedHeap;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;

import java.util.List;
import java.util.function.Consumer;

/**
 * Logic to access the hotspot VM's heap
 */
public class HotspotHeap {

  private final Hotspot hotspot;

  public HotspotHeap(Hotspot hotspot) {
    this.hotspot = hotspot;
  }

  public void walkHeap(Consumer<HeapObject> consumer) {

  }

  private List<AddressRange> collectLiveRegions() {
    HotspotStructs structs = hotspot.getStructs();
    CollectedHeap heap = hotspot.getUniverse()._collectedHeap();

    if (structs.isInstanceOf(heap, ParallelScavengeHeap.class)) {
      ParallelScavengeHeap parallelScavengeHeap = structs.dynamicCast(heap, ParallelScavengeHeap.class);
    } else {
      throw new UnsupportedOperationException("We don't know how to handle heaps of type "
          + structs.getDynamicType(heap));
    }

    return null;
  }
}
