package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.AddressNotMappedException;
import com.addepar.heapdump.inspect.struct.CollectedHeap;
import com.addepar.heapdump.inspect.struct.Klass;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;
import com.addepar.heapdump.inspect.struct.oopDesc;

import java.util.ArrayList;
import java.util.List;

/**
 * Logic to access the hotspot VM's heap
 */
public class HotspotHeap {

  private final Hotspot hotspot;

  public HotspotHeap(Hotspot hotspot) {
    this.hotspot = hotspot;
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

    // TODO: TLAB (Thread-local allocation buffers)

    return ranges;
  }

  public boolean isLikelyObject(long address, long bottom) {
    oopDesc obj = hotspot.getStructs().structAt(address, oopDesc.class);
    Klass klass = obj.getKlass(hotspot);

    try {
      // Perform a dynamic type check (up until now there isn't a guarantee that the pointer is valid)
      if (klass.getAddress() == 0
          || !hotspot.getAddressSpace().isMapped(klass.getAddress(), hotspot.getAddressSpace().getPointerSize())
          || !klass.isInstanceOf(Klass.class)) {
        return false;
      }
    } catch (AddressNotMappedException e) {
      return false;
    }

    // TODO: more validation
    return true;
  }
//
//  public Oop newOopIfPossible(OopHandle handle, Address bottom) {
//    if (handle == null) {
//      return null;
//    }
//
//    Address klassAddress;
//    if (VM.getVM().isCompressedKlassPointersEnabled()) {
//      klassAddress = handle.getCompKlassAddressAt(oopCompressedKlass.getOffset());
//    } else {
//      klassAddress = handle.getAddressAt(oopKlass.getOffset());
//    }
//
//    if (klassAddress == null) {
//      return null;
//    }
//
//    // Validate that the address actually points at a Klass
//    try {
//      Address vtbl = klassAddress.getAddressAt(0);
//      if (!klassVtbls.contains(vtbl)) {
//        return null;
//      }
//    } catch (UnmappedAddressException e) {
//      return null;
//    }
//
//    // Check if we hit the Class.klass or Class.array_klass fields
//    if (isInternalKlassPointer(handle, bottom, oopKlassOffset) ||
//        isInternalKlassPointer(handle, bottom, oopArrayKlassOffset)) {
//      return null;
//    }
//
//    int layoutHelper = klassAddress.getJIntAt(klassLayoutHelper.getOffset());
//    int tag = layoutHelper >> Klass.LH_ARRAY_TAG_SHIFT;
//
//    if (tag == Klass.LH_ARRAY_TAG_OBJ_VALUE) {
//      // object array
//      Klass bottomKlass = getKlassAtAddress(objArrayBottomKlass.getValue(klassAddress));
//      return new FastObjArray(handle, this, new FastObjArrayKlass(klassAddress, bottomKlass));
//    } else if (tag == Klass.LH_ARRAY_TAG_TYPE_VALUE) {
//      // primitive array
//      return new FastTypeArray(handle, this, new TypeArrayKlass(klassAddress));
//    } else {
//      Symbol name = Symbol.create(klassAddress.getAddressAt(klassName.getOffset()));
//      if (javaLangClass.equals(name)) {
//        // instance of java.lang.Class, which has special handling for static members
//        return new FastInstance(handle, this, new FastInstanceMirrorKlass(klassAddress));
//      } else {
//        // instance of anything else. we don't handle InstanceRefKlass or InstanceClassLoaderKlass
//        // because they contain no extra logic
//        return new FastInstance(handle, this, new FastInstanceKlass(klassAddress));
//      }
//    }
//  }
}
