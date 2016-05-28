package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.debugger.Address;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class FastObjArrayKlass extends ObjArrayKlass {
  private Klass bottomKlass;

  public FastObjArrayKlass(Address addr, Klass bottomKlass) {
    super(addr);
    this.bottomKlass = bottomKlass;
  }

  @Override
  public Klass getBottomKlass() {
    return bottomKlass;
  }
}
