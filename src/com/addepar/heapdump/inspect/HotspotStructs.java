package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.struct.CollectedHeap;
import com.addepar.heapdump.inspect.struct.Klass;
import com.addepar.heapdump.inspect.struct.Universe;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
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
  private final Set<Class<?>> structInterfaces;
  private final Map<Class<?>, Constructor<?>> constructors;
  Map<FieldDescriptor, FieldInfo> fieldMap;

  public HotspotStructs(AddressSpace space, HotspotTypes types) {
    this.space = space;
    this.types = types;
    this.fieldMap = generateFieldMap();
    this.structInterfaces = new HashSet<>(Arrays.asList(
        CollectedHeap.class,
        Klass.class,
        Universe.class
    ));
    this.constructors = generateImplementations();
  }

  public <T> T staticStruct(Class<T> structInterface) {
    return structAt(0, structInterface);
  }

  public <T> T structAt(long address, Class<T> structInterface) {
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

  private Map<Class<?>, Constructor<?>> generateImplementations() {
    Map<Class<?>, Constructor<?>> map = new HashMap<>();
    AsmClassLoader loader = new AsmClassLoader();
    for (Class<?> iface : structInterfaces) {
      map.put(iface, generateImplementation(iface, loader));
    }
    return map;
  }

  private Constructor<?> generateImplementation(Class<?> iface, AsmClassLoader loader) {
    String ifaceName = iface.getName().replace('.', '/');
    String implName = ifaceName + "Impl";

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    // Header
    cw.visit(52, ACC_PUBLIC + ACC_SUPER, implName, null, "java/lang/Object", new String[] { ifaceName });

    // Local variables
    fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "addressSpace", "Lcom/addepar/heapdump/inspect/AddressSpace;", null, null);
    fv.visitEnd();
    fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "address", "J", null, null);
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

    for (Method method : iface.getMethods()) {
      String fieldType = method.getAnnotation(FieldType.class).value();
      FieldDescriptor descriptor = new FieldDescriptor(iface.getSimpleName(), method.getName(), fieldType);

      FieldInfo fieldInfo = fieldMap.get(descriptor);
      if (fieldInfo == null) {
        throw new RuntimeException("Could not find field " + descriptor + " in JVM's gHotSpotVMStructs");
      }
      Class<?> returnType = method.getReturnType();

      if (returnType == byte.class) {
        checkTypeWidth(fieldType, 1);
        generatePrimitiveMethod(cw, method, fieldInfo, "B", "getByte", implName);
      } else if (returnType == boolean.class) {
        checkTypeWidth(fieldType, 1);
        generatePrimitiveMethod(cw, method, fieldInfo, "Z", "getBoolean", implName);
      } else if (returnType == char.class) {
        checkTypeWidth(fieldType, 2);
        generatePrimitiveMethod(cw, method, fieldInfo, "C", "getChar", implName);
      } else if (returnType == int.class) {
        checkTypeWidth(fieldType, 4);
        generatePrimitiveMethod(cw, method, fieldInfo, "I", "getInt", implName);
      } else if (returnType == long.class) {
        checkTypeWidth(fieldType, 8);
        generatePrimitiveMethod(cw, method, fieldInfo, "J", "getLong", implName);
      } else if (returnType == short.class) {
        checkTypeWidth(fieldType, 2);
        generatePrimitiveMethod(cw, method, fieldInfo, "S", "getShort", implName);
      } else if (structInterfaces.contains(returnType)) {
        if (!fieldType.endsWith("*")) {
          checkTypeWidth(fieldType, space.getPointerSize());
        }
        generateWrapperMethod(cw, method, fieldInfo, returnType.getName().replace('.', '/'), implName);
      } else {
        throw new IllegalStateException("Unrecognized return type " + returnType + " for method " + method);
      }
    }

    cw.visitEnd();
    byte[] classBytes = cw.toByteArray();
    Class<?> cl = loader.defineClass(implName.replace('/', '.'), classBytes);

    try {
      return cl.getConstructor(AddressSpace.class, long.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkTypeWidth(String typeName, int size) {
    HotspotTypes.TypeDescriptor typeDescriptor = types.getType(typeName);
    if (typeDescriptor == null) {
      throw new RuntimeException("Type " + typeName + " was not declared");
    }
    if (typeDescriptor.getSize() != size) {
      throw new RuntimeException("Type width of " + typeName + " does not match expected size of " + size);
    }
  }

  private void generatePrimitiveMethod(ClassWriter cw, Method method, FieldInfo fieldInfo, String fieldType, String delegate, String impl) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, method.getName(), "()" + fieldType, null, null);
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
    if ("J".equals(fieldType)) {
      mv.visitInsn(LRETURN);
    } else {
      mv.visitInsn(IRETURN);
    }
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
      String typeName = space.getAsciiString(space.getPointer(current + typeNameOffset));
      if (typeName == null) {
        break;
      }
      String fieldName = space.getAsciiString(space.getPointer(current + fieldNameOffset));
      String typeString = space.getAsciiString(space.getPointer(current + typeStringOffset));
      FieldDescriptor descriptor = new FieldDescriptor(typeName, fieldName, typeString);

      FieldInfo info = new FieldInfo(space.getInt(current + isStaticOffset) != 0,
          space.getLong(current + offsetOffset), space.getPointer(current + addressOffset));

      fieldMap.put(descriptor, info);
      current += stride;
    }

    return fieldMap;
  }

  private static final class FieldDescriptor {
    final String typeName;
    final String fieldName;
    final String typeString;

    FieldDescriptor(String typeName, String fieldName, String typeString) {
      this.typeName = typeName;
      this.fieldName = fieldName;
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
      return "[" + typeName + "," + fieldName + "," + typeString + "]";
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

  private static class AsmClassLoader extends ClassLoader {
    Class<?> defineClass(String name, byte[] classBytes) {
      return super.defineClass(name, classBytes, 0, classBytes.length);
    }
  }
}
