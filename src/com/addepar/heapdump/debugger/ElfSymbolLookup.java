package com.addepar.heapdump.debugger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;

/**
 * Utility to help find non-dynamic symbols in an ELF file
 *
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class ElfSymbolLookup {
  private final FileChannel fileChannel;

  public ElfSymbolLookup(String filename) throws IOException {
    this.fileChannel = FileChannel.open(Paths.get(filename));
  }

  public void close() throws IOException {
    fileChannel.close();
  }
}
