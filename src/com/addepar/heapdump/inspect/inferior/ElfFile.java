package com.addepar.heapdump.inspect.inferior;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class ElfFile implements AutoCloseable {

  private FileChannel fileChannel;
  private boolean is64Bit;
  private ByteOrder byteOrder;
  private Map<String, Long> symbolValues;

  public ElfFile(Path path) throws IOException {
    fileChannel = FileChannel.open(path);
    symbolValues = new HashMap<>();
    parse();
  }

  public Map<String, Long> getSymbolValues() {
    return symbolValues;
  }

  @Override
  public void close() throws IOException {
    fileChannel.close();
  }

  private void parse() throws IOException {
    ByteBuffer buf = ByteBuffer.allocate(MIN_SIZE_OF_ELF_HEADER);
    int bytes = fileChannel.read(buf);
    if (bytes < MIN_SIZE_OF_ELF_HEADER) {
      throw new IOException("File too small for ELF header");
    }
    buf.flip();

    if (buf.get() != ELFMAG0 || buf.get() != ELFMAG1 || buf.get() != ELFMAG2 ||
        buf.get() != ELFMAG3) {
      throw new IOException("Not an ELF file");
    }

    int classByte = buf.get();
    if (classByte == ELFCLASS64) {
      is64Bit = true;
    } else if (classByte != ELFCLASS32) {
      throw new IOException("Unrecognized class byte in ELF file");
    }

    int endianByte = buf.get();
    if (endianByte == ELFDATA2MSB) {
      byteOrder = ByteOrder.BIG_ENDIAN;
    } else if (endianByte == ELFDATA2LSB) {
      byteOrder = ByteOrder.LITTLE_ENDIAN;
    } else {
      throw new IOException("Unrecognized endianness in ELF file");
    }

    int versionByte = buf.get();
    if (versionByte != EV_CURRENT) {
      throw new IOException("Unrecognized ELF version");
    }

    buf.position(EI_NIDENT);
    buf.order(byteOrder);

    int fileType = getShort(buf);
    int machineType = getShort(buf);
    long fileVersion = getWord(buf);
    if (fileVersion != EV_CURRENT) {
      throw new IOException("Unrecognized ELF file version");
    }

    long entryPoint = getAddr(buf);
    long programHeaderOffset = getAddr(buf);
    long sectionHeaderOffset = getAddr(buf);
    long flags = getWord(buf);
    int headerSize = getShort(buf);
    int programHeaderEntrySize = getShort(buf);
    int programHeaderEntryCount = getShort(buf);
    int sectionHeaderEntrySize = getShort(buf);
    int sectionHeaderEntryCount = getShort(buf);
    int stringTableIndex = getShort(buf);

    parseSections(sectionHeaderOffset, sectionHeaderEntrySize, sectionHeaderEntryCount);
  }

  private void parseSections(long sectionHeaderOffset, int entrySize, int entryCount)
      throws IOException {
    if (sectionHeaderOffset < 0) {
      throw new IOException("Section header offset is too large or is negative");
    }

    int sectionTableSize = Math.multiplyExact(entrySize, entryCount);
    ByteBuffer buf = ByteBuffer.allocate(sectionTableSize).order(byteOrder);
    fileChannel.position(sectionHeaderOffset);
    int bytes = fileChannel.read(buf);
    if (bytes < sectionTableSize) {
      throw new IOException("Could not read entire ELF section table");
    }
    buf.flip();

    // Find all the relevant sections
    boolean haveSymtab = false;
    ElfSection[] sections = new ElfSection[entryCount];
    for (int i = 0; i < entryCount; i++) {
      buf.position(i * entrySize);

      long nameIndex = getWord(buf);
      int type = buf.getInt(); // signed for convenience
      long flags = getAddr(buf);
      long addr = getAddr(buf);
      long offset = getAddr(buf);
      long size = getAddr(buf);
      long link = getWord(buf);
      long info = getWord(buf);
      long align = getAddr(buf);
      long entsize = getAddr(buf);

      if (type == SHT_SYMTAB) {
        haveSymtab = true;
      }

      if (type == SHT_SYMTAB || type == SHT_STRTAB || type == SHT_DYNSYM) {
        if (link > 0xFFFF && type != SHT_STRTAB) { // section indexes fit in an unsigned short
          throw new IOException("ELF file contains invalid section link");
        }
        if (entsize > 0xFFFF && type != SHT_STRTAB) { // symbols fit in an unsigned short
          throw new IOException("ELF file contains invalid symbol size");
        }
        if (size > Integer.MAX_VALUE || size < 0) {
          throw new IOException("ELF section size too big");
        }

        ByteBuffer contents = ByteBuffer.allocate((int) size).order(byteOrder);
        if (fileChannel.read(contents, offset) < size) {
          throw new IOException("Could not read ELF section");
        }
        contents.flip();

        sections[i] = new ElfSection(type, (int) link, (int) entsize, contents);
      }
    }

    for (int i = 0; i < entryCount; i++) {
      if (sections[i] != null && sections[i].type == (haveSymtab ? SHT_SYMTAB : SHT_DYNSYM)) {
        ByteBuffer symtab = sections[i].contents;
        int numSymbols = symtab.limit() / sections[i].entsize;
        for (int j = 0; j < numSymbols; j++) {
          symtab.position(j * sections[i].entsize);

          long name = getWord(symtab);
          long value = 0;
          long size = 0;
          if (!is64Bit) {
            value = getAddr(symtab);
            size = getAddr(symtab);
          }
          byte info = symtab.get();
          byte other = symtab.get();
          int shndx = getShort(symtab);
          if (is64Bit) {
            value = getAddr(symtab);
            size = getAddr(symtab);
          }

          int type = info & ELF32_ST_TYPE_MASK;
          if (type != STT_FUNC && type != STT_OBJECT) {
            continue;
          }

          ByteBuffer stringTableBuffer = sections[sections[i].link].contents;
          stringTableBuffer.position(Math.toIntExact(name));
          String symbolName = getString(stringTableBuffer);

          if (!symbolName.isEmpty()) {
            // The version in the JDK subtracts a "baseaddr" from the value, but I'm ignoring that
            symbolValues.put(symbolName, value);
          }
        }
      }
    }
  }

  private String getString(ByteBuffer buf) throws IOException {
    byte b;
    ByteBuffer slice = buf.slice();
    while ((slice.get()) != 0) {
      // the position is moving forward
    }
    slice.position(slice.position() - 1); // back up before the null character
    slice.flip();
    return StandardCharsets.UTF_8.decode(slice).toString();
  }

  private int getShort(ByteBuffer buf) {
    return Short.toUnsignedInt(buf.getShort());
  }

  private long getWord(ByteBuffer buf) {
    return Integer.toUnsignedLong(buf.getInt());
  }

  /**
   * Returns either a 32-bit or 64-bit quantity, depending on the platform. Warning! On 64-bit
   * systems, the returned value may be negative, but it should be interpreted as unsigned.
   */
  private long getAddr(ByteBuffer buf) throws IOException {
    if (is64Bit) {
      return buf.getLong();
    } else {
      return Integer.toUnsignedLong(buf.getInt());
    }
  }

  /* Header stuff */
  private static final int EI_NIDENT = 16;

  private static final int ELFMAG0 = 0x7f;
  private static final int ELFMAG1 = 'E';
  private static final int ELFMAG2 = 'L';
  private static final int ELFMAG3 = 'F';

  private static final int ELFCLASS32 = 1;
  private static final int ELFCLASS64 = 2;

  private static final int ELFDATA2LSB = 1;
  private static final int ELFDATA2MSB = 2;

  private static final int EV_CURRENT = 1;

  private static final int MIN_SIZE_OF_ELF_HEADER = 68;

  /* Section table stuff */
  private static final int SHT_SYMTAB = 2;
  private static final int SHT_STRTAB = 3;
  private static final int SHT_DYNSYM = 11;

  /* Symbol table stuff */
  private static final int ELF32_ST_TYPE_MASK = 0xF;
  private static final int STT_OBJECT = 1;
  private static final int STT_FUNC = 2;

  private static final class ElfSection {
    private final int type;
    private final int link;
    private final int entsize;
    private final ByteBuffer contents;

    private ElfSection(int type, int link, int entsize, ByteBuffer contents) {
      this.type = type;
      this.link = link;
      this.entsize = entsize;
      this.contents = contents;
    }
  }
}
