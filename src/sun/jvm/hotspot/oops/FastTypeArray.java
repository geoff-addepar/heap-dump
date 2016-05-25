package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.debugger.OopHandle;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class FastTypeArray extends TypeArray {
  private final TypeArrayKlass klass;

  public FastTypeArray(OopHandle handle, ObjectHeap heap, TypeArrayKlass klass) {
    super(handle, heap);
    this.klass = klass;
  }

  @Override
  public TypeArrayKlass getKlass() {
    return klass;
  }
}
