# heap-dump
A working version of jmap -F

# Before Using this Tool
Try running `jmap` without the `-F` option again. You can also increase the time that it will wait to connect, by specifying the option `-J-Dsun.tools.attach.attachTimeout=<milliseconds>` with a longer timeout. The default is only 5 seconds, and sometimes it takes longer than that for the VM to respond.

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

# Download
You can download the current version from the [releases](https://github.com/geoff-addepar/heap-dump/releases) page.

# Usage
First you need a core file. You can get that using:
```
gcore <pid>
```
You may need `sudo` depending on your [ptrace_scope](http://askubuntu.com/questions/41629/after-upgrade-gdb-wont-attach-to-process) settings.

To turn a core file into a heap dump:
```
java -cp heap_dump.jar:/usr/lib/jvm/java-8-oracle/lib/sa-jdi.jar \
  com.addepar.heapdump.HeapDumper -f <output.hprof> /usr/bin/java <corefile>
```
Like `jmap`, `jstack` and the other Serviceability Agent tools, you must run this against the same version of java that was used to generate the core file. 

# Status
This is a two-day hack. It has not been extensively tested.

# License
I copied most of the code directly out of the JDK, and it retains the original GPLv2 license from there. Neither I nor Oracle is responsible for any damage it causes. See the LICENSE file for details.
