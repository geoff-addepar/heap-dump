package com.addepar.heapdump.debugger;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Callback;
import com.sun.jna.IntegerType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public interface CLibrary extends Library {

  CLibrary INSTANCE = (CLibrary) Native.loadLibrary("c", CLibrary.class);

  public class size_t extends IntegerType {
    private static final long serialVersionUID = -9047836483723685882L;

    public size_t() { this(0); }
    public size_t(long value) { super(Native.SIZE_T_SIZE, value, true); }
  }

  public class Elf64_Phdr extends Structure {
    public volatile int p_type;
    public volatile int p_flags;
    public volatile long p_offset;
    public volatile Pointer p_vaddr;
    public volatile Pointer p_paddr;
    public volatile long p_filesz;
    public volatile long p_memsz;
    public volatile long p_align;

    public static class ByReference extends Elf64_Phdr implements Structure.ByReference {
    }

    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList("p_type", "p_flags", "p_offset", "p_vaddr", "p_paddr", "p_filesz",
          "p_memsz", "p_align");
    }
  }

  class dl_phdr_info extends Structure {
    public volatile Pointer dlpi_addr;
    public volatile String dlpi_name;
    public volatile Pointer dlpi_phdr;
    public volatile short dlpi_phnum;

    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList("dlpi_addr", "dlpi_name", "dlpi_phdr", "dlpi_phnum");
    }
  }

  @FunctionalInterface
  interface DlIteratePhdrCallback extends Callback {
    int invoke(dl_phdr_info info, size_t size, Pointer data);
  }

  int dl_iterate_phdr(DlIteratePhdrCallback callback, Pointer data);
}
