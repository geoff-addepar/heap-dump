package com.addepar.heapdump.inspect;

import java.util.HashMap;
import java.util.Map;

/**
 * Access to the constants declared in vmStructs.cpp
 */
public class HotspotConstants {
  private final Map<String, Integer> intConstants = new HashMap<>();
  private final Map<String, Long> longConstants = new HashMap<>();

  private final int heapWordSize;

  public HotspotConstants(AddressSpace space) {
    long hotSpotVMIntConstants = space.getPointer(space.lookupSymbol("gHotSpotVMIntConstants"));
    long intEntryNameOffset = space.getLong(space.lookupSymbol("gHotSpotVMIntConstantEntryNameOffset"));
    long intEntryValueOffset = space.getLong(space.lookupSymbol("gHotSpotVMIntConstantEntryValueOffset"));
    long intStride = space.getLong(space.lookupSymbol("gHotSpotVMIntConstantEntryArrayStride"));

    long current = hotSpotVMIntConstants;
    while (true) {
      String name = space.getAsciiString(space.getPointer(current + intEntryNameOffset));
      if (name == null) {
        break;
      }
      int value = space.getInt(current + intEntryValueOffset);
      intConstants.put(name, value);
      current += intStride;
    }

    long hotSpotVMLongConstants = space.getPointer(space.lookupSymbol("gHotSpotVMLongConstants"));
    long longEntryNameOffset = space.getLong(space.lookupSymbol("gHotSpotVMLongConstantEntryNameOffset"));
    long longEntryValueOffset = space.getLong(space.lookupSymbol("gHotSpotVMLongConstantEntryValueOffset"));
    long longStride = space.getLong(space.lookupSymbol("gHotSpotVMLongConstantEntryArrayStride"));

    current = hotSpotVMLongConstants;
    while (true) {
      String name = space.getAsciiString(space.getPointer(current + longEntryNameOffset));
      if (name == null) {
        break;
      }
      long value = space.getLong(current + longEntryValueOffset);
      longConstants.put(name, value);
      current += longStride;
    }

    heapWordSize = intConstants.get("HeapWordSize");
  }

  public int getHeapWordSize() {
    return heapWordSize;
  }
}
