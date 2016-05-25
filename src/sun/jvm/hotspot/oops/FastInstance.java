package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.debugger.OopHandle;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class FastInstance extends Instance {
  private final InstanceKlass klass;

  public FastInstance(OopHandle handle, ObjectHeap heap, InstanceKlass klass) {
    super(handle, heap);
    this.klass = klass;
  }

  @Override
  public InstanceKlass getKlass() {
    return klass;
  }
}
