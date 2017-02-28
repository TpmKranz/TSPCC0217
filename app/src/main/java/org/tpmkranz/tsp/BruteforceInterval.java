package org.tpmkranz.tsp;

import java.util.concurrent.Callable;

/**
 * Task that measures and compares all the paths in a given interval.
 */
public class BruteforceInterval implements Callable<byte[]> {

  private final int start;
  private final int end;
  private final int[] distances;

  /**
   * Initializes the task with its search interval boundaries and the distance matrix to use.
   *
   * @param start number of the first path to measure
   * @param end number of the last path to measure
   * @param distances the flattened distance matrix
   */
  public BruteforceInterval(int start, int end, int[] distances){
    this.start = start;
    this.end = end;
    this.distances = distances;
  }

  @Override
  public byte[] call() throws Exception {
    return bruteforce(start, end, distances);
  }

  /**
   * Measures and compares every path in the given range.
   *
   * <p>The core of the application
   *
   * @param start number of the first path to check
   * @param end number of the first path to check
   * @param distances the flattened distance matrix
   * @return an array containing the optimal order of waypoints in the given range
   */
  public native byte[] bruteforce(int start, int end, int[] distances);
  static {
    System.loadLibrary("nativetsp");
  }
}
