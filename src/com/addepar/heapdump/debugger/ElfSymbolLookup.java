package com.addepar.heapdump.debugger;

import java.nio.channels.FileChannel;

/**
 * Utility to help find non-dynamic symbols in an ELF file
 *
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class ElfSymbolLookup {
  private final FileChannel fileChannel;

  public ElfSymbolLookup(String filename) {
    this.fileChannel =
  }

  public void close() {
    fileChannel.close();
  }
}
