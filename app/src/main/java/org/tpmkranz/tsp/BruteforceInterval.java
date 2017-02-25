package org.tpmkranz.tsp;

import java.util.concurrent.Callable;


public class BruteforceInterval implements Callable<byte[]> {

  private final int start;
  private final int end;
  private final int[] distances;

  public BruteforceInterval(int start, int end, int[] distances){
    this.start = start;
    this.end = end;
    this.distances = distances;
  }

  @Override
  public byte[] call() throws Exception {
    return bruteforce(start, end, distances);
  }

  public native byte[] bruteforce(int start, int end, int[] distances);
  static {
    System.loadLibrary("nativetsp");
  }
}
