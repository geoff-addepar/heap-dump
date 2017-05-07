package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface JavaThread extends Thread {

  @FieldType("JavaThread*")
  JavaThread _next();
}
