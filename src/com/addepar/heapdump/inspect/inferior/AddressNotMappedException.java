package com.addepar.heapdump.inspect.inferior;

public class AddressNotMappedException extends RuntimeException {
  private static final long serialVersionUID = -7032976555027285435L;

  public AddressNotMappedException(long address) {
    super(Long.toHexString(address));
  }
}
