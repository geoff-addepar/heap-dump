package com.addepar.heapdump;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
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
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class FastObjectHeap extends ObjectHeap {

  private final IntField klassLayoutHelper;
  private final AddressField klassName;
  private final AddressField objArrayBottomKlass;

  private final MetadataField oopKlass;
  private final NarrowKlassField oopCompressedKlass;

  private final Symbol javaLangClass;


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

    javaLangClass = symTbl.probe("java/lang/Class");
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
}
