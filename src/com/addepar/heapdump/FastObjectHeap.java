package com.addepar.heapdump;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.debugger.UnmappedAddressException;
import sun.jvm.hotspot.gc_implementation.g1.G1CollectedHeap;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.PSOldGen;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.PSYoungGen;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.ParallelScavengeHeap;
import sun.jvm.hotspot.gc_interface.CollectedHeap;
import sun.jvm.hotspot.memory.GenCollectedHeap;
import sun.jvm.hotspot.memory.Generation;
import sun.jvm.hotspot.memory.MemRegion;
import sun.jvm.hotspot.memory.Space;
import sun.jvm.hotspot.memory.SpaceClosure;
import sun.jvm.hotspot.memory.SymbolTable;
import sun.jvm.hotspot.oops.FastInstance;
import sun.jvm.hotspot.oops.FastInstanceKlass;
import sun.jvm.hotspot.oops.FastInstanceMirrorKlass;
import sun.jvm.hotspot.oops.FastObjArray;
import sun.jvm.hotspot.oops.FastObjArrayKlass;
import sun.jvm.hotspot.oops.FastTypeArray;
import sun.jvm.hotspot.oops.IntField;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.MetadataField;
import sun.jvm.hotspot.oops.NarrowKlassField;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.oops.TypeArrayKlass;
import sun.jvm.hotspot.runtime.JavaThread;
import sun.jvm.hotspot.runtime.ThreadLocalAllocBuffer;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.types.basic.VtblAccess;
import sun.jvm.hotspot.utilities.AddressOps;
import sun.jvm.hotspot.utilities.Assert;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class FastObjectHeap extends ObjectHeap {

  private final IntField klassLayoutHelper;
  private final AddressField klassName;
  private final AddressField objArrayBottomKlass;

  private final MetadataField oopKlass;
  private final NarrowKlassField oopCompressedKlass;

  private final long oopKlassOffset;
  private final long oopArrayKlassOffset;

  private final Symbol javaLangClass;

  private final VtblAccess vtblAccess;
  private final Set<Address> klassVtbls;
  private final Address instanceMirrorKlassVtbl;

  private static final String[] KLASS_TYPES = {"InstanceKlass", "TypeArrayKlass", "ObjArrayKlass",
      "InstanceMirrorKlass", "InstanceClassLoaderKlass", "InstanceRefKlass"};

  public FastObjectHeap(TypeDataBase db, SymbolTable symTbl) {
    super(db);

    Type klassType = db.lookupType("Klass");
    klassLayoutHelper = new IntField(klassType.getJIntField("_layout_helper"), 0L);
    klassName = klassType.getAddressField("_name");

    Type objArrayKlassType = db.lookupType("ObjArrayKlass");
    objArrayBottomKlass = objArrayKlassType.getAddressField("_bottom_klass");

    Type oopType = db.lookupType("oopDesc");
    oopKlass = new MetadataField(oopType.getAddressField("_metadata._klass"), 0L);
    oopCompressedKlass = new NarrowKlassField(oopType.getAddressField("_metadata._compressed_klass"), 0L);

    Type jlc = db.lookupType("java_lang_Class");
    oopKlassOffset = jlc.getCIntegerField("_klass_offset").getValue();
    oopArrayKlassOffset = jlc.getCIntegerField("_array_klass_offset").getValue();

    javaLangClass = symTbl.probe("java/lang/Class");

    try {
      Field f = db.getClass().getSuperclass().getDeclaredField("vtblAccess");
      f.setAccessible(true);
      vtblAccess = (VtblAccess) f.get(db);
      klassVtbls = new HashSet<Address>();
      for (String type : KLASS_TYPES) {
        Address a = vtblAccess.getVtblForType(db.lookupType(type));
        if (a == null) {
          throw new IllegalStateException();
        }
        klassVtbls.add(a);
      }
      instanceMirrorKlassVtbl = vtblAccess.getVtblForType(db.lookupType("InstanceMirrorKlass"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // The address points to a Klass*, but is it an internal pointer inside of a java.lang.Class, or
  // an actual klass word in an object header?
  private boolean isInternalKlassPointer(Address address, Address bottom, long offset) {
    OopHandle classOop = address.addOffsetToAsOopHandle(-offset);
    if (VM.getVM().isCompressedKlassPointersEnabled()) {
      classOop = classOop.addOffsetToAsOopHandle(oopCompressedKlass.getOffset());
    } else {
      classOop = classOop.addOffsetToAsOopHandle(oopKlass.getOffset());
    }

    if (classOop.greaterThanOrEqual(bottom)) {
      Address classKlass;
      if (VM.getVM().isCompressedKlassPointersEnabled()) {
        classKlass = classOop.getCompKlassAddressAt(oopCompressedKlass.getOffset());
      } else {
        classKlass = classOop.getAddressAt(oopKlass.getOffset());
      }

      if (classKlass == null) {
        return false;
      }

      try {
        Address vtbl = classKlass.getAddressAt(0);
        return instanceMirrorKlassVtbl.equals(vtbl);
      } catch (UnmappedAddressException e) {
        return false;
      }
    }
    return false;
  }

  public Oop newOopIfPossible(OopHandle handle, Address bottom) {
    if (handle == null) {
      return null;
    }

    Address klassAddress;
    if (VM.getVM().isCompressedKlassPointersEnabled()) {
      klassAddress = handle.getCompKlassAddressAt(oopCompressedKlass.getOffset());
    } else {
      klassAddress = handle.getAddressAt(oopKlass.getOffset());
    }

    if (klassAddress == null) {
      return null;
    }

    // Validate that the address actually points at a Klass
    try {
      Address vtbl = klassAddress.getAddressAt(0);
      if (!klassVtbls.contains(vtbl)) {
        return null;
      }
    } catch (UnmappedAddressException e) {
      return null;
    }

    // Check if we hit the Class.klass or Class.array_klass fields
    if (isInternalKlassPointer(handle, bottom, oopKlassOffset) ||
        isInternalKlassPointer(handle, bottom, oopArrayKlassOffset)) {
      return null;
    }

    int layoutHelper = klassAddress.getJIntAt(klassLayoutHelper.getOffset());
    int tag = layoutHelper >> Klass.LH_ARRAY_TAG_SHIFT;

    if (tag == Klass.LH_ARRAY_TAG_OBJ_VALUE) {
      // object array
      Klass bottomKlass = getKlassAtAddress(objArrayBottomKlass.getValue(klassAddress));
      return new FastObjArray(handle, this, new FastObjArrayKlass(klassAddress, bottomKlass));
    } else if (tag == Klass.LH_ARRAY_TAG_TYPE_VALUE) {
      // primitive array
      return new FastTypeArray(handle, this, new TypeArrayKlass(klassAddress));
    } else {
      Symbol name = Symbol.create(klassAddress.getAddressAt(klassName.getOffset()));
      if (javaLangClass.equals(name)) {
        // instance of java.lang.Class, which has special handling for static members
        return new FastInstance(handle, this, new FastInstanceMirrorKlass(klassAddress));
      } else {
        // instance of anything else. we don't handle InstanceRefKlass or InstanceClassLoaderKlass
        // because they contain no extra logic
        return new FastInstance(handle, this, new FastInstanceKlass(klassAddress));
      }
    }
  }

  @Override
  public Oop newOop(OopHandle handle) {
    if (handle == null) {
      return null;
    }

    Address klassAddress;
    if (VM.getVM().isCompressedKlassPointersEnabled()) {
      klassAddress = handle.getCompKlassAddressAt(oopCompressedKlass.getOffset());
    } else {
      klassAddress = handle.getAddressAt(oopKlass.getOffset());
    }

    int layoutHelper = klassAddress.getJIntAt(klassLayoutHelper.getOffset());
    int tag = layoutHelper >> Klass.LH_ARRAY_TAG_SHIFT;

    if (tag == Klass.LH_ARRAY_TAG_OBJ_VALUE) {
      // object array
      Klass bottomKlass = getKlassAtAddress(objArrayBottomKlass.getValue(klassAddress));
      return new FastObjArray(handle, this, new FastObjArrayKlass(klassAddress, bottomKlass));
    } else if (tag == Klass.LH_ARRAY_TAG_TYPE_VALUE) {
      // primitive array
      return new FastTypeArray(handle, this, new TypeArrayKlass(klassAddress));
    } else {
      Symbol name = Symbol.create(klassAddress.getAddressAt(klassName.getOffset()));
      if (javaLangClass.equals(name)) {
        // instance of java.lang.Class, which has special handling for static members
        return new FastInstance(handle, this, new FastInstanceMirrorKlass(klassAddress));
      } else {
        // instance of anything else. we don't handle InstanceRefKlass or InstanceClassLoaderKlass
        // because they contain no extra logic
        return new FastInstance(handle, this, new FastInstanceKlass(klassAddress));
      }
    }
  }

  public Klass getKlassForClass(Oop instanceOfJavaLangClass) {
    Address address = instanceOfJavaLangClass.getHandle().getAddressAt(oopKlassOffset);
    if (address == null) {
      return null;
    }
    return getKlassAtAddress(address);
  }

  public Klass getKlassAtAddress(Address klassAddress) {
    int layoutHelper = klassAddress.getJIntAt(klassLayoutHelper.getOffset());
    int tag = layoutHelper >> Klass.LH_ARRAY_TAG_SHIFT;

    if (tag == Klass.LH_ARRAY_TAG_OBJ_VALUE) {
      // object array
      Klass bottomKlass = getKlassAtAddress(objArrayBottomKlass.getValue(klassAddress));
      return new FastObjArrayKlass(klassAddress, bottomKlass);
    } else if (tag == Klass.LH_ARRAY_TAG_TYPE_VALUE) {
      // primitive array
      return new TypeArrayKlass(klassAddress);
    } else {
      Symbol name = Symbol.create(klassAddress.getAddressAt(klassName.getOffset()));
      if (javaLangClass.equals(name)) {
        // java.lang.Class
        return new FastInstanceMirrorKlass(klassAddress);
      } else {
        // any other non-array class
        return new FastInstanceKlass(klassAddress);
      }
    }
  }

  public List collectLiveRegions() {
    ArrayList liveRegions = new ArrayList();
    LiveRegionsCollector lrc = new LiveRegionsCollector(liveRegions);
    CollectedHeap heap = VM.getVM().getUniverse().heap();
    if(heap instanceof GenCollectedHeap) {
      GenCollectedHeap i = (GenCollectedHeap)heap;

      for(int bottom = 0; bottom < i.nGens(); ++bottom) {
        Generation top = i.getGen(bottom);
        top.spaceIterate(lrc, true);
      }
    } else if(heap instanceof ParallelScavengeHeap) {
      ParallelScavengeHeap var7 = (ParallelScavengeHeap)heap;
      PSYoungGen var10 = var7.youngGen();
      this.addLiveRegions("eden", var10.edenSpace().getLiveRegions(), liveRegions);
      this.addLiveRegions("from", var10.fromSpace().getLiveRegions(), liveRegions);
      PSOldGen var13 = var7.oldGen();
      this.addLiveRegions("old ", var13.objectSpace().getLiveRegions(), liveRegions);
    } else if(heap instanceof G1CollectedHeap) {
      G1CollectedHeap var8 = (G1CollectedHeap)heap;
      var8.heapRegionIterate(lrc);
    } else if(Assert.ASSERTS_ENABLED) {
      Assert.that(false, "Expecting GenCollectedHeap, G1CollectedHeap, or ParallelScavengeHeap, but got " + heap.getClass().getName());
    }

    if(VM.getVM().getUseTLAB()) {
      for(JavaThread var9 = VM.getVM().getThreads().first(); var9 != null; var9 = var9.next()) {
        ThreadLocalAllocBuffer var12 = var9.tlab();
        if(var12.start() != null) {
          if(var12.top() != null && var12.end() != null) {
            liveRegions.add(var12.start());
            liveRegions.add(var12.start());
            liveRegions.add(var12.top());
            liveRegions.add(var12.hardEnd());
          } else {
            System.err.print("Warning: skipping invalid TLAB for thread ");
            var9.printThreadIDOn(System.err);
            System.err.println();
          }
        }
      }
    }

    this.sortLiveRegions(liveRegions);
    if(Assert.ASSERTS_ENABLED) {
      Assert.that(liveRegions.size() % 2 == 0, "Must have even number of region boundaries");
    }

    return liveRegions;
  }

  private void sortLiveRegions(List liveRegions) {
    Collections.sort(liveRegions, new Comparator() {
      public int compare(Object o1, Object o2) {
        Address a1 = (Address)o1;
        Address a2 = (Address)o2;
        return AddressOps.lt(a1, a2)?-1:(AddressOps.gt(a1, a2)?1:0);
      }
    });
  }

  private class LiveRegionsCollector implements SpaceClosure {
    private List liveRegions;

    LiveRegionsCollector(List l) {
      this.liveRegions = l;
    }

    public void doSpace(Space s) {
      addLiveRegions(s.toString(), s.getLiveRegions(), this.liveRegions);
    }
  }

  private void addLiveRegions(String name, List input, List output) {
    Iterator itr = input.iterator();

    while(itr.hasNext()) {
      MemRegion reg = (MemRegion)itr.next();
      Address top = reg.end();
      Address bottom = reg.start();
      if(Assert.ASSERTS_ENABLED) {
        Assert.that(top != null, "top address in a live region should not be null");
      }

      if(Assert.ASSERTS_ENABLED) {
        Assert.that(bottom != null, "bottom address in a live region should not be null");
      }

      output.add(top);
      output.add(bottom);
    }

  }
}
