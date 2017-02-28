package org.tpmkranz.tsp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;

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
    listView = (RecyclerView) findViewById(R.id.activity_solved_waypoints);
    listView.setAdapter(listAdapter);
  }

}
