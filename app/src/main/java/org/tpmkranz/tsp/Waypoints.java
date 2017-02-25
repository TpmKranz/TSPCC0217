package org.tpmkranz.tsp;

import static java.lang.Math.sqrt;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.location.places.ui.PlacePicker.IntentBuilder;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tpmkranz.tsp.WaypointsAdapter.SerializablePlace;

public class Waypoints extends AppCompatActivity {
  static final int[] distances = new int[]{
         0,  6193,  8246,   992,  1556,  5010,  4830,  2581,  4122,  5565,  2569,  1080,  4534,
      6218,     0, 13622,  6707,  7271, 10386, 10206,  7957,  8123,  1601,  4223,  5400,  6765,
      8489, 13800,     0,  7885,  6671,  4548,  3405,  5651,  8281, 13115, 10339,  8926, 12380,
      1613,  7163,  7254,     0,   564,  4018,  3838,  1589,  3130,  6535,  3539,  2050,  5504,
      1818,  7368,  6690,  1214,     0,  3454,  3274,  1025,  2566,  6740,  3744,  2255,  5709,
      5265, 10576,  4625,  4661,  3447,     0,  1209,  2427,  5057,  9891,  7115,  5702,  9156,
      5084, 10395,  3416,  4480,  3266,  1143,     0,  2246,  4876,  9710,  6934,  5521,  8975,
      2838,  8149,  5665,  2234,  1020,  2429,  2249,     0,  2630,  7464,  4688,  3275,  6729,
      4347,  8514,  8236,  3743,  2529,  5000,  4820,  2571,     0,  7829,  5053,  4784,  7698,
      5718,  1571, 13064,  6207,  6771,  9828,  9648,  7399,  7565,     0,  3850,  4963,  5567,
      2681,  4082, 10424,  3170,  3734,  7188,  7008,  4759,  4938,  3957,     0,  2008,  3851,
      1161,  5618,  8904,  1650,  2214,  5668,  5488,  3239,  4780,  4927,  2157,     0,  4796,
      4574,  6537, 12317,  5063,  5627,  9081,  8901,  6652,  7510,  5437,  3757,  3901,     0};
  static final int n = (Double.valueOf(sqrt(distances.length))).intValue()-1;
  static int f = 1;

  static final String BUNDLE_ADAPTER = "org.tpmkranz.tsp.Waypoints.listAdapter";

  private CardView originView;
  private RecyclerView listView;
  private WaypointsAdapter listAdapter;
  private FloatingActionButton addFab;
  private FloatingActionButton computeFab;
  private ProgressDialog waitForPlacePicker;
  private ProgressDialog waitForComputation;
  private SharedPreferences prefs;
  private RequestQueue requestQueue;
  private Toolbar toolbar;

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putSerializable(BUNDLE_ADAPTER, listAdapter);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.waypoints_action_favorite:
        AlertDialog.Builder builder = new Builder(this);
        SortedSet<String> favs = listAdapter.getUnusedFavorites();
        CharSequence[] items = new CharSequence[favs.size()];
        boolean[] checked = new boolean[favs.size()];
        int index = 0;
        for (String f : favs) {
          SerializablePlace p = new SerializablePlace(f);
          items[index++] = p.getLabel();
        }
        Set<Integer> choices = new HashSet<>();
        builder.setMultiChoiceItems(items, null, new OnMultiFavoriteClickListener(choices));
        builder.setNegativeButton(R.string.waypoints_action_favorite_cancel, null);
        builder.setPositiveButton(R.string.waypoints_action_favorite_add,
            new OnFavoriteAddClickListener(choices, listAdapter, addFab, computeFab));
        builder.create().show();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_waypoints, menu);
    return true;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.activity_waypoints);
    for (int i = 0; i <= n; i++) {
      if (i == 0) {
        f = 1;
      } else {
        f *= i;
      }
    }

    this.prefs = getPreferences(Context.MODE_PRIVATE);
    this.computeFab = (FloatingActionButton) findViewById(R.id.waypoints_compute_fab);
    this.addFab = (FloatingActionButton) findViewById(R.id.waypoints_add_fab);
    this.toolbar = (Toolbar) findViewById(R.id.waypoints_toolbar);
    this.originView = (CardView) findViewById(R.id.waypoints_origin);
    LayoutParams p = (LayoutParams) this.originView.getLayoutParams();
    p.addRule(RelativeLayout.BELOW, R.id.waypoints_toolbar);
    this.originView.setLayoutParams(p);
    setSupportActionBar(this.toolbar);
    this.listView = (RecyclerView) this.findViewById(R.id.waypoints_list);
    if (savedInstanceState != null) {
      this.listAdapter = (WaypointsAdapter) savedInstanceState.getSerializable(BUNDLE_ADAPTER);
    }
    if (this.listAdapter == null) {
      this.listAdapter = new WaypointsAdapter();
    }
    this.listAdapter.setOriginView(originView);
    this.listAdapter.setSharedPreferences(prefs);
    this.listView.setAdapter(listAdapter);
    listAdapter.redrawOrigin(true);
    if (listAdapter.getItemCount() <= 1) {
      computeFab.hide();
    }
    if (listAdapter.getItemCount() >= WaypointsAdapter.MAXIMUM_WAYPOINTS) {
      addFab.hide();
    }

    ItemTouchHelper.SimpleCallback callback = new SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
      @Override
      public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
          RecyclerView.ViewHolder target) {
        return false;
      }

      @Override
      public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        listAdapter.removeWaypoint(viewHolder.getAdapterPosition());
        if (listAdapter.getItemCount() <= 1 && computeFab.getVisibility() == View.VISIBLE) {
          computeFab.hide();
        }
        if (listAdapter.getItemCount() < WaypointsAdapter.MAXIMUM_WAYPOINTS
            && addFab.getVisibility() != View.VISIBLE) {
          addFab.show();
        }
      }
    };
    (new ItemTouchHelper(callback)).attachToRecyclerView(this.listView);

    this.waitForPlacePicker = new ProgressDialog(this);
    waitForPlacePicker.setIndeterminate(true);
    waitForPlacePicker.setCancelable(false);
    waitForPlacePicker.setMessage(getResources().getString(R.string.waypoint_wait_map));

    this.requestQueue = Volley.newRequestQueue(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  public void addWaypoint(View view) {
    PlacePicker.IntentBuilder builder = new IntentBuilder();
    try {
      startActivityForResult(builder.build(this), 0);
      waitForPlacePicker.show();
    } catch (GooglePlayServicesRepairableException e) {
      GoogleApiAvailability.getInstance().getErrorDialog(this, e.getConnectionStatusCode(), 0).show();
    } catch (GooglePlayServicesNotAvailableException e) {
      GoogleApiAvailability.getInstance().getErrorDialog(this, e.errorCode, 0).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (waitForPlacePicker.isShowing()) {
      waitForPlacePicker.dismiss();
    }
    if (resultCode == RESULT_OK) {
      this.listAdapter.addWaypoint(PlacePicker.getPlace(data, this));
      if (listAdapter.getItemCount() > 1 && computeFab.getVisibility() != View.VISIBLE) {
        computeFab.show();
      }
      if (listAdapter.getItemCount() >= WaypointsAdapter.MAXIMUM_WAYPOINTS
          && addFab.getVisibility() == View.VISIBLE) {
        addFab.hide();
      }
    }
  }

  public void makeOrigin(View view) {
    ViewGroup card = (ViewGroup) view.getParent().getParent().getParent();
    int index = listView.getChildAdapterPosition(card);
    if (index == RecyclerView.NO_POSITION) {
      if (listAdapter.isOriginLocked()) {
        listAdapter.unlockOrigin();
      } else {
        listAdapter.lockOrigin();
      }
    } else {
      listAdapter.makeOrigin(index + 1);
    }
  }

  public void computeRoute(View view) {
    if (listAdapter.getItemCount() < 2) {
      return;
    }
    if (waitForComputation != null) {
      waitForComputation.cancel();
    }
    waitForComputation = new ProgressDialog(this);
    waitForComputation.setMessage(getResources().getString(R.string.waypoint_wait_distances));
    waitForComputation.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    waitForComputation.setIndeterminate(true);
    String url = "http://localhost:5000/distances/";
    JsonObjectRequest r = new JsonObjectRequest(Method.POST, url, listAdapter.toJson(),
        new DistanceResponseListener(waitForComputation, listAdapter),
        new DistanceErrorListener(waitForComputation));
    waitForComputation.setOnCancelListener(new OnCancelRequestListener(r));
    waitForComputation.show();
    requestQueue.add(r);
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (waitForComputation != null) {
      waitForComputation.cancel();
    }
  }

  public void makeFavorite(View view) {
    ViewGroup card = (ViewGroup) view.getParent().getParent().getParent();
    int index = listView.getChildAdapterPosition(card) + 1;
    listAdapter.makeFavorite(index);
  }

  public static class DistanceResponseListener implements Response.Listener<JSONObject> {
    private ProgressDialog dialog;
    private WaypointsAdapter adapter;

    public DistanceResponseListener(ProgressDialog dialog, WaypointsAdapter adapter) {
      this.dialog = dialog;
      this.adapter = adapter;
    }


    @Override
    public void onResponse(JSONObject response) {
      try {
        JSONArray responseArray = response.getJSONArray("array");
        int[][] distances = new int[responseArray.length()][responseArray.length()];
        for (int i = 0; i < responseArray.length(); i++) {
          for (int j = 0; j < responseArray.length(); j++) {
            distances[i][j] = responseArray.getJSONArray(i).getInt(j);
          }
        }
        (new BruteforceTask(dialog, adapter, distances)).execute();
      } catch (JSONException | ArrayIndexOutOfBoundsException e) {
        dialog.setIndeterminate(false);
        dialog.setMessage(e.toString());
      }
      Log.d("RESPONSE OK", response.toString());
    }
  }

  public static class DistanceErrorListener implements Response.ErrorListener {
    private ProgressDialog dialog;

    public DistanceErrorListener(ProgressDialog dialog) {
      this.dialog = dialog;
    }

    @Override
    public void onErrorResponse(VolleyError error) {
      dialog.setIndeterminate(false);
      dialog.setMessage(error.toString());
      Log.d("RESPONSE ERROR", error.toString());
    }
  }

  public static class OnCancelRequestListener implements DialogInterface.OnCancelListener {
    private Request request;

    public OnCancelRequestListener(Request request) {
      this.request = request;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
      request.cancel();
      Log.d("REQUEST CANCEL", "cancelled");
    }
  }

  public static class OnMultiFavoriteClickListener
      implements DialogInterface.OnMultiChoiceClickListener {
    private Set<Integer> choices;

    public OnMultiFavoriteClickListener(Set<Integer> choices) {
      this.choices = choices;
    }

    @Override
    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
      if (isChecked) {
        choices.add(which);
      } else {
        choices.remove(which);
      }
    }
  }

  public static class OnFavoriteAddClickListener implements DialogInterface.OnClickListener {
    private final Set<Integer> choices;
    private final WaypointsAdapter adapter;
    private final FloatingActionButton add;
    private final FloatingActionButton compute;

    public OnFavoriteAddClickListener(Set<Integer> choices, WaypointsAdapter adapter,
        FloatingActionButton add, FloatingActionButton compute) {
      this.choices = choices;
      this.adapter = adapter;
      this.add = add;
      this.compute = compute;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (which == DialogInterface.BUTTON_POSITIVE) {
        Set<SerializablePlace> uninserted = adapter.addFavorites(choices);
        if (adapter.getItemCount() > 1) {
          compute.show();
        }
        if (adapter.getItemCount() >= WaypointsAdapter.MAXIMUM_WAYPOINTS) {
          add.hide();
        }

        StringBuilder b = new StringBuilder();
        int index = 0;
        for (SerializablePlace p : uninserted) {
          b.append(p.getLabel());
          if (++index < uninserted.size()) {
            b.append(", ");
          }
        }
        if (index == 1) {
          Toast.makeText(add.getContext(), String.format(add.getResources().getString(
              R.string.waypoints_action_favorite_add_uninserted_s), b.toString()),
              Toast.LENGTH_LONG)
              .show();
        } else if (index > 1) {
          Toast.makeText(add.getContext(), String.format(add.getResources().getString(
              R.string.waypoints_action_favorite_add_uninserted_p), b.toString()),
              Toast.LENGTH_LONG)
              .show();
        }
      }
    }
  }
}
