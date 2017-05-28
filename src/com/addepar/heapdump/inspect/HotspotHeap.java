package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.struct.Abstract_VM_Version;
import com.addepar.heapdump.inspect.struct.CollectedHeap;
import com.addepar.heapdump.inspect.struct.CompactibleFreeListSpace;
import com.addepar.heapdump.inspect.struct.ConcurrentMarkSweepGeneration;
import com.addepar.heapdump.inspect.struct.DefNewGeneration;
import com.addepar.heapdump.inspect.struct.FreeChunk;
import com.addepar.heapdump.inspect.struct.G1CollectedHeap;
import com.addepar.heapdump.inspect.struct.G1HeapRegionTable;
import com.addepar.heapdump.inspect.struct.GenCollectedHeap;
import com.addepar.heapdump.inspect.struct.Generation;
import com.addepar.heapdump.inspect.struct.HeapRegion;
import com.addepar.heapdump.inspect.struct.JavaThread;
import com.addepar.heapdump.inspect.struct.Klass;
import com.addepar.heapdump.inspect.struct.OneContigSpaceCardGeneration;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;
import com.addepar.heapdump.inspect.struct.ThreadLocalAllocBuffer;
import com.addepar.heapdump.inspect.struct.Threads;
import com.addepar.heapdump.inspect.struct.oopDesc;
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

    if (heap.isInstanceOf(ParallelScavengeHeap.class)) {
      ParallelScavengeHeap parallelScavengeHeap = heap.dynamicCast(ParallelScavengeHeap.class);
      ranges.add(parallelScavengeHeap._young_gen()._eden_space().getLiveRange());
      ranges.add(parallelScavengeHeap._young_gen()._from_space().getLiveRange());
      ranges.add(parallelScavengeHeap._old_gen()._object_space().getLiveRange());
    } else if (heap.isInstanceOf(GenCollectedHeap.class)) {
      GenCollectedHeap genCollectedHeap = heap.dynamicCast(GenCollectedHeap.class);
      for (int i = 0; i < genCollectedHeap._n_gens(); i++) {
        Generation generation = genCollectedHeap.generation(i, hotspot);
        if (generation.isInstanceOf(DefNewGeneration.class)) {
          DefNewGeneration newGeneration = generation.dynamicCast(DefNewGeneration.class);
          ranges.add(newGeneration._eden_space().getLiveRange());
          ranges.add(newGeneration._from_space().getLiveRange());
        } else if (generation.isInstanceOf(OneContigSpaceCardGeneration.class)) {
          OneContigSpaceCardGeneration contigGen = generation.dynamicCast(OneContigSpaceCardGeneration.class);
          ranges.add(contigGen._the_space().getLiveRange());
        } else if (generation.isInstanceOf(ConcurrentMarkSweepGeneration.class)) {
          addCmsRanges(ranges, generation.dynamicCast(ConcurrentMarkSweepGeneration.class));
        } else {
          throw new UnsupportedOperationException("GenCollectedHeap had unhandled generation type "
              + structs.getDynamicType(generation));
        }
      }
    } else if (heap.isInstanceOf(G1CollectedHeap.class)) {
      G1CollectedHeap g1Heap = heap.dynamicCast(G1CollectedHeap.class);
      addG1Ranges(ranges, g1Heap);
    } else {
      throw new UnsupportedOperationException("We don't know how to handle heaps of type "
          + structs.getDynamicType(heap));
    }

    if (hotspot.useTLAB()) {

      // There is a reserved area at the end of the TLAB that isn't explicitly inside the _end()
      long reserveForAllocationPrefetch =
          structs.staticStruct(Abstract_VM_Version.class)._reserve_for_allocation_prefetch()
              * hotspot.getConstants().getHeapWordSize();
      long minFillerArraySize = arrayHeaderSize();
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

  private void addCmsRanges(RangeSet<Long> ranges, ConcurrentMarkSweepGeneration generation) {
    CompactibleFreeListSpace space = generation._cmsSpace();

    FreeChunk freeChunk = hotspot.getStructs().staticStruct(FreeChunk.class);
    oopDesc oop = hotspot.getStructs().staticStruct(oopDesc.class);
    Klass klass = hotspot.getStructs().staticStruct(Klass.class);

    long minChunkSize = hotspot.alignUp(hotspot.getTypes().getType("FreeChunk").getSize(),
        hotspot.getMinObjAlignmentInBytes());

    ranges.add(Range.closedOpen(space._bottom(), space._end()));
    for (long cur = space._bottom(); cur < space._end();) {
      freeChunk.setAddress(cur);
      if (freeChunk.isFreeChunk(hotspot)) {
        ranges.remove(Range.closedOpen(cur, cur + freeChunk.size(hotspot)));
        cur += freeChunk.size(hotspot);
      } else {
        oop.setAddress(cur);
        oop.getKlass(hotspot, klass);
        cur += Math.max(oop.getObjectSize(hotspot, klass), minChunkSize);
      }
    }
  }

  private void addG1Ranges(RangeSet<Long> ranges, G1CollectedHeap heap) {
    G1HeapRegionTable regionTable = heap._hrm()._regions();
    long arrayAddress = regionTable._base();
    for (int i = 0; i < regionTable._length(); i++) {
      long region = hotspot.getAddressSpace().getPointer(arrayAddress + i * hotspot.getAddressSpace().getPointerSize());
      if (region != 0) {
        ranges.add(hotspot.getStructs().structAt(region, HeapRegion.class).getLiveRange());
      }
    }
  }

  /**
   * The number of bytes between the start of the object header and the start of array element data
   */
  private long arrayHeaderSize() {
    return hotspot.alignUp(hotspot.arrayLengthOffset() + 4, hotspot.getConstants().getHeapWordSize());
  }
}
