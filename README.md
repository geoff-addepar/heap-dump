# heap-dump
A working version of jmap -F

# Rationale
The current version of `jmap -F` (or `jmap -dump:format=b,file=dump.hprof /path/to/java corefile`) doesn't handle lambdas correctly, and generates a corrupted heap dump file. If you try to load it with `jhat`, you get errors like this:
```
WARNING: Class 1825e75d8 not found, adding fake class!
WARNING:  Failed to resolve object id 0x1803c30a0 for field clazz (signature L)
```

The Eclipse MAT utterly rejects these corrupt heap dump files, and several bugs have been filed, such as https://bugs.eclipse.org/bugs/show_bug.cgi?id=471757. The error would typically look like this:
```
!ENTRY org.eclipse.osgi 4 0 2016-05-20 18:37:11.795
!MESSAGE Application error
!STACK 1
java.lang.NullPointerException
	at org.eclipse.mat.hprof.HprofParserHandlerImpl.resolveClassHierarchy(HprofParserHandlerImpl.java:587)
	at org.eclipse.mat.hprof.Pass2Parser.readInstanceDump(Pass2Parser.java:205)
	at org.eclipse.mat.hprof.Pass2Parser.readDumpSegments(Pass2Parser.java:159)
	at org.eclipse.mat.hprof.Pass2Parser.read(Pass2Parser.java:89)
```

# Status
This is a one-day hack. It has not been extensively tested.

# License
I copied most of the code directly out of the JDK, and it retains the original GPLv2 license from there. Neither I nor Oracle is responsible for any damage it causes. See the LICENSE file for details.
