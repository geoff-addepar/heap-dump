package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.struct.Abstract_VM_Version;
import com.addepar.heapdump.inspect.struct.CollectedHeap;
import com.addepar.heapdump.inspect.struct.DynamicHotspotStruct;
import com.addepar.heapdump.inspect.struct.Flag;
import com.addepar.heapdump.inspect.struct.HotspotStruct;
import com.addepar.heapdump.inspect.struct.ImmutableSpace;
import com.addepar.heapdump.inspect.struct.JavaThread;
import com.addepar.heapdump.inspect.struct.Klass;
import com.addepar.heapdump.inspect.struct.MutableSpace;
import com.addepar.heapdump.inspect.struct.PSOldGen;
import com.addepar.heapdump.inspect.struct.PSYoungGen;
import com.addepar.heapdump.inspect.struct.ParallelScavengeHeap;
import com.addepar.heapdump.inspect.struct.Symbol;
import com.addepar.heapdump.inspect.struct.Thread;
import com.addepar.heapdump.inspect.struct.ThreadLocalAllocBuffer;
import com.addepar.heapdump.inspect.struct.Threads;
import com.addepar.heapdump.inspect.struct.Universe;
import com.addepar.heapdump.inspect.struct.arrayOopDesc;
import com.addepar.heapdump.inspect.struct.java_lang_Class;
import com.addepar.heapdump.inspect.struct.oopDesc;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LADD;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * Utilities for accessing HotSpot data structures
 */
public class HotspotStructs {

  private final AddressSpace space;
  private final HotspotTypes types;
  private final HotspotConstants constants;
  private final Set<Class<? extends HotspotStruct>> structInterfaces;
  private final Map<Class<? extends HotspotStruct>, Constructor<? extends HotspotStruct>> constructors;
  private Map<FieldDescriptor, FieldInfo> fieldMap;

  public HotspotStructs(AddressSpace space, HotspotTypes types, HotspotConstants constants) {
    this.space = space;
    this.types = types;
    this.constants = constants;
    this.fieldMap = generateFieldMap();
    this.structInterfaces = new HashSet<>(Arrays.asList(
        Abstract_VM_Version.class,
        arrayOopDesc.class,
        CollectedHeap.class,
        Flag.class,
        ImmutableSpace.class,
        java_lang_Class.class,
        JavaThread.class,
        Klass.class,
        MutableSpace.class,
        oopDesc.class,
        ParallelScavengeHeap.class,
        PSOldGen.class,
        PSYoungGen.class,
        Symbol.class,
        Thread.class,
        ThreadLocalAllocBuffer.class,
        Threads.class,
        Universe.class
    ));
    this.constructors = generateImplementations();
  }

  public <T extends HotspotStruct> T staticStruct(Class<T> structInterface) {
    return structAt(0, structInterface);
  }

  public <T extends HotspotStruct> T structAt(long address, Class<T> structInterface) {
    Constructor<?> constructor = constructors.get(structInterface);
    if (constructor == null) {
      throw new IllegalStateException("Struct class " + structInterface + " has not been registered");
    }
    try {
      Object struct = constructor.newInstance(space, address);
      return structInterface.cast(struct);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * If the given struct can safely be casted to the given other struct, then do so, otherwise return null. Requires
   * the given struct to have a vtable.
   */
  public <T extends DynamicHotspotStruct, U extends T> U dynamicCast(T struct, Class<U> subclass) {
    if (isInstanceOf(struct, subclass)) {
      return structAt(struct.getAddress(), subclass);
    } else {
      return null;
    }
  }

  public <T extends DynamicHotspotStruct> boolean isInstanceOf(T struct, Class<? extends T> subclass) {
    HotspotTypes.TypeDescriptor dynamicType = getDynamicType(struct);
    return dynamicType != null && dynamicType.isSubclassOf(getStaticType(subclass));
  }

  public HotspotTypes.TypeDescriptor getDynamicType(DynamicHotspotStruct struct) {
    return types.getDynamicType(struct.getAddress());
  }

  public HotspotTypes.TypeDescriptor getStaticType(Class<? extends HotspotStruct> subclass) {
    return types.getType(subclass.getSimpleName());
  }

  /**
   * Look up the offset an "unchecked" field, which has no type
   */
  public long offsetOf(String structName, String fieldName) {
    FieldDescriptor descriptor = new FieldDescriptor(structName, fieldName, null);
    FieldInfo info = fieldMap.get(descriptor);
    if (info.isStatic) {
      throw new IllegalStateException("Cannot get the offset of a static field");
    } else {
      return info.offset;
    }
  }

  private Map<Class<? extends HotspotStruct>, Constructor<? extends HotspotStruct>> generateImplementations() {
    Map<Class<? extends HotspotStruct>, Constructor<? extends HotspotStruct>> map = new HashMap<>();
    AsmClassLoader loader = new AsmClassLoader(this, constants);
    for (Class<? extends HotspotStruct> iface : structInterfaces) {
      map.put(iface, generateImplementation(iface, loader));
    }
    return map;
  }

  private Constructor<? extends HotspotStruct> generateImplementation(Class<? extends HotspotStruct> iface,
                                                                      AsmClassLoader loader) {
    if (DynamicHotspotStruct.class.isAssignableFrom(iface) && !getStaticType(iface).isDynamic()) {
      throw new IllegalStateException(iface.getSimpleName() + " is expected to have a vtable but one was not found");
    }

    String ifaceName = iface.getName().replace('.', '/');
    String implName = ifaceName + "Impl";

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    // Header
    cw.visit(52, ACC_PUBLIC + ACC_SUPER, implName, null, "java/lang/Object", new String[] { ifaceName });

    // Local variables
    fv = cw.visitField(ACC_PRIVATE, "addressSpace", "Lcom/addepar/heapdump/inspect/AddressSpace;", null, null);
    fv.visitEnd();
    fv = cw.visitField(ACC_PRIVATE, "address", "J", null, null);
    fv.visitEnd();

    // Constructor
    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Lcom/addepar/heapdump/inspect/AddressSpace;J)V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitFieldInsn(PUTFIELD, implName, "addressSpace", "Lcom/addepar/heapdump/inspect/AddressSpace;");
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(LLOAD, 2);
    mv.visitFieldInsn(PUTFIELD, implName, "address", "J");
    mv.visitInsn(RETURN);
    mv.visitMaxs(3, 4);
    mv.visitEnd();

    // getAddress
    mv = cw.visitMethod(ACC_PUBLIC, "getAddress", "()J", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, implName, "address", "J");
    mv.visitInsn(LRETURN);
    mv.visitMaxs(2, 1);
    mv.visitEnd();

    // setAddress
    mv = cw.visitMethod(ACC_PUBLIC, "setAddress", "(J)V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(LLOAD, 1);
    mv.visitFieldInsn(PUTFIELD, implName, "address", "J");
    mv.visitInsn(RETURN);
    mv.visitMaxs(3, 3);

    Class<? extends HotspotStruct> currentIface = iface;
    while (currentIface != HotspotStruct.class && currentIface != DynamicHotspotStruct.class) {
      if (!getStaticType(iface).isSubclassOf(getStaticType(currentIface))) {
        throw new IllegalStateException("According to the JVM, " + iface.getSimpleName()
            + " is not a subclass of " + currentIface);
      }

      for (Method method : currentIface.getDeclaredMethods()) {
        if (!Modifier.isAbstract(method.getModifiers())) {
          continue;
        }

        FieldType fieldType = method.getAnnotation(FieldType.class);
        String fieldTypeName = fieldType != null ? fieldType.value() : null;
        FieldDescriptor descriptor = new FieldDescriptor(currentIface.getSimpleName(), method.getName(), fieldTypeName);

        FieldInfo fieldInfo = fieldMap.get(descriptor);
        if (fieldInfo == null) {
          throw new RuntimeException("Could not find field " + descriptor + " in JVM's gHotSpotVMStructs");
        }
        Class<?> returnType = method.getReturnType();

        if (returnType == byte.class) {
          checkTypeWidth(fieldTypeName, 1);
          generatePrimitiveMethod(cw, method, fieldInfo, "B", "getByte", implName, "B");
        } else if (returnType == boolean.class) {
          checkTypeWidth(fieldTypeName, 1);
          generatePrimitiveMethod(cw, method, fieldInfo, "Z", "getBoolean", implName, "Z");
        } else if (returnType == char.class) {
          checkTypeWidth(fieldTypeName, 2);
          generatePrimitiveMethod(cw, method, fieldInfo, "C", "getChar", implName, "C");
        } else if (returnType == int.class) {
          checkTypeWidth(fieldTypeName, 4);
          generatePrimitiveMethod(cw, method, fieldInfo, "I", "getInt", implName, "I");
        } else if (returnType == long.class) {
          checkTypeWidth(fieldTypeName, 8);
          if (method.getAnnotation(AddressField.class) != null) {
            generatePrimitiveMethod(cw, method, fieldInfo, "J", "getPointer", implName, "J");
          } else {
            generatePrimitiveMethod(cw, method, fieldInfo, "J", "getLong", implName, "J");
          }
        } else if (returnType == short.class) {
          checkTypeWidth(fieldTypeName, 2);
          generatePrimitiveMethod(cw, method, fieldInfo, "S", "getShort", implName, "S");
        } else if (returnType == String.class) {
          checkTypeWidth(fieldTypeName, space.getPointerSize());
          generateStringMethod(cw, method, fieldInfo,implName);
        } else if (structInterfaces.contains(returnType)) {
          generateWrapperMethod(cw, method, fieldInfo, returnType.getName().replace('.', '/'), implName);
        } else {
          throw new IllegalStateException("Unrecognized return type " + returnType + " for method " + method);
        }
      }

      if (currentIface.getInterfaces().length != 1) {
        throw new IllegalStateException("Expected exactly one superinterface for " + currentIface);
      }
      currentIface = currentIface.getInterfaces()[0].asSubclass(HotspotStruct.class);
    }

    cw.visitEnd();

    byte[] classBytes = cw.toByteArray();
    Class<? extends HotspotStruct> cl =
        loader.defineClass(implName.replace('/', '.'), classBytes).asSubclass(HotspotStruct.class);

    try {
      return cl.getConstructor(AddressSpace.class, long.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkTypeWidth(String typeName, int size) {
    if (typeName == null) {
      // unchecked fields (e.g. Flag._addr) can't be verified, so just let them through and assume the
      // caller is doing the right thing
      return;
    }

    if (typeName.endsWith("*")) {
      if (size != space.getPointerSize()) {
        throw new RuntimeException("Type width of " + typeName + " does not match expected size of " + size);
      }
      return;
    }

    if (typeName.startsWith("const ")) {
      typeName = typeName.substring(6);
    }

    HotspotTypes.TypeDescriptor typeDescriptor = types.getType(typeName);
    if (typeDescriptor == null) {
      throw new RuntimeException("Type " + typeName + " was not declared");
    }

    if (typeDescriptor.getSize() != size) {
      throw new RuntimeException("Type width of " + typeName + " does not match expected size of " + size);
    }
  }

  private void generatePrimitiveMethod(ClassWriter cw, Method method, FieldInfo fieldInfo, String fieldType,
                                       String delegate, String impl, String returnFieldType) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, method.getName(), "()" + returnFieldType, null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, impl, "addressSpace", "Lcom/addepar/heapdump/inspect/AddressSpace;");
    if (fieldInfo.isStatic) {
      mv.visitLdcInsn(fieldInfo.address);
    } else {
      mv.visitLdcInsn(fieldInfo.offset);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, impl, "address", "J");
      mv.visitInsn(LADD);
    }
    mv.visitMethodInsn(INVOKEVIRTUAL, "com/addepar/heapdump/inspect/AddressSpace", delegate, "(J)" + fieldType, false);
    if (!fieldType.equals(returnFieldType)) {
      if ("I".equals(fieldType) && "J".equals(returnFieldType)) {
        mv.visitInsn(I2L);
      } else {
        throw new RuntimeException("Unsupported widening conversion " + fieldType + " to " + returnFieldType);
      }
    }
    if ("J".equals(returnFieldType)) {
      mv.visitInsn(LRETURN);
    } else {
      mv.visitInsn(IRETURN);
    }
    mv.visitMaxs(5, 1);
    mv.visitEnd();
  }

  private void generateStringMethod(ClassWriter cw, Method method, FieldInfo fieldInfo, String impl) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, method.getName(), "()Ljava/lang/String;", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, impl, "addressSpace", "Lcom/addepar/heapdump/inspect/AddressSpace;");
    if (fieldInfo.isStatic) {
      mv.visitLdcInsn(fieldInfo.address);
    } else {
      mv.visitLdcInsn(fieldInfo.offset);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, impl, "address", "J");
      mv.visitInsn(LADD);
    }
    mv.visitMethodInsn(INVOKEVIRTUAL, "com/addepar/heapdump/inspect/AddressSpace", "getAsciiString",
        "(J)Ljava/lang/String;", false);
    mv.visitInsn(ARETURN);
    mv.visitMaxs(5, 1);
    mv.visitEnd();
  }

  /**
   * Generate a method that returns another struct
   */
  private void generateWrapperMethod(ClassWriter cw, Method method, FieldInfo fieldInfo, String fieldType, String impl) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, method.getName(), "()L" + fieldType + ";", null, null);
    String fieldImpl = fieldType + "Impl";

    FieldType annotation = method.getAnnotation(FieldType.class);
    boolean embedded = !annotation.value().endsWith("*");

    // non-embedded: return new KlassImpl(addressSpace, addressSpace.getPointer([address+]offset))
    // embedded: return new KlassImpl(addressSpace, [address+]offset)
    mv.visitCode();
    mv.visitTypeInsn(NEW, fieldImpl);
    mv.visitInsn(DUP);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, impl, "addressSpace", "Lcom/addepar/heapdump/inspect/AddressSpace;");
    if (!embedded) {
      mv.visitInsn(DUP);
    }
    if (fieldInfo.isStatic) {
      mv.visitLdcInsn(fieldInfo.address);
    } else {
      mv.visitLdcInsn(fieldInfo.offset);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, impl, "address", "J");
      mv.visitInsn(LADD);
    }
    if (!embedded) {
      mv.visitMethodInsn(INVOKEVIRTUAL, "com/addepar/heapdump/inspect/AddressSpace", "getPointer", "(J)J", false);
    }
    mv.visitMethodInsn(INVOKESPECIAL, fieldImpl, "<init>", "(Lcom/addepar/heapdump/inspect/AddressSpace;J)V", false);
    mv.visitInsn(ARETURN);
    mv.visitMaxs(8, 1);
    mv.visitEnd();
  }

  private Map<FieldDescriptor, FieldInfo> generateFieldMap() {
    long vmStructs = space.getPointer(space.lookupSymbol("gHotSpotVMStructs"));
    long typeNameOffset = space.getLong(space.lookupSymbol("gHotSpotVMStructEntryTypeNameOffset"));
    long fieldNameOffset = space.getLong(space.lookupSymbol("gHotSpotVMStructEntryFieldNameOffset"));
    long typeStringOffset = space.getLong(space.lookupSymbol("gHotSpotVMStructEntryTypeStringOffset"));
    long isStaticOffset = space.getLong(space.lookupSymbol("gHotSpotVMStructEntryIsStaticOffset"));
    long offsetOffset = space.getLong(space.lookupSymbol("gHotSpotVMStructEntryOffsetOffset"));
    long addressOffset = space.getLong(space.lookupSymbol("gHotSpotVMStructEntryAddressOffset"));
    long stride = space.getLong(space.lookupSymbol("gHotSpotVMStructEntryArrayStride"));

    long current = vmStructs;

    Map<FieldDescriptor, FieldInfo> fieldMap = new HashMap<>();
    while (true) {
      String typeName = space.getAsciiString(current + typeNameOffset);
      if (typeName == null) {
        break;
      }
      String fieldName = space.getAsciiString(current + fieldNameOffset);
      String typeString = space.getAsciiString(current + typeStringOffset);
      FieldDescriptor descriptor = new FieldDescriptor(typeName, fieldName, typeString);

      FieldInfo info = new FieldInfo(space.getInt(current + isStaticOffset) != 0,
          space.getLong(current + offsetOffset), space.getPointer(current + addressOffset));

      fieldMap.put(descriptor, info);
      current += stride;
    }

    return fieldMap;
  }

  public List<String> getFields(String typeName) {
    List<String> result = new ArrayList<>();
    for (Map.Entry<FieldDescriptor, FieldInfo> field : fieldMap.entrySet()) {
      if (field.getKey().typeName.equals(typeName)) {
        result.add(field.getKey().toString());
      }
    }
    return result;
  }

  private static final class FieldDescriptor {
    final String typeName;
    final String fieldName;
    final String typeString;

    FieldDescriptor(String typeName, String fieldName, String typeString) {
      this.typeName = typeName;
      this.fieldName = fieldName.replace(".", "_");
      this.typeString = typeString;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof FieldDescriptor)) {
        return false;
      }
      FieldDescriptor other = (FieldDescriptor) obj;
      return Objects.equals(typeName, other.typeName)
          && Objects.equals(fieldName, other.fieldName)
          && Objects.equals(typeString, other.typeString);
    }

    @Override
    public int hashCode() {
      return Objects.hash(typeName, fieldName, typeString);
    }

    @Override
    public String toString() {
      return typeString + " " + typeName + "::" + fieldName;
    }
  }

  private static final class FieldInfo {
    final boolean isStatic;
    final long offset;
    final long address;

    FieldInfo(boolean isStatic, long offset, long address) {
      this.isStatic = isStatic;
      this.offset = offset;
      this.address = address;
    }
  }

  public static class AsmClassLoader extends ClassLoader {
    private final HotspotStructs hotspotStructs;
    private final HotspotConstants hotspotConstants;

    public AsmClassLoader(HotspotStructs hotspotStructs, HotspotConstants hotspotConstants) {
      this.hotspotStructs = hotspotStructs;
      this.hotspotConstants = hotspotConstants;
    }

    Class<?> defineClass(String name, byte[] classBytes) {
      return super.defineClass(name, classBytes, 0, classBytes.length);
    }

    public HotspotStructs getHotspotStructs() {
      return hotspotStructs;
    }

    public HotspotConstants getHotspotConstants() {
      return hotspotConstants;
    }
  }
}
