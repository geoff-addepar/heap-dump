package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.AddressNotMappedException;
import com.addepar.heapdump.inspect.struct.Klass;
import com.addepar.heapdump.inspect.struct.oopDesc;

/**
 * A helper that can be used to find the nearest object header to a given heap location.
 *
 * This class is not thread safe.
 */
public class OopFinder {
  private final Hotspot hotspot;
  private final oopDesc oop;
  private final Klass klass;
  private final long heapWordSize;

  public OopFinder(Hotspot hotspot) {
    this.hotspot = hotspot;
    this.oop = hotspot.getStructs().staticStruct(oopDesc.class);
    this.klass = hotspot.getStructs().staticStruct(Klass.class);
    this.heapWordSize = hotspot.getConstants().getHeapWordSize();
  }

  /**
   * Walk backwards to find the start of the object at probeAddress, but don't walk past the bottom
   * of the live region.
   */
  public boolean probeForObject(long probeAddress, long bottom) {
    long cur = probeAddress & ~(heapWordSize - 1);
    while (Long.compareUnsigned(cur, bottom) >= 0) {
      oop.setAddress(cur);
      oop.getKlass(hotspot, klass);
      if (isLikelyObject()) {
        if (Long.compareUnsigned(cur + oop.getObjectSize(hotspot, klass), probeAddress) > 0) {
          return true; // original address was within the nearest object
        } else {
          // declare this a "miss" even though we might not have walked far enough, and we just
          // stumbled on some spurious data that looked like an Oop header, but the size is busted
          return false;
        }
      }
      cur -= heapWordSize;
    }
    return false; // not found
  }

  /*
   * Get the object that was found in the last probe (if successful). The returned <code>oopDesc</code> may be
   * reused/modified in the next call to <code>probeForObject</code>, so the caller should not retain references to it.
   */
  public oopDesc getProbedObject() {
    return oop;
  }

  /*
   * Get the Klass that was found in the last probe (if successful). The returned <code>Klass</code> may be
   * reused/modified in the next call to <code>probeForObject</code>, so the caller should not retain references to it.
   */
  public Klass getProbedKlass() {
    return klass;
  }

  /**
   * Check if the current value of <code>oop</code> looks like a probable object
   */
  private boolean isLikelyObject() {
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
