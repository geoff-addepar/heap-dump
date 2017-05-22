package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.NoSuchSymbolException;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Access to the type hierarchy provided by vmStructs.cpp
 */
public class HotspotTypes {

  private final AddressSpace space;
  private final Map<String, TypeDescriptor> typeMap = new HashMap<>();
  private final Long2ObjectOpenHashMap<TypeDescriptor> vtableMap = new Long2ObjectOpenHashMap<>();

  public HotspotTypes(AddressSpace space) {
    this.space = space;

    generateTypeMap();
  }

  private void generateTypeMap() {
    long vmTypes = space.getPointer(space.lookupSymbol("gHotSpotVMTypes"));
    long typeNameOffset = space.getLong(space.lookupSymbol("gHotSpotVMTypeEntryTypeNameOffset"));
    long superclassNameOffset = space.getLong(space.lookupSymbol("gHotSpotVMTypeEntrySuperclassNameOffset"));
    long isOopTypeOffset = space.getLong(space.lookupSymbol("gHotSpotVMTypeEntryIsOopTypeOffset"));
    long isIntegerTypeOffset = space.getLong(space.lookupSymbol("gHotSpotVMTypeEntryIsIntegerTypeOffset"));
    long isUnsignedOffset = space.getLong(space.lookupSymbol("gHotSpotVMTypeEntryIsUnsignedOffset"));
    long sizeOffset = space.getLong(space.lookupSymbol("gHotSpotVMTypeEntrySizeOffset"));
    long stride = space.getLong(space.lookupSymbol("gHotSpotVMTypeEntryArrayStride"));

    long current = vmTypes;
    while (true) {
      String typeName = space.getAsciiString(current + typeNameOffset);
      if (typeName == null) {
        break;
      }

      String superclassName = space.getAsciiString(current + superclassNameOffset);
      boolean isOopType = space.getInt(current + isOopTypeOffset) != 0;
      boolean isIntegerType = space.getInt(current + isIntegerTypeOffset) != 0;
      boolean isUnsigned = space.getInt(current + isUnsignedOffset) != 0;
      long size = space.getLong(current + sizeOffset);

      boolean isDynamic;
      long vtableAddress = 0;
      try {
        vtableAddress = space.lookupVtable(typeName);
        isDynamic = true;
      } catch (NoSuchSymbolException e) {
        // ignore; there are a lot of types without vtables
        isDynamic = false;
      }

      TypeDescriptor descriptor =
          new TypeDescriptor(typeName, superclassName, isOopType, isIntegerType, isUnsigned, size, isDynamic);
      typeMap.put(typeName, descriptor);
      if (isDynamic) {
        vtableMap.put(vtableAddress, descriptor);
      }
      current += stride;
    }

    for (TypeDescriptor type : typeMap.values()) {
      type.superclass = getType(type.superclassName);
    }
  }

  public TypeDescriptor getType(String typeName) {
    return typeMap.get(typeName);
  }

  public TypeDescriptor getDynamicType(long address) {
    long vtable = space.getPointer(address);
    return vtableMap.get(vtable);
  }

  public static class TypeDescriptor {
    private final String typeName;
    private final String superclassName;
    private final boolean isOopType;
    private final boolean isIntegerType;
    private final boolean isUnsigned;
    private final long size;

    // has a vtable?
    private final boolean isDynamic;

    private TypeDescriptor superclass;

    public TypeDescriptor(String typeName, String superclassName, boolean isOopType, boolean isIntegerType,
                          boolean isUnsigned, long size, boolean isDynamic) {
      this.typeName = typeName;
      this.superclassName = superclassName;
      this.isOopType = isOopType;
      this.isIntegerType = isIntegerType;
      this.isUnsigned = isUnsigned;
      this.size = size;
      this.isDynamic = isDynamic;
    }

    public String getTypeName() {
      return typeName;
    }

    public String getSuperclassName() {
      return superclassName;
    }

    public long getSize() {
      return size;
    }

    public boolean isDynamic() {
      return isDynamic;
    }

    public boolean isSubclassOf(TypeDescriptor other) {
      return this == other || (superclass != null && superclass.isSubclassOf(other));
    }

    @Override
    public String toString() {
      return typeName;
    }
  }
}
