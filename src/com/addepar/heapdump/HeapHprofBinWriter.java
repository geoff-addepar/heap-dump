/*
 * Copyright (c) 2004, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

package com.addepar.heapdump;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.Debugger;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.memory.SymbolTable;
import sun.jvm.hotspot.memory.SystemDictionary;
import sun.jvm.hotspot.oops.ArrayKlass;
import sun.jvm.hotspot.oops.BooleanField;
import sun.jvm.hotspot.oops.ByteField;
import sun.jvm.hotspot.oops.CharField;
import sun.jvm.hotspot.oops.DefaultHeapVisitor;
import sun.jvm.hotspot.oops.DoubleField;
import sun.jvm.hotspot.oops.Field;
import sun.jvm.hotspot.oops.FloatField;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.IntField;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.LongField;
import sun.jvm.hotspot.oops.NarrowOopField;
import sun.jvm.hotspot.oops.ObjArray;
import sun.jvm.hotspot.oops.ObjArrayKlass;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.OopField;
import sun.jvm.hotspot.oops.ShortField;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.oops.TypeArray;
import sun.jvm.hotspot.oops.TypeArrayKlass;
import sun.jvm.hotspot.runtime.AddressVisitor;
import sun.jvm.hotspot.runtime.BasicType;
import sun.jvm.hotspot.runtime.JNIHandleBlock;
import sun.jvm.hotspot.runtime.JavaThread;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.utilities.AbstractHeapGraphWriter;
import sun.jvm.hotspot.utilities.AssertionFailure;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * This class writes Java heap in hprof binary format. This format is
 * used by Heap Analysis Tool (HAT). The class is heavily influenced
 * by 'hprof_io.c' of 1.5 new hprof implementation.
 */

/* hprof binary format: (result either written to a file or sent over
 * the network).
 *
 * WARNING: This format is still under development, and is subject to
 * change without notice.
 *
 * header    "JAVA PROFILE 1.0.1" or "JAVA PROFILE 1.0.2" (0-terminated)
 * u4        size of identifiers. Identifiers are used to represent
 *            UTF8 strings, objects, stack traces, etc. They usually
 *            have the same size as host pointers. For example, on
 *            Solaris and Win32, the size is 4.
 * u4         high word
 * u4         low word    number of milliseconds since 0:00 GMT, 1/1/70
 * [record]*  a sequence of records.
 *
 */

/*
 *
 * Record format:
 *
 * u1         a TAG denoting the type of the record
 * u4         number of *microseconds* since the time stamp in the
 *            header. (wraps around in a little more than an hour)
 * u4         number of bytes *remaining* in the record. Note that
 *            this number excludes the tag and the length field itself.
 * [u1]*      BODY of the record (a sequence of bytes)
 */

/*
 * The following TAGs are supported:
 *
 * TAG           BODY       notes
 *----------------------------------------------------------
 * HPROF_UTF8               a UTF8-encoded name
 *
 *               id         name ID
 *               [u1]*      UTF8 characters (no trailing zero)
 *
 * HPROF_LOAD_CLASS         a newly loaded class
 *
 *                u4        class serial number (> 0)
 *                id        class object ID
 *                u4        stack trace serial number
 *                id        class name ID
 *
 * HPROF_UNLOAD_CLASS       an unloading class
 *
 *                u4        class serial_number
 *
 * HPROF_FRAME              a Java stack frame
 *
 *                id        stack frame ID
 *                id        method name ID
 *                id        method signature ID
 *                id        source file name ID
 *                u4        class serial number
 *                i4        line number. >0: normal
 *                                       -1: unknown
 *                                       -2: compiled method
 *                                       -3: native method
 *
 * HPROF_TRACE              a Java stack trace
 *
 *               u4         stack trace serial number
 *               u4         thread serial number
 *               u4         number of frames
 *               [id]*      stack frame IDs
 *
 *
 * HPROF_ALLOC_SITES        a set of heap allocation sites, obtained after GC
 *
 *               u2         flags 0x0001: incremental vs. complete
 *                                0x0002: sorted by allocation vs. live
 *                                0x0004: whether to force a GC
 *               u4         cutoff ratio
 *               u4         total live bytes
 *               u4         total live instances
 *               u8         total bytes allocated
 *               u8         total instances allocated
 *               u4         number of sites that follow
 *               [u1        is_array: 0:  normal object
 *                                    2:  object array
 *                                    4:  boolean array
 *                                    5:  char array
 *                                    6:  float array
 *                                    7:  double array
 *                                    8:  byte array
 *                                    9:  short array
 *                                    10: int array
 *                                    11: long array
 *                u4        class serial number (may be zero during startup)
 *                u4        stack trace serial number
 *                u4        number of bytes alive
 *                u4        number of instances alive
 *                u4        number of bytes allocated
 *                u4]*      number of instance allocated
 *
 * HPROF_START_THREAD       a newly started thread.
 *
 *               u4         thread serial number (> 0)
 *               id         thread object ID
 *               u4         stack trace serial number
 *               id         thread name ID
 *               id         thread group name ID
 *               id         thread group parent name ID
 *
 * HPROF_END_THREAD         a terminating thread.
 *
 *               u4         thread serial number
 *
 * HPROF_HEAP_SUMMARY       heap summary
 *
 *               u4         total live bytes
 *               u4         total live instances
 *               u8         total bytes allocated
 *               u8         total instances allocated
 *
 * HPROF_HEAP_DUMP          denote a heap dump
 *
 *               [heap dump sub-records]*
 *
 *                          There are four kinds of heap dump sub-records:
 *
 *               u1         sub-record type
 *
 *               HPROF_GC_ROOT_UNKNOWN         unknown root
 *
 *                          id         object ID
 *
 *               HPROF_GC_ROOT_THREAD_OBJ      thread object
 *
 *                          id         thread object ID  (may be 0 for a
 *                                     thread newly attached through JNI)
 *                          u4         thread sequence number
 *                          u4         stack trace sequence number
 *
 *               HPROF_GC_ROOT_JNI_GLOBAL      JNI global ref root
 *
 *                          id         object ID
 *                          id         JNI global ref ID
 *
 *               HPROF_GC_ROOT_JNI_LOCAL       JNI local ref
 *
 *                          id         object ID
 *                          u4         thread serial number
 *                          u4         frame # in stack trace (-1 for empty)
 *
 *               HPROF_GC_ROOT_JAVA_FRAME      Java stack frame
 *
 *                          id         object ID
 *                          u4         thread serial number
 *                          u4         frame # in stack trace (-1 for empty)
 *
 *               HPROF_GC_ROOT_NATIVE_STACK    Native stack
 *
 *                          id         object ID
 *                          u4         thread serial number
 *
 *               HPROF_GC_ROOT_STICKY_CLASS    System class
 *
 *                          id         object ID
 *
 *               HPROF_GC_ROOT_THREAD_BLOCK    Reference from thread block
 *
 *                          id         object ID
 *                          u4         thread serial number
 *
 *               HPROF_GC_ROOT_MONITOR_USED    Busy monitor
 *
 *                          id         object ID
 *
 *               HPROF_GC_CLASS_DUMP           dump of a class object
 *
 *                          id         class object ID
 *                          u4         stack trace serial number
 *                          id         super class object ID
 *                          id         class loader object ID
 *                          id         signers object ID
 *                          id         protection domain object ID
 *                          id         reserved
 *                          id         reserved
 *
 *                          u4         instance size (in bytes)
 *
 *                          u2         size of constant pool
 *                          [u2,       constant pool index,
 *                           ty,       type
 *                                     2:  object
 *                                     4:  boolean
 *                                     5:  char
 *                                     6:  float
 *                                     7:  double
 *                                     8:  byte
 *                                     9:  short
 *                                     10: int
 *                                     11: long
 *                           vl]*      and value
 *
 *                          u2         number of static fields
 *                          [id,       static field name,
 *                           ty,       type,
 *                           vl]*      and value
 *
 *                          u2         number of inst. fields (not inc. super)
 *                          [id,       instance field name,
 *                           ty]*      type
 *
 *               HPROF_GC_INSTANCE_DUMP        dump of a normal object
 *
 *                          id         object ID
 *                          u4         stack trace serial number
 *                          id         class object ID
 *                          u4         number of bytes that follow
 *                          [vl]*      instance field values (class, followed
 *                                     by super, super's super ...)
 *
 *               HPROF_GC_OBJ_ARRAY_DUMP       dump of an object array
 *
 *                          id         array object ID
 *                          u4         stack trace serial number
 *                          u4         number of elements
 *                          id         array class ID
 *                          [id]*      elements
 *
 *               HPROF_GC_PRIM_ARRAY_DUMP      dump of a primitive array
 *
 *                          id         array object ID
 *                          u4         stack trace serial number
 *                          u4         number of elements
 *                          u1         element type
 *                                     4:  boolean array
 *                                     5:  char array
 *                                     6:  float array
 *                                     7:  double array
 *                                     8:  byte array
 *                                     9:  short array
 *                                     10: int array
 *                                     11: long array
 *                          [u1]*      elements
 *
 * HPROF_CPU_SAMPLES        a set of sample traces of running threads
 *
 *                u4        total number of samples
 *                u4        # of traces
 *               [u4        # of samples
 *                u4]*      stack trace serial number
 *
 * HPROF_CONTROL_SETTINGS   the settings of on/off switches
 *
 *                u4        0x00000001: alloc traces on/off
 *                          0x00000002: cpu sampling on/off
 *                u2        stack trace depth
 *
 *
 * When the header is "JAVA PROFILE 1.0.2" a heap dump can optionally
 * be generated as a sequence of heap dump segments. This sequence is
 * terminated by an end record. The additional tags allowed by format
 * "JAVA PROFILE 1.0.2" are:
 *
 * HPROF_HEAP_DUMP_SEGMENT  denote a heap dump segment
 *
 *               [heap dump sub-records]*
 *               The same sub-record types allowed by HPROF_HEAP_DUMP
 *
 * HPROF_HEAP_DUMP_END      denotes the end of a heap dump
 *
 */

public class HeapHprofBinWriter extends AbstractHeapGraphWriter {

  // The heap size threshold used to determine if segmented format
  // ("JAVA PROFILE 1.0.2") should be used.
  private static final long HPROF_SEGMENTED_HEAP_DUMP_THRESHOLD = 2L * 0x40000000;

  // The approximate size of a heap segment. Used to calculate when to create
  // a new segment.
  private static final long HPROF_SEGMENTED_HEAP_DUMP_SEGMENT_SIZE = 1L * 0x40000000;

  // hprof binary file header
  private static final String HPROF_HEADER_1_0_1 = "JAVA PROFILE 1.0.1";
  private static final String HPROF_HEADER_1_0_2 = "JAVA PROFILE 1.0.2";

  // constants in enum HprofTag
  private static final int HPROF_UTF8             = 0x01;
  private static final int HPROF_LOAD_CLASS       = 0x02;
  private static final int HPROF_UNLOAD_CLASS     = 0x03;
  private static final int HPROF_FRAME            = 0x04;
  private static final int HPROF_TRACE            = 0x05;
  private static final int HPROF_ALLOC_SITES      = 0x06;
  private static final int HPROF_HEAP_SUMMARY     = 0x07;
  private static final int HPROF_START_THREAD     = 0x0A;
  private static final int HPROF_END_THREAD       = 0x0B;
  private static final int HPROF_HEAP_DUMP        = 0x0C;
  private static final int HPROF_CPU_SAMPLES      = 0x0D;
  private static final int HPROF_CONTROL_SETTINGS = 0x0E;

  // 1.0.2 record types
  private static final int HPROF_HEAP_DUMP_SEGMENT = 0x1C;
  private static final int HPROF_HEAP_DUMP_END     = 0x2C;

  // Heap dump constants
  // constants in enum HprofGcTag
  private static final int HPROF_GC_ROOT_UNKNOWN       = 0xFF;
  private static final int HPROF_GC_ROOT_JNI_GLOBAL    = 0x01;
  private static final int HPROF_GC_ROOT_JNI_LOCAL     = 0x02;
  private static final int HPROF_GC_ROOT_JAVA_FRAME    = 0x03;
  private static final int HPROF_GC_ROOT_NATIVE_STACK  = 0x04;
  private static final int HPROF_GC_ROOT_STICKY_CLASS  = 0x05;
  private static final int HPROF_GC_ROOT_THREAD_BLOCK  = 0x06;
  private static final int HPROF_GC_ROOT_MONITOR_USED  = 0x07;
  private static final int HPROF_GC_ROOT_THREAD_OBJ    = 0x08;
  private static final int HPROF_GC_CLASS_DUMP         = 0x20;
  private static final int HPROF_GC_INSTANCE_DUMP      = 0x21;
  private static final int HPROF_GC_OBJ_ARRAY_DUMP     = 0x22;
  private static final int HPROF_GC_PRIM_ARRAY_DUMP    = 0x23;

  // constants in enum HprofType
  private static final int HPROF_ARRAY_OBJECT  = 1;
  private static final int HPROF_NORMAL_OBJECT = 2;
  private static final int HPROF_BOOLEAN       = 4;
  private static final int HPROF_CHAR          = 5;
  private static final int HPROF_FLOAT         = 6;
  private static final int HPROF_DOUBLE        = 7;
  private static final int HPROF_BYTE          = 8;
  private static final int HPROF_SHORT         = 9;
  private static final int HPROF_INT           = 10;
  private static final int HPROF_LONG          = 11;

  // Java type codes
  private static final int JVM_SIGNATURE_BOOLEAN = 'Z';
  private static final int JVM_SIGNATURE_CHAR    = 'C';
  private static final int JVM_SIGNATURE_BYTE    = 'B';
  private static final int JVM_SIGNATURE_SHORT   = 'S';
  private static final int JVM_SIGNATURE_INT     = 'I';
  private static final int JVM_SIGNATURE_LONG    = 'J';
  private static final int JVM_SIGNATURE_FLOAT   = 'F';
  private static final int JVM_SIGNATURE_DOUBLE  = 'D';
  private static final int JVM_SIGNATURE_ARRAY   = '[';
  private static final int JVM_SIGNATURE_CLASS   = 'L';

  public synchronized void write(String fileName) throws IOException {
    // open file stream and create buffered data output stream
    fos = new FileOutputStream(fileName);
    countOut = new CountingOutputStream(new BufferedOutputStream(fos));
    out = new DataOutputStream(countOut);

    VM vm = VM.getVM();
    dbg = vm.getDebugger();

    objectHeap = new FastObjectHeap(vm.getTypeDataBase(), vm.getSymbolTable());
    try {
      java.lang.reflect.Field field = VM.class.getDeclaredField("heap");
      field.setAccessible(true);
      field.set(vm, objectHeap);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    symTbl = vm.getSymbolTable();
    Type klassType = vm.lookupType("Klass");
    klassJavaMirrorField = new OopField(klassType.getOopField("_java_mirror"), 0L);
    klassSuperField = klassType.getAddressField("_super");

    objectKlass = SystemDictionary.getObjectKlass();

    OBJ_ID_SIZE = (int) vm.getOopSize();

    BOOLEAN_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_BOOLEAN);
    BYTE_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_BYTE);
    CHAR_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_CHAR);
    SHORT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_SHORT);
    INT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_INT);
    LONG_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_LONG);
    FLOAT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_FLOAT);
    DOUBLE_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_DOUBLE);
    OBJECT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_OBJECT);

    BOOLEAN_SIZE = objectHeap.getBooleanSize();
    BYTE_SIZE = objectHeap.getByteSize();
    CHAR_SIZE = objectHeap.getCharSize();
    SHORT_SIZE = objectHeap.getShortSize();
    INT_SIZE = objectHeap.getIntSize();
    LONG_SIZE = objectHeap.getLongSize();
    FLOAT_SIZE = objectHeap.getFloatSize();
    DOUBLE_SIZE = objectHeap.getDoubleSize();

    // Always dump the heap as segments so that lamda classes can be declared inline
    useSegmentedHeapDump = true;

    // hprof bin format header
    writeFileHeader();

    // dummy stack trace without any frames so that
    // HAT can be run without -stack false option
    writeDummyTrace();

    // hprof UTF-8 symbols section
    writeSymbols();

    // HPROF_LOAD_CLASS records for all classes
    writeClasses();

    // write CLASS_DUMP records
    writeClassDumpRecords();

    // this will write heap data into the buffer stream
    write();

    // flush buffer stream.
    out.flush();

    // Fill in final length
    fillInHeapRecordLength();

    if (useSegmentedHeapDump) {
      // Write heap segment-end record
      out.writeByte((byte) HPROF_HEAP_DUMP_END);
      out.writeInt(0);
      out.writeInt(0);
    }

    // flush buffer stream and throw it.
    out.flush();
    out = null;

    // close the file stream
    fos.close();
  }

  // the function iterates heap and calls Oop type specific writers
  protected void write() throws IOException {
    SymbolTable symTbl = VM.getVM().getSymbolTable();
    javaLangClass = symTbl.probe("java/lang/Class");
    javaLangString = symTbl.probe("java/lang/String");
    try {
      System.out.println("Dumping heap");
      objectHeap.iterate(new DefaultHeapVisitor() {
        public void prologue(long usedSize) {
          try {
            writeHeapHeader();
          } catch (IOException exp) {
            throw new RuntimeException(exp);
          }
        }

        public boolean doObj(Oop oop) {
          try {
            writeHeapRecordPrologue();
            if (oop instanceof TypeArray) {
              writePrimitiveArray((TypeArray)oop);
            } else if (oop instanceof ObjArray) {
              Klass klass = oop.getKlass();
              ObjArrayKlass oak = (ObjArrayKlass) klass;
              Klass bottomType = oak.getBottomKlass();
              if (bottomType instanceof InstanceKlass ||
                  bottomType instanceof TypeArrayKlass) {
                writeObjectArray((ObjArray)oop);
              } else {
                writeInternalObject(oop);
              }
            } else if (oop instanceof Instance) {
              Instance instance = (Instance) oop;
              Klass klass = instance.getKlass();
              Symbol name = klass.getName();
              if (name.equals(javaLangString)) {
                writeString(instance);
              } else if (name.equals(javaLangClass)) {
                writeClass(instance);
              } else {
                writeInstance(instance);
              }
            } else {
              // not-a-Java-visible oop
              writeInternalObject(oop);
            }
            writeHeapRecordEpilogue();
          } catch (IOException exp) {
            throw new RuntimeException(exp);
          }
          return false;
        }

        public void epilogue() {
          try {
            writeHeapFooter();
          } catch (IOException exp) {
            throw new RuntimeException(exp);
          }
        }
      });
      System.out.println("Done dumping heap");

      // write JavaThreads
      writeJavaThreads();

      // write JNI global handles
      writeGlobalJNIHandles();

    } catch (RuntimeException re) {
      handleRuntimeException(re);
    }
  }

  @Override
  protected void writeHeapRecordPrologue() throws IOException {
    if (currentSegmentStart == 0) {
      // write heap data header, depending on heap size use segmented heap
      // format
      out.writeByte((byte) (useSegmentedHeapDump ? HPROF_HEAP_DUMP_SEGMENT
          : HPROF_HEAP_DUMP));
      out.writeInt(0);

      // remember position of dump length, we will fixup
      // length later - hprof format requires length.
      currentSegmentStart = countOut.getCount();

      // write dummy length of 0 and we'll fix it later.
      out.writeInt(0);
    }
  }

  @Override
  protected void writeHeapRecordEpilogue() throws IOException {
    if (useSegmentedHeapDump) {
      if ((countOut.getCount() - currentSegmentStart - 4) >= HPROF_SEGMENTED_HEAP_DUMP_SEGMENT_SIZE) {
        fillInHeapRecordLength();
        currentSegmentStart = 0;
      }
    }
  }

  private void fillInHeapRecordLength() throws IOException {
    // now get current position to calculate length
    long dumpEnd = countOut.getCount();

    // calculate length of heap data
    long dumpLenLong = (dumpEnd - currentSegmentStart - 4L);

    // Check length boundary, overflow could happen but is _very_ unlikely
    if(dumpLenLong >= (4L * 0x40000000)){
      throw new RuntimeException("Heap segment size overflow.");
    }

    // flush to ensure the surrounding bytes have been written
    out.flush();

    // seek the position to write length
    fos.getChannel().position(currentSegmentStart);

    int dumpLen = (int) dumpLenLong;

    // write length as integer
    fos.write((dumpLen >>> 24) & 0xFF);
    fos.write((dumpLen >>> 16) & 0xFF);
    fos.write((dumpLen >>> 8) & 0xFF);
    fos.write((dumpLen >>> 0) & 0xFF);

    //Reset to previous current position
    fos.getChannel().position(dumpEnd);
  }

  private void writeClassDumpRecords() throws IOException {
    SystemDictionary sysDict = VM.getVM().getSystemDictionary();
    try {
      sysDict.allClassesDo(new SystemDictionary.ClassVisitor() {
        public void visit(Klass k) {
          try {
            writeHeapRecordPrologue();
            writeClassDumpRecord(k);
            writeHeapRecordEpilogue();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    } catch (RuntimeException re) {
      handleRuntimeException(re);
    }
  }

  protected void writeClass(Instance instance) throws IOException {
    Klass reflectedKlass = objectHeap.getKlassForClass(instance);
    // dump instance record only for primitive type Class objects.
    // all other Class objects are covered by writeClassDumpRecords.
    if (reflectedKlass == null) {
      writeInstance(instance);
    } else if (!classDataCache.containsKey(reflectedKlass.getAddress()) && reflectedKlass instanceof InstanceKlass) {
      writeClassDumpRecord((InstanceKlass) reflectedKlass);
    }
  }

  private void writeClassDumpRecord(Klass k) throws IOException {
    out.writeByte((byte)HPROF_GC_CLASS_DUMP);
    writeObjectIDForKlass(k);
    out.writeInt(DUMMY_STACK_TRACE_ID);

    Klass superKlass = getJavaSuper(k);
    if (superKlass != null) {
      writeObjectIDForKlass(superKlass);
    } else {
      writeObjectID(null);
    }

    if (k instanceof InstanceKlass) {
      InstanceKlass ik = (InstanceKlass) k;
      writeObjectID(ik.getClassLoader());
      writeObjectID(null);  // ik.getJavaMirror().getSigners());
      writeObjectID(null);  // ik.getJavaMirror().getProtectionDomain());
      // two reserved id fields
      writeObjectID(null);
      writeObjectID(null);
      List fields = getInstanceFields(ik);
      int instSize = getSizeForFields(fields);
      classDataCache.put(ik.getAddress(), new ClassData(instSize, fields));
      out.writeInt(instSize);

      // For now, ignore constant pool - HAT ignores too!
      // output number of cp entries as zero.
      out.writeShort((short) 0);

      List declaredFields = ik.getImmediateFields();
      List staticFields = new ArrayList();
      List instanceFields = new ArrayList();
      Iterator itr = null;
      for (itr = declaredFields.iterator(); itr.hasNext();) {
        Field field = (Field) itr.next();
        if (field.isStatic()) {
          staticFields.add(field);
        } else {
          instanceFields.add(field);
        }
      }

      // dump static field descriptors
      writeFieldDescriptors(staticFields, ik);

      // dump instance field descriptors
      writeFieldDescriptors(instanceFields, null);
    } else {
      if (k instanceof ObjArrayKlass) {
        ObjArrayKlass oak = (ObjArrayKlass) k;
        Klass bottomKlass = oak.getBottomKlass();
        if (bottomKlass instanceof InstanceKlass) {
          InstanceKlass ik = (InstanceKlass) bottomKlass;
          writeObjectID(ik.getClassLoader());
          writeObjectID(null); // ik.getJavaMirror().getSigners());
          writeObjectID(null); // ik.getJavaMirror().getProtectionDomain());
        } else {
          writeObjectID(null);
          writeObjectID(null);
          writeObjectID(null);
        }
      } else {
        writeObjectID(null);
        writeObjectID(null);
        writeObjectID(null);
      }
      // two reserved id fields
      writeObjectID(null);
      writeObjectID(null);
      // write zero instance size -- as instance size
      // is variable for arrays.
      out.writeInt(0);
      // no constant pool for array klasses
      out.writeShort((short) 0);
      // no static fields for array klasses
      out.writeShort((short) 0);
      // no instance fields for array klasses
      out.writeShort((short) 0);
    }
  }

  protected void writeJavaThread(JavaThread jt, int index) throws IOException {
    out.writeByte((byte) HPROF_GC_ROOT_THREAD_OBJ);
    writeObjectID(jt.getThreadObj());
    out.writeInt(index);
    out.writeInt(DUMMY_STACK_TRACE_ID);
    writeLocalJNIHandles(jt, index);
  }

  protected void writeLocalJNIHandles(JavaThread jt, int index) throws IOException {
    final int threadIndex = index;
    JNIHandleBlock blk = jt.activeHandles();
    if (blk != null) {
      try {
        blk.oopsDo(new AddressVisitor() {
          public void visitAddress(Address handleAddr) {
            try {
              if (handleAddr != null) {
                OopHandle oopHandle = handleAddr.getOopHandleAt(0);
                Oop oop = objectHeap.newOop(oopHandle);
                // exclude JNI handles hotspot internal objects
                if (oop != null && isJavaVisible(oop)) {
                  out.writeByte((byte) HPROF_GC_ROOT_JNI_LOCAL);
                  writeObjectID(oop);
                  out.writeInt(threadIndex);
                  out.writeInt(EMPTY_FRAME_DEPTH);
                }
              }
            } catch (IOException exp) {
              throw new RuntimeException(exp);
            }
          }
          public void visitCompOopAddress(Address handleAddr) {
            throw new RuntimeException(
                " Should not reach here. JNIHandles are not compressed \n");
          }
        });
      } catch (RuntimeException re) {
        handleRuntimeException(re);
      }
    }
  }

  protected void writeGlobalJNIHandle(Address handleAddr) throws IOException {
    OopHandle oopHandle = handleAddr.getOopHandleAt(0);
    Oop oop = objectHeap.newOop(oopHandle);
    // exclude JNI handles of hotspot internal objects
    if (oop != null && isJavaVisible(oop)) {
      out.writeByte((byte) HPROF_GC_ROOT_JNI_GLOBAL);
      writeObjectID(oop);
      // use JNIHandle address as ID
      writeObjectID(getAddressValue(handleAddr));
    }
  }

  protected void writeObjectArray(ObjArray array) throws IOException {
    out.writeByte((byte) HPROF_GC_OBJ_ARRAY_DUMP);
    writeObjectID(array);
    out.writeInt(DUMMY_STACK_TRACE_ID);
    out.writeInt((int) array.getLength());
    writeObjectIDForKlass(array.getKlass());
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      OopHandle handle = array.getOopHandleAt(index);
      writeObjectID(getAddressValue(handle));
    }
  }

  protected void writePrimitiveArray(TypeArray array) throws IOException {
    out.writeByte((byte) HPROF_GC_PRIM_ARRAY_DUMP);
    writeObjectID(array);
    out.writeInt(DUMMY_STACK_TRACE_ID);
    out.writeInt((int) array.getLength());
    TypeArrayKlass tak = (TypeArrayKlass) array.getKlass();
    final int type = (int) tak.getElementType();
    out.writeByte((byte) type);
    switch (type) {
      case TypeArrayKlass.T_BOOLEAN:
        writeBooleanArray(array);
        break;
      case TypeArrayKlass.T_CHAR:
        writeCharArray(array);
        break;
      case TypeArrayKlass.T_FLOAT:
        writeFloatArray(array);
        break;
      case TypeArrayKlass.T_DOUBLE:
        writeDoubleArray(array);
        break;
      case TypeArrayKlass.T_BYTE:
        writeByteArray(array);
        break;
      case TypeArrayKlass.T_SHORT:
        writeShortArray(array);
        break;
      case TypeArrayKlass.T_INT:
        writeIntArray(array);
        break;
      case TypeArrayKlass.T_LONG:
        writeLongArray(array);
        break;
      default:
        throw new RuntimeException("should not reach here");
    }
  }

  private void writeBooleanArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = BOOLEAN_BASE_OFFSET + index * BOOLEAN_SIZE;
      out.writeBoolean(array.getHandle().getJBooleanAt(offset));
    }
  }

  private void writeByteArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = BYTE_BASE_OFFSET + index * BYTE_SIZE;
      out.writeByte(array.getHandle().getJByteAt(offset));
    }
  }

  private void writeShortArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = SHORT_BASE_OFFSET + index * SHORT_SIZE;
      out.writeShort(array.getHandle().getJShortAt(offset));
    }
  }

  private void writeIntArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = INT_BASE_OFFSET + index * INT_SIZE;
      out.writeInt(array.getHandle().getJIntAt(offset));
    }
  }

  private void writeLongArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = LONG_BASE_OFFSET + index * LONG_SIZE;
      out.writeLong(array.getHandle().getJLongAt(offset));
    }
  }

  private void writeCharArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = CHAR_BASE_OFFSET + index * CHAR_SIZE;
      out.writeChar(array.getHandle().getJCharAt(offset));
    }
  }

  private void writeFloatArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = FLOAT_BASE_OFFSET + index * FLOAT_SIZE;
      out.writeFloat(array.getHandle().getJFloatAt(offset));
    }
  }

  private void writeDoubleArray(TypeArray array) throws IOException {
    final int length = (int) array.getLength();
    for (int index = 0; index < length; index++) {
      long offset = DOUBLE_BASE_OFFSET + index * DOUBLE_SIZE;
      out.writeDouble(array.getHandle().getJDoubleAt(offset));
    }
  }

  protected void writeInstance(Instance instance) throws IOException {
    Klass klass = instance.getKlass();
    ClassData cd = (ClassData) classDataCache.get(klass.getAddress());
    if (cd == null) {
      // The class is not present in the system dictionary, probably Lambda.
      // Add it to cache here
      if (klass instanceof InstanceKlass) {
        InstanceKlass ik = (InstanceKlass) klass;
        List fields = getInstanceFields(ik);
        int instSize = getSizeForFields(fields);
        cd = new ClassData(instSize, fields);
        classDataCache.put(ik.getAddress(), cd);

        writeClassDumpRecord(ik);
      }
    }

    out.writeByte((byte) HPROF_GC_INSTANCE_DUMP);
    writeObjectID(instance);
    out.writeInt(DUMMY_STACK_TRACE_ID);
    writeObjectIDForKlass(klass);

    if (cd == null) {
      throw new AssertionFailure("can not get class data for " + klass.getName().asString() + klass.getAddress());
    }
    List fields = cd.fields;
    int size = cd.instSize;
    out.writeInt(size);
    for (Iterator itr = fields.iterator(); itr.hasNext();) {
      writeField((Field) itr.next(), instance);
    }
  }

  //-- Internals only below this point

  private void writeFieldDescriptors(List fields, InstanceKlass ik)
      throws IOException {
    // ik == null for instance fields.
    out.writeShort((short) fields.size());
    for (Iterator itr = fields.iterator(); itr.hasNext();) {
      Field field = (Field) itr.next();
      Symbol name = symTbl.probe(field.getID().getName());
      writeSymbolID(name);
      char typeCode = (char) field.getSignature().getByteAt(0);
      int kind = signatureToHprofKind(typeCode);
      out.writeByte((byte)kind);
      if (ik != null) {
        // static field
        writeField(field, ik.getJavaMirror());
      }
    }
  }

  public static int signatureToHprofKind(char ch) {
    switch (ch) {
      case JVM_SIGNATURE_CLASS:
      case JVM_SIGNATURE_ARRAY:
        return HPROF_NORMAL_OBJECT;
      case JVM_SIGNATURE_BOOLEAN:
        return HPROF_BOOLEAN;
      case JVM_SIGNATURE_CHAR:
        return HPROF_CHAR;
      case JVM_SIGNATURE_FLOAT:
        return HPROF_FLOAT;
      case JVM_SIGNATURE_DOUBLE:
        return HPROF_DOUBLE;
      case JVM_SIGNATURE_BYTE:
        return HPROF_BYTE;
      case JVM_SIGNATURE_SHORT:
        return HPROF_SHORT;
      case JVM_SIGNATURE_INT:
        return HPROF_INT;
      case JVM_SIGNATURE_LONG:
        return HPROF_LONG;
      default:
        throw new RuntimeException("should not reach here");
    }
  }

  private void writeField(Field field, Oop oop) throws IOException {
    char typeCode = (char) field.getSignature().getByteAt(0);
    switch (typeCode) {
      case JVM_SIGNATURE_BOOLEAN:
        out.writeBoolean(((BooleanField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_CHAR:
        out.writeChar(((CharField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_BYTE:
        out.writeByte(((ByteField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_SHORT:
        out.writeShort(((ShortField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_INT:
        out.writeInt(((IntField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_LONG:
        out.writeLong(((LongField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_FLOAT:
        out.writeFloat(((FloatField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_DOUBLE:
        out.writeDouble(((DoubleField)field).getValue(oop));
        break;
      case JVM_SIGNATURE_CLASS:
      case JVM_SIGNATURE_ARRAY: {
        if (VM.getVM().isCompressedOopsEnabled()) {
          OopHandle handle = ((NarrowOopField)field).getValueAsOopHandle(oop);
          writeObjectID(getAddressValue(handle));
        } else {
          OopHandle handle = ((OopField)field).getValueAsOopHandle(oop);
          writeObjectID(getAddressValue(handle));
        }
        break;
      }
      default:
        throw new RuntimeException("should not reach here");
    }
  }

  private void writeHeader(int tag, int len) throws IOException {
    out.writeByte((byte)tag);
    out.writeInt(0); // current ticks
    out.writeInt(len);
  }

  private void writeDummyTrace() throws IOException {
    writeHeader(HPROF_TRACE, 3 * 4);
    out.writeInt(DUMMY_STACK_TRACE_ID);
    out.writeInt(0);
    out.writeInt(0);
  }

  private void writeSymbols() throws IOException {
    try {
      symTbl.symbolsDo(new SymbolTable.SymbolVisitor() {
        public void visit(Symbol sym) {
          try {
            writeSymbol(sym);
          } catch (IOException exp) {
            throw new RuntimeException(exp);
          }
        }
      });
    } catch (RuntimeException re) {
      handleRuntimeException(re);
    }
  }

  private void writeSymbol(Symbol sym) throws IOException {
    byte[] buf = sym.asString().getBytes("UTF-8");
    writeHeader(HPROF_UTF8, buf.length + OBJ_ID_SIZE);
    writeSymbolID(sym);
    out.write(buf);
  }

  private void writeClasses() throws IOException {
    final Set klasses = new HashSet();
    // write class list (id, name) association
    SystemDictionary sysDict = VM.getVM().getSystemDictionary();
    try {
      sysDict.allClassesDo(new SystemDictionary.ClassVisitor() {
        public void visit(Klass k) {
          try {
            klasses.add(k.getAddress());
            writeClassLoadRecord(k);
          } catch (IOException exp) {
            throw new RuntimeException(exp);
          }
        }
      });
    } catch (RuntimeException re) {
      handleRuntimeException(re);
    }

    final Symbol javaLangClass = symTbl.probe("java/lang/Class");
    System.out.println("Dumping lambda classes");
    objectHeap.iterate(new DefaultHeapVisitor() {
      @Override
      public boolean doObj(Oop oop) {
        if (oop instanceof Instance) {
          Instance instance = (Instance)oop;
          Klass klass = instance.getKlass();
          Symbol name = klass.getName();
          if (name.equals(javaLangClass)) {
            try {
              Klass reflectedKlass = objectHeap.getKlassForClass(instance);
              if (reflectedKlass != null && !klasses.contains(reflectedKlass.getAddress())) {
                klasses.add(reflectedKlass.getAddress());
                writeClassLoadRecord(reflectedKlass);
              }
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
        return false;
      }
    });
    System.out.println("Done dumping lambda classes");
  }

  private void writeClassLoadRecord(Klass k) throws IOException {
    writeHeader(HPROF_LOAD_CLASS, 2 * (OBJ_ID_SIZE + 4));
    out.writeInt(classSerialNum);
    writeObjectIDForKlass(k);
    out.writeInt(DUMMY_STACK_TRACE_ID);
    writeSymbolID(k.getName());
    classSerialNum++;
  }

  // writes hprof binary file header
  private void writeFileHeader() throws IOException {
    // version string
    if(useSegmentedHeapDump) {
      out.writeBytes(HPROF_HEADER_1_0_2);
    }
    else {
      out.writeBytes(HPROF_HEADER_1_0_1);
    }
    out.writeByte((byte)'\0');

    // write identifier size. we use pointers as identifiers.
    out.writeInt(OBJ_ID_SIZE);

    // timestamp -- file creation time.
    out.writeLong(System.currentTimeMillis());
  }

  private void writeObjectIDForKlass(Klass klass) throws IOException {
    OopHandle handle = klass != null ? klassJavaMirrorField.getValueAsOopHandle(klass) : null;
    long address = getAddressValue(handle);
    writeObjectID(address);
  }

  // writes unique ID for an object
  private void writeObjectID(Oop oop) throws IOException {
    OopHandle handle = (oop != null)? oop.getHandle() : null;
    long address = getAddressValue(handle);
    writeObjectID(address);
  }

  private void writeSymbolID(Symbol sym) throws IOException {
    writeObjectID(getAddressValue(sym.getAddress()));
  }

  private void writeObjectID(long address) throws IOException {
    if (OBJ_ID_SIZE == 4) {
      out.writeInt((int) address);
    } else {
      out.writeLong(address);
    }
  }

  private long getAddressValue(Address addr) {
    return (addr == null)? 0L : dbg.getAddressValue(addr);
  }

  // get all declared as well as inherited (directly/indirectly) fields
  private List/*<Field>*/ getInstanceFields(InstanceKlass ik) {
    InstanceKlass klass = ik;
    List res = new ArrayList();
    while (klass != null) {
      List curFields = klass.getImmediateFields();
      for (Iterator itr = curFields.iterator(); itr.hasNext();) {
        Field f = (Field) itr.next();
        if (! f.isStatic()) {
          res.add(f);
        }
      }
      klass = (InstanceKlass) getSuper(klass);
    }
    return res;
  }

  private Klass getSuper(InstanceKlass klass) {
    Address superAddress = klassSuperField.getValue(klass.getAddress());
    if (superAddress == null) {
      return null;
    }
    return objectHeap.getKlassAtAddress(superAddress);
  }

  private Klass getJavaSuper(Klass klass) {
    if (klass instanceof InstanceKlass) {
      return getSuper((InstanceKlass) klass);
    } else if (klass instanceof ArrayKlass) {
      return objectKlass;
    } else {
      return null;
    }
  }

  // get size in bytes (in stream) required for given fields.  Note
  // that this is not the same as object size in heap. The size in
  // heap will include size of padding/alignment bytes as well.
  private int getSizeForFields(List fields) {
    int size = 0;
    for (Iterator itr = fields.iterator(); itr.hasNext();) {
      Field field = (Field) itr.next();
      char typeCode = (char) field.getSignature().getByteAt(0);
      switch (typeCode) {
        case JVM_SIGNATURE_BOOLEAN:
        case JVM_SIGNATURE_BYTE:
          size++;
          break;
        case JVM_SIGNATURE_CHAR:
        case JVM_SIGNATURE_SHORT:
          size += 2;
          break;
        case JVM_SIGNATURE_INT:
        case JVM_SIGNATURE_FLOAT:
          size += 4;
          break;
        case JVM_SIGNATURE_CLASS:
        case JVM_SIGNATURE_ARRAY:
          size += OBJ_ID_SIZE;
          break;
        case JVM_SIGNATURE_LONG:
        case JVM_SIGNATURE_DOUBLE:
          size += 8;
          break;
        default:
          throw new RuntimeException("should not reach here");
      }
    }
    return size;
  }

  // We don't have allocation site info. We write a dummy
  // stack trace with this id.
  private static final int DUMMY_STACK_TRACE_ID = 1;
  private static final int EMPTY_FRAME_DEPTH = -1;

  private DataOutputStream out;
  private CountingOutputStream countOut;
  private FileOutputStream fos;
  private Debugger dbg;
  private FastObjectHeap objectHeap;
  private SymbolTable symTbl;
  private OopField klassJavaMirrorField;
  private AddressField klassSuperField;
  private Klass objectKlass;

  // oopSize of the debuggee
  private int OBJ_ID_SIZE;

  // Added for hprof file format 1.0.2 support
  private boolean useSegmentedHeapDump;
  private long currentSegmentStart;

  private long BOOLEAN_BASE_OFFSET;
  private long BYTE_BASE_OFFSET;
  private long CHAR_BASE_OFFSET;
  private long SHORT_BASE_OFFSET;
  private long INT_BASE_OFFSET;
  private long LONG_BASE_OFFSET;
  private long FLOAT_BASE_OFFSET;
  private long DOUBLE_BASE_OFFSET;
  private long OBJECT_BASE_OFFSET;

  private long BOOLEAN_SIZE;
  private long BYTE_SIZE;
  private long CHAR_SIZE;
  private long SHORT_SIZE;
  private long INT_SIZE;
  private long LONG_SIZE;
  private long FLOAT_SIZE;
  private long DOUBLE_SIZE;

  private static class ClassData {
    int instSize;
    List fields;

    ClassData(int instSize, List fields) {
      this.instSize = instSize;
      this.fields = fields;
    }
  }

  private int classSerialNum = 1;
  private Map classDataCache = new HashMap(); // <InstanceKlass, ClassData>
}
