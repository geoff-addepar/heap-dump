package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface Klass {

  @FieldType("Klass*")
  Klass _super();
}
