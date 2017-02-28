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

public class SolvedPointsAdapter
    extends RecyclerView.Adapter<SolvedPointsAdapter.ViewHolder>
    implements Serializable {

  private final SerializablePlace[] points;
  private final byte[] path;
  private final int[][] distances;

  public SolvedPointsAdapter(WaypointsAdapter original, byte[] path, int[] distances) {
    this.path = path;
    this.distances = new int[path.length + 1][path.length + 1];
    for (int i = 0; i < path.length + 1; i++) {
      System.arraycopy(distances, i * (path.length + 1), this.distances[i], 0, path.length + 1);
    }
    this.points = original.points.toArray(new SerializablePlace[original.points.size()]);
  }

  public int distance() {
    int[] distances = new int[this.distances.length*this.distances.length];
    for (int i = 0; i < this.distances.length; i++) {
      System.arraycopy(this.distances[i], 0, distances, i * this.distances.length, this.distances.length);
    }
    return BruteforceTask.distance(distances, path);
  }

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

  public static class ViewHolder extends RecyclerView.ViewHolder {

    CardView point;
    TextView label;
    TextView details;
    TextView distance;

    public ViewHolder(View itemView) {
      super(itemView);
      point = (CardView) itemView.findViewById(R.id.solved_point_card);
      label = (TextView) itemView.findViewById(R.id.solved_point_label);
      details = (TextView) itemView.findViewById(R.id.solved_point_details);
      distance = (TextView) itemView.findViewById(R.id.solved_point_distance);
    }

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
