package org.tpmkranz.tsp;

import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.Serializable;
import java.util.Locale;
import org.tpmkranz.tsp.WaypointsAdapter.SerializablePlace;

/**
 * Maintains the list of waypoints and their ideal order.
 */
public class SolvedPointsAdapter
    extends RecyclerView.Adapter<SolvedPointsAdapter.ViewHolder>
    implements Serializable {

  private final SerializablePlace[] points;
  private final byte[] path;
  private final int[][] distances;

  /**
   * Initializes the adapter with all the data it needs.
   *
   * @param original the original {@link WaypointsAdapter} from which to take waypoint information
   * @param path the calculated shortest path
   * @param distances the flattened distance matrix
   */
  public SolvedPointsAdapter(WaypointsAdapter original, byte[] path, int[] distances) {
    this.path = path;
    this.distances = new int[path.length + 1][path.length + 1];
    for (int i = 0; i < path.length + 1; i++) {
      System.arraycopy(distances, i * (path.length + 1), this.distances[i], 0, path.length + 1);
    }
    this.points = original.points.toArray(new SerializablePlace[original.points.size()]);
  }

  /**
   * Returns the distance covered by the ideal path.
   *
   * @return the sum of distances between the waypoints of the path
   */
  public int distance() {
    int[] distances = new int[this.distances.length*this.distances.length];
    for (int i = 0; i < this.distances.length; i++) {
      System.arraycopy(this.distances[i], 0, distances, i * this.distances.length, this.distances.length);
    }
    return BruteforceTask.distance(distances, path);
  }

  /**
   * Creates a URL that points to the <a href="http://project-osrm.org/">Project OSRM</a> demo
   * routing website.
   *
   * @return a URL to a site that displays the ideal route
   */
  public String toOsrmRouteUrl() {
    if (points.length < 1) {
      return "";
    }

    StringBuilder b = new StringBuilder("http://map.project-osrm.org/?z=1");
    SerializablePlace o = points[0];
    b.append(String.format(
        Locale.US,
        "&center=%f%%2C%f&loc=%f%%2C%f",
        o.getLatitude(), o.getLongitude(),
        o.getLatitude(), o.getLongitude()
    ));
    for (byte i : path) {
      SerializablePlace p = points[i];
      b.append(String.format(
          Locale.US,
          "&loc=%f%%2C%f", p.getLatitude(), p.getLongitude()
      ));
    }
    b.append(String.format(
        Locale.US,
        "&loc=%f%%2C%f", o.getLatitude(), o.getLongitude()
    ));
    b.append("&hl=en&alt=0");

    return b.toString();
  }


  @Override
  public SolvedPointsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    ViewGroup l = (ViewGroup) LayoutInflater
        .from(parent.getContext())
        .inflate(R.layout.view_solvedpoint, parent, false);
    return new ViewHolder(l);
  }

  @Override
  public void onBindViewHolder(SolvedPointsAdapter.ViewHolder holder, int position) {
    holder.setData(position, points, path, distances);
  }

  @Override
  public int getItemCount() {
    return Integer.MAX_VALUE;
  }

  /**
   * A {@link RecyclerView.ViewHolder} for a waypoint and a distance estimate to its successor.
   */
  public static class ViewHolder extends RecyclerView.ViewHolder {

    CardView point;
    TextView label;
    TextView details;
    TextView distance;

    /**
     * Saves references to all the {@link View}s that are used to display data.
     *
     * @param itemView the {@link ViewGroup} holding the waypoint and distance displays
     */
    public ViewHolder(View itemView) {
      super(itemView);
      point = (CardView) itemView.findViewById(R.id.solved_point_card);
      label = (TextView) itemView.findViewById(R.id.solved_point_label);
      details = (TextView) itemView.findViewById(R.id.solved_point_details);
      distance = (TextView) itemView.findViewById(R.id.solved_point_distance);
    }

    /**
     * Fills a view with the necessary information.
     *
     * @param index the list index of the current view
     * @param points an array of the waypoints in original order
     * @param path the ideal path
     * @param distances the distance matrix
     */
    public void setData(int index, SerializablePlace[] points, byte[] path, int[][] distances) {
      index = index % points.length;
      int pIndex = (index == 0) ? 0 : path[index-1];
      int sIndex = ((index + 1) % points.length == 0) ? 0 : path[((index + 1) % points.length) - 1];
      point.setCardBackgroundColor(
          point.getResources().getColor(
              index == 0 ?
                  R.color.colorAccentLight :
                  R.color.cardview_light_background
          )
      );
      label.setText(points[pIndex].getLabel());
      details.setText(points[pIndex].getDetails());
      int minutes = (distances[pIndex][(sIndex)%points.length] + 30) / 60;
      distance.setText(
          distance.getResources().getString(
              minutes == 0 ?
                  R.string.solvedpoints_distance_z :
                  (minutes == 1 ?
                      R.string.solvedpoints_distance_s :
                      R.string.solvedpoints_distance_p),
              minutes == 0 ? 1 : minutes
          )
      );
    }
  }
}
