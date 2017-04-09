package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.struct.CollectedHeap;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;

import java.util.ArrayList;
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

  public List<AddressRange> collectLiveRegions() {
    HotspotStructs structs = hotspot.getStructs();
    CollectedHeap heap = hotspot.getUniverse()._collectedHeap();

    List<AddressRange> ranges = new ArrayList<>();

    if (structs.isInstanceOf(heap, ParallelScavengeHeap.class)) {
      ParallelScavengeHeap parallelScavengeHeap = structs.dynamicCast(heap, ParallelScavengeHeap.class);
      ranges.add(parallelScavengeHeap._young_gen()._eden_space().getLiveRange());
      ranges.add(parallelScavengeHeap._young_gen()._from_space().getLiveRange());
      ranges.add(parallelScavengeHeap._old_gen()._object_space().getLiveRange());
    } else {
      throw new UnsupportedOperationException("We don't know how to handle heaps of type "
          + structs.getDynamicType(heap));
    }

    return ranges;
  }
}
