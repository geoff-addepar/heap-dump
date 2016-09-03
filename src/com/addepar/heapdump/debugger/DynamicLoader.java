package com.addepar.heapdump.debugger;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA bindings for dynamic loading via libdl (dlsym, dlopen, etc.)
 *
 * @author Geoff Lywood (geoff@addepar.com)
 */
public interface DynamicLoader extends Library {

  DynamicLoader INSTANCE = (DynamicLoader) Native.loadLibrary("dl", DynamicLoader.class);

  Pointer dlopen(String filename, int flag);

  // flag values:
  int RTLD_LAZY = 1;
  int RTLD_NOW = 2;
  int RTLD_NOLOAD = 4;
  int RTLD_DEEPBIND = 8;
  int RTLD_GLOBAL = 0x100;
  int RTLD_LOCAL = 0;
  int RTLD_NODELETE = 0x1000;

  String dlerror();

  Pointer dlsym(Pointer handle, String symbol);

  int dlclose(Pointer handle);
}
