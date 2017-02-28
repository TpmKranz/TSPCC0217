package org.tpmkranz.tsp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
  static final String BUNDLE_PATH = "org.tpmkranz.tsp.Waypoints.path";
  public static final String BUNDLE_DISTANCES = "org.tpmkranz.tsp.Waypoints.distances";

  private RecyclerView listView;
  private WaypointsAdapter listAdapter;
  private FloatingActionButton addFab;
  private FloatingActionButton computeFab;
  private ProgressDialog waitForPlacePicker;
  private ProgressDialog waitForComputation;
  private SharedPreferences prefs;
  private RequestQueue requestQueue;
  private static Snackbar snackbar;
  private static boolean hints = true;

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
        int index = 0;
        for (String f : favs) {
          SerializablePlace p = new SerializablePlace(f);
          items[index++] = p.getLabel();
        }
        Set<Integer> choices = new HashSet<>();
        builder.setMultiChoiceItems(items, null, new OnMultiChoiceFavoriteListener(choices));
        builder.setPositiveButton(R.string.waypoints_action_favorite_add,
            new OnClickAddFavoritesListener(choices, listAdapter, addFab, computeFab)
        );
        builder.setNeutralButton(R.string.waypoints_action_favorite_invert, null);
        AlertDialog d = builder.create();
        d.setOnShowListener(new OnShowSetOnClickInvertSelectionListener(choices, listAdapter));
        d.show();
        return true;
      case R.id.waypoints_action_hints:
        SharedPreferences.Editor e = prefs.edit();
        hints = !hints;
        e.putBoolean(getString(R.string.preferences_hints), hints);
        e.apply();
        item.setIcon(
            hints ?
                R.drawable.ic_info_white_48px :
                R.drawable.ic_no_info_white_48px
        ).setTitle(
            hints ?
                R.string.waypoints_action_hints_disable :
                R.string.waypoints_action_hints_enable
        );
        if (snackbar != null) {
          if (hints && !snackbar.isShown()) {
            showHint(listAdapter);
          } else if (!hints && snackbar.isShown()) {
            snackbar.dismiss();
          }
        }
        return true;
      case R.id.waypoints_action_empty:
        listAdapter.empty();
        showButtons(listAdapter, computeFab, addFab);
        showHint(listAdapter);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_waypoints, menu);
    menu.findItem(R.id.waypoints_action_hints)
        .setIcon(
            hints ?
                R.drawable.ic_info_white_48px :
                R.drawable.ic_no_info_white_48px
        ).setTitle(
            hints ?
                R.string.waypoints_action_hints_disable :
                R.string.waypoints_action_hints_enable
        );
    menu.findItem(R.id.waypoints_action_empty).setVisible(
        listAdapter.getItemCount() > (listAdapter.isOriginLocked() ? 1 : 0)
    );
    return true;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.activity_waypoints);

    this.prefs = getPreferences(Context.MODE_PRIVATE);
    this.computeFab = (FloatingActionButton) findViewById(R.id.waypoints_compute_fab);
    this.addFab = (FloatingActionButton) findViewById(R.id.waypoints_add_fab);
    setSupportActionBar((Toolbar) findViewById(R.id.waypoints_toolbar));
    this.listView = (RecyclerView) this.findViewById(R.id.waypoints_list);
    if (savedInstanceState != null) {
      this.listAdapter = (WaypointsAdapter) savedInstanceState.getSerializable(BUNDLE_ADAPTER);
    }
    if (this.listAdapter == null) {
      this.listAdapter = new WaypointsAdapter();
    }
    this.listAdapter.setSharedPreferences(prefs);
    this.listView.setAdapter(listAdapter);
    showButtons(this.listAdapter, this.computeFab, this.addFab);

    ItemTouchHelper.SimpleCallback callback = new SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
      @Override
      public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
          RecyclerView.ViewHolder target) {
        return false;
      }

      @Override
      public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        listAdapter.removeWaypoint(viewHolder.getAdapterPosition());
        showButtons(listAdapter, computeFab, addFab);
        showHint(listAdapter);
      }
    };
    (new ItemTouchHelper(callback)).attachToRecyclerView(this.listView);

    snackbar = Snackbar.make(listView, "", Snackbar.LENGTH_INDEFINITE);
    hints = prefs.getBoolean(getString(R.string.preferences_hints), true);
    showHint(listAdapter);

    this.waitForPlacePicker = new ProgressDialog(this);
    this.waitForPlacePicker.setIndeterminate(true);
    this.waitForPlacePicker.setCancelable(false);
    this.waitForPlacePicker.setMessage(getResources().getString(R.string.waypoint_wait_map));

    this.requestQueue = Volley.newRequestQueue(this);
  }

  static void showButtons(WaypointsAdapter a, FloatingActionButton cFab, FloatingActionButton aFab) {
    int items = a.getItemCount();
    cFab.setVisibility(items <= 2 ? View.GONE : View.VISIBLE);
    aFab.setVisibility(items <= WaypointsAdapter.MAXIMUM_WAYPOINTS ? View.VISIBLE : View.GONE);
    ((AppCompatActivity)cFab.getContext()).invalidateOptionsMenu();
  }

  static void showHint(WaypointsAdapter a) {
    if (snackbar == null || !hints) {
      return;
    }
    CoordinatorLayout c = (CoordinatorLayout) ((AppCompatActivity)snackbar.getContext())
        .findViewById(R.id.activity_waypoints);
    switch (a.getItemCount()) {
      case 0:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_start, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 1:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_two, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 2:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_one, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 3:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_more, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 4:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_remove, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 5:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_hints, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 6:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_origin, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 7:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_pin, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 8:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_favorite, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 9:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_label, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 10:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_placeholder, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 11:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_enough, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 12:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_manage, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      case 13:
        snackbar = Snackbar.make(c, R.string.waypoint_hint_warning, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        break;
      default:
        snackbar.dismiss();
        break;
    }
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
      listAdapter.addWaypoint(PlacePicker.getPlace(data, this));
      showButtons(listAdapter, computeFab, addFab);
      showHint(listAdapter);
    }
  }

  public void makeOrigin(View view) {
    ViewGroup card = (ViewGroup) view.getParent().getParent().getParent();
    int index = listView.getChildAdapterPosition(card);
    if (index == 0) {
      listAdapter.lockOrigin();
      invalidateOptionsMenu();
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
    String url = "https://router.project-osrm.org/table/v1/driving/" + listAdapter.toOsrmString();
    JsonObjectRequest r = new JsonObjectRequest(Method.POST, url, null,
        new DistanceResponseListener(waitForComputation, listAdapter),
        new DistanceErrorListener(waitForComputation));
    waitForComputation.setOnCancelListener(new OnCancelCancelRequestListener(r));
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
        JSONArray responseArray = response.getJSONArray("durations");
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

  public static class OnCancelCancelRequestListener implements DialogInterface.OnCancelListener {
    private Request request;

    public OnCancelCancelRequestListener(Request request) {
      this.request = request;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
      request.cancel();
      Log.d("REQUEST CANCEL", "cancelled");
    }
  }

  public static class OnMultiChoiceFavoriteListener
      implements DialogInterface.OnMultiChoiceClickListener {
    private Set<Integer> choices;

    public OnMultiChoiceFavoriteListener(Set<Integer> choices) {
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

  public static class OnClickAddFavoritesListener implements DialogInterface.OnClickListener {
    private final Set<Integer> choices;
    private final WaypointsAdapter adapter;
    private final FloatingActionButton add;
    private final FloatingActionButton compute;

    public OnClickAddFavoritesListener(Set<Integer> choices, WaypointsAdapter adapter,
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
        showButtons(adapter, compute, add);
        showHint(adapter);

        if (uninserted.size() > 0) {
          StringBuilder b = new StringBuilder();
          int index = 0;
          for (SerializablePlace p : uninserted) {
            b.append(p.getLabel());
            if (++index < uninserted.size()) {
              b.append(", ");
            }
          }
          Toast.makeText(compute.getContext(), compute.getContext().getString(
              uninserted.size() == 1 ?
                  R.string.waypoints_action_favorite_add_uninserted_s :
                  R.string.waypoints_action_favorite_add_uninserted_p,
              b.toString()
          ), Toast.LENGTH_LONG).show();
        }
      }
    }
  }

  public static class OnShowSetOnClickInvertSelectionListener implements DialogInterface.OnShowListener {

    private final Set<Integer> choices;
    private final WaypointsAdapter adapter;

    public OnShowSetOnClickInvertSelectionListener(Set<Integer> choices, WaypointsAdapter adapter) {
      this.choices = choices;
      this.adapter = adapter;
    }

    @Override
    public void onShow(final DialogInterface dialog) {
      ((AlertDialog)dialog).getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View v) {
              for (Integer i : choices) {
                ((AlertDialog) dialog).getListView().setItemChecked(i, false);
              }
              Set<Integer> invertedChoices = adapter.invertUnusedFavorites(choices);
              choices.clear();
              choices.addAll(invertedChoices);
              for (Integer i : choices) {
                ((AlertDialog) dialog).getListView().setItemChecked(i, true);
              }
            }
          }
      );
    }
  }
}
