package com.addepar.heapdump.inspect.inferior;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SelfInferior implements Inferior {
  private FileChannel selfMem;
  Set<String> visitedFiles = new HashSet<>();
  private Map<String, Long> symbols = new HashMap<>();
  private long[] mappedRanges; // even indexes are starts, odd indexes are ends
  private final int pointerSize; // either 4 or 8, indicating 32-bit or 64-bit respectively

  public SelfInferior() throws IOException {
    switch (System.getProperty("os.arch")) {
      case "amd64":
        this.pointerSize = 8;
        break;

      default:
        throw new IllegalStateException("Unrecognized architecture " + System.getProperty("os.arch"));
    }

    selfMem = FileChannel.open(Paths.get("/proc/self/mem"), StandardOpenOption.READ);
    reset();
  }

  @Override
  public void read(long address, ByteBuffer buf) {
    // It seems that FileChannelImpl won't take a negative offset. There are addresses up in the negative part of the
    // address space (mainly kernel stuff, or the VDSO). Since pread() takes a signed off_t, I'm not sure anything can
    // read this...
    if (address < 0) {
      return;
    }

    try {
      selfMem.read(buf, address);
    } catch (IOException e) {
      // Returning an empty buffer means that there was a problem reading
    }
  }

  @Override
  public boolean isMapped(long address) {
    int index = Arrays.binarySearch(mappedRanges, address);

    // This is tricky. There are three cases to consider:
    // - The address is in the array. Since the end of each range is represented by the last mapped byte, it doesn't
    //   matter if it's the start or the end. All of these addresses are mapped. This is the "index >= 0" case.
    // - The address is mapped, but not explicitly in the array. In this case, the "insertion point" is going to be
    //   an odd-indexed array element. The binary search's return value will therefore be even.
    // - The address is not mapped, and not in the array. In this case, the "insertion point" is going to be
    //   an even-indexed array element. The binary search's return value will therefore be odd.
    return (index & 1) == 0 || index >= 0;
  }

  @Override
  public int getPointerSize() {
    return pointerSize;
  }

  @Override
  public long lookupSymbol(String symbolName) {
    Long ret = symbols.get(symbolName);
    if (ret == null) {
      throw new NoSuchSymbolException(symbolName);
    }
    return ret;
  }

  @Override
  public long lookupVtable(String typeName) {
     return lookupSymbol("_ZTV" + typeName.length() + typeName) + 2 * pointerSize;
  }

  @Override
  public void detach() throws IOException {
    selfMem.close();
  }

  public void reset() {
    LongArrayList rawMappings = new LongArrayList();

    Pattern lineParser = Pattern.compile(
        "^(?<low>\\p{XDigit}+)-(?<high>\\p{XDigit}+) \\S+ (?<offset>\\p{XDigit}+) \\S+ \\S+ *(?<path>\\S.*)?$");

    try {
      Files.lines(Paths.get("/proc/self/maps")).forEach(line -> {
        Matcher matcher = lineParser.matcher(line);
        if (!matcher.matches()) {
          throw new IllegalStateException("Cannot parse line in /proc/self/maps: " + line);
        }

        long low = Long.parseUnsignedLong(matcher.group("low"), 16);
        long high = Long.parseUnsignedLong(matcher.group("high"), 16);
        long offset = Long.parseUnsignedLong(matcher.group("offset"), 16);
        String path = matcher.group("path");

        rawMappings.add(low);
        rawMappings.add(high - 1);

        if (path != null && !path.startsWith("[") && !visitedFiles.contains(path)) {
          visitedFiles.add(path);
          try (ElfFile file = new ElfFile(Paths.get(path))) {
            for (Map.Entry<String, Long> entry : file.getSymbolValues().entrySet()) {
              if (entry.getValue() != 0) {
                symbols.put(entry.getKey(), entry.getValue() + low - offset);
              }
            }
          } catch (IOException e) {
            // skip things that aren't there anymore, e.g. JNA
          }
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read /proc/self/maps", e);
    }

    mappedRanges = rawMappings.toLongArray();

    // Logically, we should use an unsigned comparator for both this and the binary search, but the binary search
    // function doesn't allow a comparator.
    Arrays.sort(mappedRanges);
  }
}
