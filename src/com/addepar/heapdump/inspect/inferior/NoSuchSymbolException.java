package com.addepar.heapdump.inspect.inferior;

/**
 * Thrown when an attempt is made to look up a symbol that cannot be found in the inferior
 */
@SuppressWarnings("serial")
public class NoSuchSymbolException extends RuntimeException {

  public NoSuchSymbolException(String symbolName) {
    super(symbolName);
  }
}
