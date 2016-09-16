package com.addepar.heapdump.debugger;

import com.addepar.heapdump.debugger.CLibrary.dl_phdr_info;
import com.addepar.heapdump.debugger.CLibrary.size_t;

import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Pointer;

/**
 * Utility to help find non-dynamic symbols in the currently running process.
 *
 * @author Geoff Lywood (geoff@addepar.com)
 */
class ElfSymbolLookup {
  private Map<String, Long> symbols = new HashMap<>();

  public ElfSymbolLookup() {
    CLibrary.INSTANCE.dl_iterate_phdr(this::doProgramHeader, null);
  }

  private int doProgramHeader(dl_phdr_info info, size_t size, Pointer data) {
    if (info.dlpi_name.isEmpty()) {
      return 0;
    }

    try (ElfFile file = new ElfFile(Paths.get(info.dlpi_name))) {
      for (Map.Entry<String, Long> entry : file.getSymbolValues().entrySet()) {
        if (entry.getValue() != 0) {
          symbols.put(entry.getKey(), entry.getValue() + Pointer.nativeValue(info.dlpi_addr));
        }
      }
      return 0;
    } catch (NoSuchFileException e) {
      // skip things that aren't there anymore, e.g. JNA
      return 0;
    } catch (Throwable e) {
      e.printStackTrace();
      return 1;
    }
  }

  public Long lookup(String symbolName) {
    return symbols.get(symbolName);
  }
}
