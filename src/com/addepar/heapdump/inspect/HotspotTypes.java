package com.addepar.heapdump.inspect;

import java.util.HashMap;
import java.util.Map;

/**
 * Access to the type hierarchy provided by vmStructs.cpp
 */
public class HotspotTypes {

  private final AddressSpace space;
  private final Map<String, TypeDescriptor> typeMap = new HashMap<>();

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
      String typeName = space.getAsciiString(space.getPointer(current + typeNameOffset));
      if (typeName == null) {
        break;
      }

      String superclassName = space.getAsciiString(space.getPointer(current + superclassNameOffset));
      boolean isOopType = space.getInt(current + isOopTypeOffset) != 0;
      boolean isIntegerType = space.getInt(current + isIntegerTypeOffset) != 0;
      boolean isUnsigned = space.getInt(current + isUnsignedOffset) != 0;
      long size = space.getLong(current + sizeOffset);

      TypeDescriptor descriptor =
          new TypeDescriptor(typeName, superclassName, isOopType, isIntegerType, isUnsigned, size);
      typeMap.put(typeName, descriptor);
      current += stride;
    }
  }

  public TypeDescriptor getType(String typeName) {
    return typeMap.get(typeName);
  }

  public static class TypeDescriptor {
    private final String typeName;
    private final String superclass;
    private final boolean isOopType;
    private final boolean isIntegerType;
    private final boolean isUnsigned;
    private final long size;

    public TypeDescriptor(String typeName, String superclass, boolean isOopType, boolean isIntegerType,
                          boolean isUnsigned, long size) {
      this.typeName = typeName;
      this.superclass = superclass;
      this.isOopType = isOopType;
      this.isIntegerType = isIntegerType;
      this.isUnsigned = isUnsigned;
      this.size = size;
    }

    public String getTypeName() {
      return typeName;
    }

    public String getSuperclass() {
      return superclass;
    }

    public long getSize() {
      return size;
    }
  }
}
