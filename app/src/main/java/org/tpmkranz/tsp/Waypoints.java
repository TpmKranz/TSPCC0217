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
  static final String BUNDLE_ADAPTER = "org.tpmkranz.tsp.Waypoints.listAdapter";

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

    this.prefs = getPreferences(Context.MODE_PRIVATE);
    this.computeFab = (FloatingActionButton) findViewById(R.id.waypoints_compute_fab);
    this.addFab = (FloatingActionButton) findViewById(R.id.waypoints_add_fab);
    this.toolbar = (Toolbar) findViewById(R.id.waypoints_toolbar);
    setSupportActionBar(this.toolbar);
    this.listView = (RecyclerView) this.findViewById(R.id.waypoints_list);
    if (savedInstanceState != null) {
      this.listAdapter = (WaypointsAdapter) savedInstanceState.getSerializable(BUNDLE_ADAPTER);
    }
    if (this.listAdapter == null) {
      this.listAdapter = new WaypointsAdapter();
    }
    this.listAdapter.setSharedPreferences(prefs);
    this.listView.setAdapter(listAdapter);
    if (listAdapter.getItemCount() <= 2) {
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
        if (listAdapter.getItemCount() <= 2) {
          computeFab.hide();
        }
        if (listAdapter.getItemCount() <= WaypointsAdapter.MAXIMUM_WAYPOINTS) {
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
      if (listAdapter.getItemCount() > 2) {
        computeFab.show();
      }
      if (listAdapter.getItemCount() > WaypointsAdapter.MAXIMUM_WAYPOINTS) {
        addFab.hide();
      }
    }
  }

  public void makeOrigin(View view) {
    ViewGroup card = (ViewGroup) view.getParent().getParent().getParent();
    int index = listView.getChildAdapterPosition(card);
    if (index == 0) {
      listAdapter.lockOrigin();
    } else {
      listAdapter.makeOrigin(index);
    }
  }

  public void computeRoute(View view) {
    if (listAdapter.getItemCount() <= 2) {
      return;
    }
    if (waitForComputation != null) {
      waitForComputation.cancel();
    }
    waitForComputation = new ProgressDialog(this);
    waitForComputation.setTitle(getResources().getString(R.string.waypoint_wait_title));
    waitForComputation.setMessage(getResources().getString(R.string.waypoint_wait_distances));
    waitForComputation.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    waitForComputation.setIndeterminate(true);
    waitForComputation.setMax(1);
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
    int index = listView.getChildAdapterPosition(card);
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
        dialog.setMax(0);
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
      dialog.setMax(0);
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
        if (adapter.getItemCount() > 2) {
          compute.show();
        }
        if (adapter.getItemCount() > WaypointsAdapter.MAXIMUM_WAYPOINTS) {
          add.hide();
        }

        if (uninserted.size() > 0) {
          StringBuilder b = new StringBuilder();
          int index = 0;
          for (SerializablePlace p : uninserted) {
            b.append(p.getLabel());
            if (++index < uninserted.size()) {
              b.append(", ");
            }
          }
          Toast.makeText(add.getContext(),
              String.format(add.getResources().getString(
                  index == 1 ?
                      R.string.waypoints_action_favorite_add_uninserted_s :
                      R.string.waypoints_action_favorite_add_uninserted_p
              ), b.toString()),
              Toast.LENGTH_LONG
          ).show();
        }
      }
    }
  }
}
