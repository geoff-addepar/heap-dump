package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.struct.Abstract_VM_Version;
import com.addepar.heapdump.inspect.struct.CollectedHeap;
import com.addepar.heapdump.inspect.struct.JavaThread;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;
import com.addepar.heapdump.inspect.struct.ThreadLocalAllocBuffer;
import com.addepar.heapdump.inspect.struct.Threads;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

/**
 * Logic to access the hotspot VM's heap
 */
public class HotspotHeap {

  private final Hotspot hotspot;

  public HotspotHeap(Hotspot hotspot) {
    this.hotspot = hotspot;
  }

  public RangeSet<Long> collectLiveRegions() {
    HotspotStructs structs = hotspot.getStructs();
    CollectedHeap heap = hotspot.getUniverse()._collectedHeap();

    RangeSet<Long> ranges = TreeRangeSet.create();

    if (structs.isInstanceOf(heap, ParallelScavengeHeap.class)) {
      ParallelScavengeHeap parallelScavengeHeap = structs.dynamicCast(heap, ParallelScavengeHeap.class);
      ranges.add(parallelScavengeHeap._young_gen()._eden_space().getLiveRange());
      ranges.add(parallelScavengeHeap._young_gen()._from_space().getLiveRange());
      ranges.add(parallelScavengeHeap._old_gen()._object_space().getLiveRange());
    } else {
      throw new UnsupportedOperationException("We don't know how to handle heaps of type "
          + structs.getDynamicType(heap));
    }

    if (hotspot.useTLAB()) {

      // There is a reserved area at the end of the TLAB that isn't explicitly inside the _end()
      long reserveForAllocationPrefetch =
          structs.staticStruct(Abstract_VM_Version.class)._reserve_for_allocation_prefetch()
              * hotspot.getConstants().getHeapWordSize();
      long minFillerArraySize = hotspot.alignUp(hotspot.arrayLengthOffset() + 4,
          hotspot.getConstants().getHeapWordSize());
      long reserve = hotspot.alignUp(Math.max(minFillerArraySize, reserveForAllocationPrefetch),
          hotspot.getMinObjAlignmentInBytes());

      for (JavaThread thread = structs.staticStruct(Threads.class)._thread_list();
           thread.getAddress() != 0;
           thread = thread._next()) {

        ThreadLocalAllocBuffer tlab = thread._tlab();
        if (tlab._start() != 0 && tlab._top() != 0 && tlab._end() != 0) {
          long hardEnd = tlab._end() + reserve;
          ranges.remove(Range.closedOpen(tlab._top(), hardEnd));
        }
      }
    }

    return ranges;
  }

  /**
   * The number of bytes between the start of the object header and the start of array element data
   */
  private long arrayHeaderSize() {
    return hotspot.alignUp(hotspot.arrayLengthOffset() + 4, hotspot.getConstants().getHeapWordSize());
  }
}
