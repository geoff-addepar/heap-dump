package com.addepar.heapdump.debugger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.DebuggerBase;
import sun.jvm.hotspot.debugger.DebuggerException;
import sun.jvm.hotspot.debugger.DebuggerUtilities;
import sun.jvm.hotspot.debugger.JVMDebugger;
import sun.jvm.hotspot.debugger.MachineDescription;
import sun.jvm.hotspot.debugger.NotInHeapException;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.debugger.ReadResult;
import sun.jvm.hotspot.debugger.ThreadProxy;
import sun.jvm.hotspot.debugger.UnalignedAddressException;
import sun.jvm.hotspot.debugger.UnmappedAddressException;
import sun.jvm.hotspot.debugger.cdbg.CDebugger;
import sun.jvm.hotspot.utilities.PlatformInfo;

/**
 * "Debugger" that attaches to the current process without stopping it. It uses files in /proc/self
 * to examine the current memory state of the process while it's running. Note that this is not
 * even remotely close to thread safe, and anything we look at with this debugger is potentially
 * stale immediately.
 *
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class SelfDebugger extends DebuggerBase implements JVMDebugger {

  private ElfSymbolLookup elfSymbolLookup;
  private FileChannel selfMem;

  public SelfDebugger(MachineDescription machDesc) throws IOException {
    this.machDesc = machDesc;
    this.utils = new DebuggerUtilities(machDesc.getAddressSize(), machDesc.isBigEndian()) {
      public void checkAlignment(long address, long alignment) {
        if(address % alignment != 0L && (alignment != 8L || address % 4L != 0L)) {
          throw new UnalignedAddressException("Trying to read at address: " + this.addressValueToString(address) + " with alignment: " + alignment, address);
        }
      }
    };
    this.initCache(4096L, (long)this.parseCacheNumPagesProperty(4096));
    this.selfMem = FileChannel.open(Paths.get("/proc/self/mem"), StandardOpenOption.READ);
    this.elfSymbolLookup = new ElfSymbolLookup();
  }

  @Override
  public void attach(int processId) throws DebuggerException {
    throw new RuntimeException("Can't attach the self debugger to a pid");
  }

  @Override
  public void attach(String execName, String coreName) throws DebuggerException {
    throw new RuntimeException("Can't attach the self debugger to a core file");
  }

  @Override
  public ReadResult readBytesFromProcess(long address, long numBytes) throws DebuggerException {
    if (numBytes > Integer.MAX_VALUE) {
      throw new RuntimeException("Read too many bytes");
    }
    ByteBuffer dst = ByteBuffer.allocate((int) numBytes);
    try {
      int bytesRead = selfMem.read(dst, address);
      if (bytesRead < numBytes) {
        return new ReadResult(address + bytesRead);
      } else {
        return new ReadResult(dst.array());
      }
    } catch (IOException e) {
      // Not sure if we can be more specific about the address
      return new ReadResult(address);
    }
  }

  @Override
  public void writeBytesToProcess(long l, long l1, byte[] bytes)
      throws UnmappedAddressException, DebuggerException {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean detach() throws DebuggerException {
    try {
      selfMem.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return true;
  }

  @Override
  public boolean hasProcessList() throws DebuggerException {
    return false;
  }

  @Override
  public List<Object> getProcessList() throws DebuggerException {
    return null;
  }

  public long getAddressValue(Address addr) {
    return addr == null?0L:((SelfAddress)addr).getValue();
  }

  @Override
  public MachineDescription getMachineDescription() throws DebuggerException {
    return machDesc;
  }

  @Override
  public boolean hasConsole() throws DebuggerException {
    return false;
  }

  @Override
  public String getConsolePrompt() throws DebuggerException {
    return null;
  }

  @Override
  public String consoleExecuteCommand(String s) throws DebuggerException {
    return null;
  }

  @Override
  public ThreadProxy getThreadForIdentifierAddress(Address address) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public ThreadProxy getThreadForThreadId(long l) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public Address parseAddress(String addressString) throws NumberFormatException, DebuggerException {
    long addr = this.utils.scanAddress(addressString);
    return addr == 0L?null:new SelfAddress(this, addr);
  }

  @Override
  public String getOS() throws DebuggerException {
    return PlatformInfo.getOS();
  }

  @Override
  public String getCPU() throws DebuggerException {
    return PlatformInfo.getCPU();
  }

  @Override
  public OopHandle lookupOop(String objectName, String symbol) {
    Address addr = this.lookup(objectName, symbol);
    return addr == null?null:addr.addOffsetToAsOopHandle(0L);
  }

  @Override
  public Address lookup(String objectName, String symbol) {
    // It's safe to ignore objectName. See libproc_impl.c and lookup_symbol()
    Long value = elfSymbolLookup.lookup(symbol);
    return value == null || value == 0 ? null : new SelfAddress(this, value);
  }

  @Override
  public CDebugger getCDebugger() throws DebuggerException {
    return null;
  }

  String addressValueToString(long address) {
    return this.utils.addressValueToString(address);
  }

  SelfAddress readAddress(long address) throws UnmappedAddressException, UnalignedAddressException {
    long value = this.readAddressValue(address);
    return value == 0L?null:new SelfAddress(this, value);
  }

  SelfAddress readCompOopAddress(long address) throws UnmappedAddressException, UnalignedAddressException {
    long value = this.readCompOopAddressValue(address);
    return value == 0L?null:new SelfAddress(this, value);
  }

  SelfAddress readCompKlassAddress(long address) throws UnmappedAddressException, UnalignedAddressException {
    long value = this.readCompKlassAddressValue(address);
    return value == 0L?null:new SelfAddress(this, value);
  }

  SelfOopHandle readOopHandle(long address) throws UnmappedAddressException, UnalignedAddressException,
      NotInHeapException {
    long value = this.readAddressValue(address);
    return value == 0L?null:new SelfOopHandle(this, value);
  }

  SelfOopHandle readCompOopHandle(long address) throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
    long value = this.readCompOopAddressValue(address);
    return value == 0L?null:new SelfOopHandle(this, value);
  }
}
