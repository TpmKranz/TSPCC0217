package org.tpmkranz.tsp;

import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.Serializable;
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
      int pIndex;
      for (pIndex = 0; pIndex < path.length; pIndex++) {
        if (path[pIndex] == index) {
          break;
        }
      }
      if (pIndex == path.length) {
        pIndex = 0;
      } else {
        pIndex++;
      }
      point.setCardBackgroundColor(
          point.getResources().getColor(
              index == 0 ?
                  R.color.colorAccentLight :
                  R.color.cardview_light_background
          )
      );
      label.setText(points[pIndex].getLabel());
      details.setText(points[pIndex].getLabel());
      distance.setText(
          distance.getResources().getString(
              R.string.solvedpoints_distance,
              (distances[pIndex][(pIndex+1)%points.length] + 59) / 60
          )
      );
    }
  }
}
