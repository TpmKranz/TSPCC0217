package org.tpmkranz.tsp;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BruteforceTask extends AsyncTask<Void, Integer, byte[]> {

  private final ProgressDialog progressDialog;
  private final ExecutorService pool;
  private final BruteforceInterval[] forces;
  private final int numberOfForces;
  private final WaypointsAdapter listAdapter;
  private final int[] distances;
  private final int n;
  private final int[] factorials;

  public BruteforceTask(ProgressDialog progress, WaypointsAdapter adapter, int[][] distances) {
    this.progressDialog = progress;
    this.listAdapter = adapter;
    this.distances = new int[distances.length*distances.length];
    for (int i = 0; i < distances.length; i++) {
      System.arraycopy(distances[i], 0, this.distances, i * distances.length, distances.length);
    }
    this.n = distances.length-1;
    this.factorials = new int[distances.length];
    this.factorials[0] = 1;
    for (int i = 1; i < factorials.length; i++) {
      factorials[i] = factorials[i-1]*i;
    }
    Log.d("CORE NUMBER", String.valueOf(Runtime.getRuntime().availableProcessors()));
    this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    this.numberOfForces = (n > 8 ? factorials[n] / factorials[8] : 2);
    this.forces = new BruteforceInterval[numberOfForces];
    for (int i = 0; i < numberOfForces; i++) {
      this.forces[i] = new BruteforceInterval(
          i*(factorials[n]/numberOfForces)+1,
          (i+1)*(factorials[n]/numberOfForces),
          this.distances
      );
    }
  }

  @Override
  protected void onPreExecute() {
    progressDialog.setMax(factorials[n]);
    progressDialog.setProgress(0);
    progressDialog.setMessage(progressDialog.getContext().getResources().getText(R.string.waypoint_wait_computation));
    progressDialog.setOnCancelListener(new OnCancelStopAsyncTaskListener(this));
  }

  @Override
  protected byte[] doInBackground(Void... params) {
    Future<byte[]>[] futures = new Future[numberOfForces];
    int minimalDistance = Integer.MAX_VALUE;
    byte[] minimalRoute = null;
    for (int i = 0; i < numberOfForces; i++) {
      futures[i] = pool.submit(forces[i]);
    }
    try {
      for (int i = 0; i < numberOfForces; i++) {
        if (isCancelled()) {
          throw new InterruptedException();
        }
        byte[] result = futures[i].get();
        if (distance(result) < minimalDistance) {
          minimalRoute = result;
          minimalDistance = distance(result);
          Log.d("MINIMAL ROUTE", Arrays.toString(minimalRoute) + " " + minimalDistance);
        }
        publishProgress(i + 1);
      }
      this.pool.shutdownNow();
      return minimalRoute;
    } catch (InterruptedException | ExecutionException e) {
      this.pool.shutdownNow();
      return null;
    }
  }

  @Override
  protected void onProgressUpdate(Integer... finishedForces) {
    progressDialog.setIndeterminate(false);
    progressDialog.setProgress(finishedForces[0]*(factorials[n]/numberOfForces));
  }

  @Override
  protected void onCancelled(byte[] bytes) {
    progressDialog.dismiss();
  }

  @Override
  protected void onPostExecute(byte[] shortestRoute) {
    progressDialog.dismiss();
    Intent intent = new Intent(progressDialog.getContext(), SolvedWaypoints.class);
    intent.putExtra(Waypoints.BUNDLE_ADAPTER, listAdapter);
    intent.putExtra(Waypoints.BUNDLE_PATH, shortestRoute);
    intent.putExtra(Waypoints.BUNDLE_DISTANCES, distances);
    progressDialog.getContext().startActivity(intent);
  }

  int distance(byte[] route) {
    int distance = distances[route[0]];
    for (byte i = 0; i < n - 1; i++) {
      byte current = route[i];
      byte next = route[i+1];
      distance += distances[current*(n+1)+next];
    }
    distance += distances[route[n-1]*(n+1)];
    return distance;
  }

  public static class OnCancelStopAsyncTaskListener implements DialogInterface.OnCancelListener {

    private AsyncTask<?, ?, ?> task;

    public OnCancelStopAsyncTaskListener(AsyncTask<?,?,?> task) {
      this.task = task;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
      if (!task.isCancelled()) {
        task.cancel(true);
      }
    }
  }
}
