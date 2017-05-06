package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.HotspotStructs;

/**
 * Hotspot types that are expected to have a vtable should extend from this interface. It adds support for dynamic
 * type checks and dynamic casts.
 */
public interface DynamicHotspotStruct extends HotspotStruct {

  default <T extends DynamicHotspotStruct> T dynamicCast(Class<T> subclass) {
    HotspotStructs.AsmClassLoader asmClassLoader = (HotspotStructs.AsmClassLoader) getClass().getClassLoader();
    return asmClassLoader.getHotspotStructs().dynamicCast(this, subclass);
  }

  default boolean isInstanceOf(Class<? extends DynamicHotspotStruct> subclass) {
    HotspotStructs.AsmClassLoader asmClassLoader = (HotspotStructs.AsmClassLoader) getClass().getClassLoader();
    return asmClassLoader.getHotspotStructs().isInstanceOf(this, subclass);
  }
}
