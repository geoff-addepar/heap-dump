package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.Inferior;
import com.addepar.heapdump.inspect.inferior.SelfInferior;
import com.addepar.heapdump.inspect.struct.Klass;
import com.addepar.heapdump.inspect.struct.oopDesc;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public final class StatisticalHeapInspector {
  /**
   * 2000 samples is:
   * 99% confident that the numbers are within +/- 2.9%
   * 95% confident that the numbers are within +/- 2.2%
   */
  private static final int SAMPLES = 2000;

  /**
   * We don't show anything that occupies less than 2% of the heap, because numbers like 1% +/- 3% are meaningless
   */
  private static final int MIN_SIGNIFICANT_SAMPLES = 40;

  /**
   * We need at least this many hits to tell the user that their numbers are ok
   * 99% confidence that the numbers are within 3% requires 1849 samples on a large population
   */
  private static final int MIN_TOTAL_HITS = 1849;

  private final PrintWriter out;
  private final Hotspot hotspot;
  private final HotspotHeap heap;
  private final Random random;

  private StatisticalHeapInspector(PrintWriter out, Hotspot hotspot) {
    this.out = out;
    this.hotspot = hotspot;
    this.heap = hotspot.getHeap();
    this.random = new Random();
  }

  private long getGcRunCount() {
    long gcRuns = 0;
    for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      gcRuns += gcBean.getCollectionCount();
    }
    return gcRuns;
  }

  private void run() {
    long startGcRuns = getGcRunCount();
    Graph graph = new Graph();
    List<AddressRange> liveRegions = heap.collectLiveRegions();
    OopFinder finder = new OopFinder(hotspot);

    long totalSize = 0L;

    for (AddressRange liveRegion : liveRegions) {
      long bottom = liveRegion.getStart();
      long top = liveRegion.getEnd();
      totalSize += top - bottom;
    }

    long startTime = System.currentTimeMillis();

    int totalHits = 0;
    for (int i = 0; i < SAMPLES; i++) {

      long randomOffset = randomLong(totalSize);

      for (AddressRange liveRegion : liveRegions) {
        long bottom = liveRegion.getStart();
        long top = liveRegion.getEnd();
        long size = top - bottom;
        if (size > randomOffset) {
          if (finder.probeForObject(bottom + randomOffset, bottom)) {
            addToGraph(graph, finder.getProbedObject(), finder.getProbedKlass());
            totalHits++;
          }
          break;
        } else {
          randomOffset -= size;
        }
      }
    }

    long endTime = System.currentTimeMillis();
    long endGcRuns = getGcRunCount();

    write(graph, totalSize, totalHits, endTime - startTime, endGcRuns - startGcRuns);
  }

  private void addToGraph(Graph graph, oopDesc object, Klass klass) {
    Node node = graph.nodes.get(klass.getAddress());
    if (node == null) {
      node = new Node();
      node.klassName = klass.getName(hotspot);
      graph.nodes.put(klass.getAddress(), node);
    }
    node.hits++;
    node.size += object.getObjectSize(hotspot, klass);
  }

  // This is terrible as far as random number generators go.... given that there's a 48-bit LCG
  // underlying it all, I'm not sure if this is remotely sound, even for small bounds.
  // The algorithm is more or less copied from java.util.Random.nextInt(bound)
  private long randomLong(long bound) {
    long r = random.nextLong();
    long m = bound - 1;
    long u = r;
    while (u - (r = u % bound) + m < 0) {
      u = random.nextLong();
    }
    return r;
  }

  private void write(Graph graph, long totalHeapSize, int totalHits, long millis, long gcRuns) {
    out.println();
    out.println();
    out.println("Live heap:     " + totalHeapSize);
    out.println("Total samples: " + SAMPLES);
    out.println("Total hits:    " + totalHits);
    out.println("Runtime:       " + millis + " ms");
    out.println("# GC Runs:     " + gcRuns);
    out.println();
    out.println("Hits | % of heap | Estimated Total Size | Estimated Number | Class");
    out.println("------------------------------------------------------------------");
    List<Node> sortedNodes = new ArrayList<>(graph.nodes.values());
    Collections.sort(sortedNodes, Comparator.comparing((Node node) -> node.hits).reversed());
    for (Node node : sortedNodes) {
      // ignore low fidelity stuff
      if (node.hits <= MIN_SIGNIFICANT_SAMPLES) {
        break;
      }
      String className = node.klassName;
      double estimatedPercent = (double) node.hits / (double) totalHits * 100.0;
      double sizeOfObject = (double) node.size / (double) node.hits;
      double estimatedSize = ((double) node.hits * (double) totalHeapSize) / (double) totalHits;
      double estimatedNumber = estimatedSize / sizeOfObject;
      out.format("%4d | %8.1f%% | %20.0f | %16.0f | %s\n",
          node.hits, estimatedPercent, estimatedSize, estimatedNumber, className);
    }
    out.println();
    if (totalHits > MIN_TOTAL_HITS) {
      out.println("'% of heap' measurements are within +/- 3%, at the 99% confidence level");
    } else {
      out.println("THERE WAS SIGNIFICANT DATA LOSS, NUMBERS MAY BE INACCURATE");
    }
    out.flush();
  }

  public static void main(String args[]) throws IOException {
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    PrintWriter out = new PrintWriter(System.out);

    Inferior inferior = new SelfInferior();
    Hotspot hotspot = new Hotspot(inferior);
    StatisticalHeapInspector dumper = new StatisticalHeapInspector(out, hotspot);
    dumper.run();
  }

  private class Node {
    String klassName;
    long hits;
    long size;
  }

  private class Graph {
    Map<Long, Node> nodes = new HashMap<>();
  }
}
