package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.debugger.Address;

/**
 * This is a giant hack to disable some useless code in the InstanceKlass constructor.
 *
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class FastInstanceKlass extends InstanceKlass {
  private final boolean initialized;

  public FastInstanceKlass(Address addr) {
    super(addr);
    initialized = true;
  }

  @Override
  public int getAllFieldsCount() {
    if (initialized) {
      return super.getAllFieldsCount();
    } else {
      return 0;
    }
  }

  @Override
  public int getJavaFieldsCount() {
    if (initialized) {
      return super.getJavaFieldsCount();
    } else {
      return 0;
    }
  }
}
