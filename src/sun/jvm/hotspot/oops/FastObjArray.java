package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.debugger.OopHandle;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class FastObjArray extends ObjArray {
  private final ObjArrayKlass klass;

  public FastObjArray(OopHandle handle, ObjectHeap heap, ObjArrayKlass klass) {
    super(handle, heap);
    this.klass = klass;
  }

  @Override
  public ObjArrayKlass getKlass() {
    return klass;
  }
}
