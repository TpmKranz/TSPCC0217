package org.tpmkranz.tsp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * The activity presenting the ideal route.
 */
public class SolvedWaypoints extends AppCompatActivity {

  final static String BUNDLE_ADAPTER = "org.tpmkranz.tsp.SolvedWaypoints.listAdapter";

  private RecyclerView listView;
  private SolvedPointsAdapter listAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_solved_waypoints);
    if (savedInstanceState != null) {
      listAdapter = (SolvedPointsAdapter) savedInstanceState.getSerializable(BUNDLE_ADAPTER);
    }
    if (listAdapter == null) {
      if (getIntent() != null
          && getIntent().hasExtra(Waypoints.BUNDLE_ADAPTER)
          && getIntent().hasExtra(Waypoints.BUNDLE_PATH)
          && getIntent().hasExtra(Waypoints.BUNDLE_DISTANCES)) {
        listAdapter = new SolvedPointsAdapter(
            (WaypointsAdapter) getIntent().getSerializableExtra(Waypoints.BUNDLE_ADAPTER),
            getIntent().getByteArrayExtra(Waypoints.BUNDLE_PATH),
            getIntent().getIntArrayExtra(Waypoints.BUNDLE_DISTANCES)
        );
      } else {
        finish();
        return;
      }
    }
    listView = (RecyclerView) findViewById(R.id.solved_waypoints_list);
    listView.setAdapter(listAdapter);
    Snackbar.make(
        listView,
        getString(R.string.solved_route, (listAdapter.distance() + 30) / 60),
        Snackbar.LENGTH_INDEFINITE)
        .setAction(R.string.solved_route_open, new OnClickOpenInBrowserListener(listAdapter.toOsrmRouteUrl()))
        .show();
  }


  /**
   * Launches an {@link Intent} for opening a URL.
   */
  public static class OnClickOpenInBrowserListener implements OnClickListener {
    private final String url;

    /**
     * Initializes the URL to be opened.
     *
     * @param url the URL that should be opened on clicking
     */
    public OnClickOpenInBrowserListener(String url) {
      this.url = url;
    }

    @Override
    public void onClick(View v) {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse(url));
      v.getContext().startActivity(intent);
    }
  }
}
