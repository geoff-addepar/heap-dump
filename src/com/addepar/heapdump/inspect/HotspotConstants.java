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
  private final int layoutHelperArrayTagShift;
  private final int layoutHelperArrayTagTypeValue;
  private final int layoutHelperArrayTagObjValue;
  private final int layoutHelperInstanceSlowPathBit;
  private final int layoutHelperHeaderSizeShift;
  private final int layoutHelperHeaderSizeMask;
  private final int layoutHelperLog2ElementSizeShift;
  private final int layoutHelperLog2ElementSizeMask;

  public HotspotConstants(AddressSpace space) {
    long hotSpotVMIntConstants = space.getPointer(space.lookupSymbol("gHotSpotVMIntConstants"));
    long intEntryNameOffset = space.getLong(space.lookupSymbol("gHotSpotVMIntConstantEntryNameOffset"));
    long intEntryValueOffset = space.getLong(space.lookupSymbol("gHotSpotVMIntConstantEntryValueOffset"));
    long intStride = space.getLong(space.lookupSymbol("gHotSpotVMIntConstantEntryArrayStride"));

    long current = hotSpotVMIntConstants;
    while (true) {
      String name = space.getAsciiString(current + intEntryNameOffset);
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
      String name = space.getAsciiString(current + longEntryNameOffset);
      if (name == null) {
        break;
      }
      long value = space.getLong(current + longEntryValueOffset);
      longConstants.put(name, value);
      current += longStride;
    }

    heapWordSize = intConstants.get("HeapWordSize");
    layoutHelperArrayTagShift = intConstants.get("Klass::_lh_array_tag_shift");
    layoutHelperArrayTagTypeValue = intConstants.get("Klass::_lh_array_tag_type_value");
    layoutHelperArrayTagObjValue = intConstants.get("Klass::_lh_array_tag_obj_value");
    layoutHelperInstanceSlowPathBit = intConstants.get("Klass::_lh_instance_slow_path_bit");
    layoutHelperHeaderSizeShift = intConstants.get("Klass::_lh_header_size_shift");
    layoutHelperHeaderSizeMask = intConstants.get("Klass::_lh_header_size_mask");
    layoutHelperLog2ElementSizeShift = intConstants.get("Klass::_lh_log2_element_size_shift");
    layoutHelperLog2ElementSizeMask = intConstants.get("Klass::_lh_log2_element_size_mask");
  }

  public int getHeapWordSize() {
    return heapWordSize;
  }

  public int getLayoutHelperArrayTagShift() {
    return layoutHelperArrayTagShift;
  }

  public int getLayoutHelperArrayTagTypeValue() {
    return layoutHelperArrayTagTypeValue;
  }

  public int getLayoutHelperArrayTagObjValue() {
    return layoutHelperArrayTagObjValue;
  }

  public int getLayoutHelperInstanceSlowPathBit() {
    return layoutHelperInstanceSlowPathBit;
  }

  public int getLayoutHelperHeaderSizeShift() {
    return layoutHelperHeaderSizeShift;
  }

  public int getLayoutHelperHeaderSizeMask() {
    return layoutHelperHeaderSizeMask;
  }

  public int getLayoutHelperLog2ElementSizeShift() {
    return layoutHelperLog2ElementSizeShift;
  }

  public int getLayoutHelperLog2ElementSizeMask() {
    return layoutHelperLog2ElementSizeMask;
  }
}
