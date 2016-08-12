package com.addepar.heapdump;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.JVMDebugger;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.gc_interface.CollectedHeap;
import sun.jvm.hotspot.memory.CompactibleFreeListSpace;
import sun.jvm.hotspot.memory.ConcurrentMarkSweepGeneration;
import sun.jvm.hotspot.memory.GenCollectedHeap;
import sun.jvm.hotspot.memory.Generation;
import sun.jvm.hotspot.memory.SymbolTable;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public class StatisticalHeapDumper extends Tool {
  private static final int SAMPLES = 10000;

  private JVMDebugger dbg;
  private FastObjectHeap objectHeap;
  private SymbolTable symTbl;
  private Random random;
  private int heapWordSize;

  private int misses;

  void init() {
    random = new Random();
    VM vm = VM.getVM();
    heapWordSize = VM.getVM().getHeapWordSize();
    dbg = vm.getDebugger();

    objectHeap = new FastObjectHeap(vm.getTypeDataBase(), vm.getSymbolTable());
    try {
      java.lang.reflect.Field field = VM.class.getDeclaredField("heap");
      field.setAccessible(true);
      field.set(vm, objectHeap);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    symTbl = vm.getSymbolTable();


  }

  @Override
  public void run() {
    init();

    Graph graph = new Graph();
    List<Address> liveRegions = objectHeap.collectLiveRegions();

    long totalSize = 0L;

    for (int space = 0; space < liveRegions.size(); space += 2) {
      Address bottom = liveRegions.get(space);
      Address top = liveRegions.get(space + 1);
      totalSize += top.minus(bottom);
    }

    CompactibleFreeListSpace cmsSpaceOld = null;
    CollectedHeap heap = VM.getVM().getUniverse().heap();
    if (heap instanceof GenCollectedHeap) {
      GenCollectedHeap genCollectedHeap = (GenCollectedHeap) heap;
      Generation lastGen = genCollectedHeap.getGen(1);
      if (lastGen instanceof ConcurrentMarkSweepGeneration) {
        ConcurrentMarkSweepGeneration cmsGen = (ConcurrentMarkSweepGeneration) lastGen;
        cmsSpaceOld = cmsGen.cmsSpace();
      }
    }

    int totalHits = 0;
    for (int i = 0; i < SAMPLES; i++) {

      long randomOffset = randomLong(totalSize);

      for(int space = 0; space < liveRegions.size(); space += 2) {
        Address bottom = liveRegions.get(space);
        Address top = liveRegions.get(space + 1);
        long size = top.minus(bottom);
        if (size > randomOffset) {
          Oop probedObject = probeForObject(bottom.addOffsetTo(randomOffset), bottom);
          if (probedObject != null) {
            addToGraph(graph, probedObject);
            totalHits++;
          }
          break;
        } else {
          randomOffset -= size;
        }
      }
    }
    write(graph, totalSize, totalHits);
  }

  /**
   * Walk backwards to find the start of the object at probeAddress, but don't walk past the bottom
   * of the live region.
   */
  private Oop probeForObject(Address probeAddress, Address bottom) {
    OopHandle cur = probeAddress.andWithMask(~(heapWordSize - 1)).addOffsetToAsOopHandle(0);
    while (cur.greaterThanOrEqual(bottom)) {
      Oop oop = objectHeap.newOopIfPossible(cur, bottom);
      if (oop != null) {
        if (cur.addOffsetToAsOopHandle(oop.getObjectSize()).greaterThan(probeAddress)) {
          return oop; // original address was within the nearest object
        } else {
          // declare this a "miss" even though we might not have walked far enough, and we just
          // stumbled on some spurious data that looked like an Oop header, but the size is busted
          return null;
        }
      }
      cur = cur.addOffsetToAsOopHandle(-heapWordSize);
    }
    return null; // not found
  }

  private void addToGraph(Graph graph, Oop object) {
    Klass klass = object.getKlass();
    Node node = graph.nodes.get(klass.getAddress());
    if (node == null) {
      node = new Node();
      node.klass = klass;
      graph.nodes.put(klass.getAddress(), node);
    }
    node.hits++;
    node.size += object.getObjectSize();
  }

  // This is terrible as far as random number generators go.... given that there's a 48-bit LCG
  // underlying it all, I'm not sure if this is remotely sound, even for small bounds
  // more or less copied from java.util.Random.nextInt(bound)
  private long randomLong(long bound) {
    long r = random.nextLong();
    long m = bound - 1;
    long u = r;
    while (u - (r = u % bound) + m < 0) {
      u = random.nextLong();
    }
    return r;
  }

  private void write(Graph graph, long totalHeapSize, int totalHits) {
    System.out.println();
    System.out.println();
    System.out.println("Live heap:     " + totalHeapSize);
    System.out.println("Total samples: " + SAMPLES);
    System.out.println("Total hits:    " + totalHits);
    System.out.println();
    System.out.println("Estimated Number | Estimated Total Size | Class");
    System.out.println("-----------------------------------------------");
    List<Node> sortedNodes = new ArrayList<Node>(graph.nodes.values());
    Collections.sort(sortedNodes, new Comparator<Node>() {
      @Override
      public int compare(Node o1, Node o2) {
        return Long.signum(o2.hits - o1.hits);
      }
    });
    for (Node node : sortedNodes) {
      // ignore low fidelity stuff
      if (node.hits <= 2) {
        break;
      }
      String className = node.klass.getName().asString();
      double sizeOfObject = (double) node.size / (double) node.hits;
      double estimatedSize = ((double) node.hits * (double) totalHeapSize) / (double) SAMPLES;
      double estimatedNumber = estimatedSize / sizeOfObject;
      System.out.format("%16.0f | %20.0f | %s\n", estimatedNumber, estimatedSize, className);
    }
  }

  public static void main(String args[]) {
    StatisticalHeapDumper dumper = new StatisticalHeapDumper();
    dumper.execute(args);
  }

  private class Node {
    Klass klass;
    long hits;
    long size;
  }

  // Represents an outgoing reference
  private class Edge {
    Address destKlass;
    long hits;
  }

  private class Graph {
    Map<Address, Node> nodes = new HashMap<Address, Node>();
    Map<Node, Edge> outgoingEdges = new HashMap<Node, Edge>();
  }
}
