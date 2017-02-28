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

/**
 * Main activity, used for gathering the list of waypoints.
 */
public class Waypoints extends AppCompatActivity {
  static final String BUNDLE_ADAPTER = "org.tpmkranz.tsp.Waypoints.listAdapter";
  static final String BUNDLE_PATH = "org.tpmkranz.tsp.Waypoints.path";
  static final String BUNDLE_DISTANCES = "org.tpmkranz.tsp.Waypoints.distances";

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
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putSerializable(BUNDLE_ADAPTER, listAdapter);
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (waitForComputation != null) {
      waitForComputation.cancel();
    }
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

  /**
   * Shows or hides the {@link FloatingActionButton}s as necessary.
   *
   * @param adapter for deciding what states the buttons should be in
   * @param compute the button for starting the route calculation
   * @param add the button for adding a waypoint
   */
  protected static void showButtons(WaypointsAdapter adapter, FloatingActionButton compute, FloatingActionButton add) {
    int items = adapter.getItemCount();
    compute.setVisibility(items <= 2 ? View.GONE : View.VISIBLE);
    add.setVisibility(items <= WaypointsAdapter.MAXIMUM_WAYPOINTS ? View.VISIBLE : View.GONE);
    ((AppCompatActivity)compute.getContext()).invalidateOptionsMenu();
  }

  /**
   * Shows user tips depending on the current number of waypoints.
   *
   * @param adapter for deciding which tip to show
   */
  protected static void showHint(WaypointsAdapter adapter) {
    if (snackbar == null || !hints) {
      return;
    }
    CoordinatorLayout c = (CoordinatorLayout) ((AppCompatActivity)snackbar.getContext())
        .findViewById(R.id.activity_waypoints);
    switch (adapter.getItemCount()) {
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

  /**
   * Launches a {@link PlacePicker} for selecting a new waypoint.
   *
   * @param view the {@link FloatingActionButton} for adding a waypoint
   */
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

  /**
   * Retrieves the distance matrix for the current list of points and starts the calculation.
   *
   * @param view the {@link FloatingActionButton} for starting the calculation
   */
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

  /**
   * Makes the waypoint associated with the clicked {@link View} the new starting point or locks the
   * current one.
   *
   * @param view the {@link android.widget.Button} associated with the waypoint's list entry
   */
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

  /**
   * Adds or removes the waypoint associated with the clicked {@link View} to/from the quick access
   * list.
   *
   * @param view the {@link android.widget.Button} associated with the waypoint's list entry
   */
  public void makeFavorite(View view) {
    ViewGroup card = (ViewGroup) view.getParent().getParent().getParent();
    int index = listView.getChildAdapterPosition(card);
    listAdapter.makeFavorite(index);
  }

  /**
   * Awaits the distance matrix response requested in {@link #computeRoute(View)} and sets the
   * calculation in motion.
   */
  public static class DistanceResponseListener implements Response.Listener<JSONObject> {
    private ProgressDialog dialog;
    private WaypointsAdapter adapter;

    /**
     * Initializes the {@link Response.Listener} with the associated {@link ProgressDialog} and the
     * {@link WaypointsAdapter} for future use.
     *
     * @param dialog the dialog indicating that a network operation is pending
     * @param adapter the adapter that's going to be passed to the {@link BruteforceTask}
     */
    public DistanceResponseListener(ProgressDialog dialog, WaypointsAdapter adapter) {
      this.dialog = dialog;
      this.adapter = adapter;
    }


    /**
     * Tries to launch the calculation.
     *
     * @param response the received <a href="http://project-osrm.org/docs/v5.6.0/api/#table-service">
     *   distance matrix</a>
     */
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

  /**
   * Reacts to errors that may happen while retrieving the distance matrix.
   */
  public static class DistanceErrorListener implements Response.ErrorListener {
    private ProgressDialog dialog;

    /**
     * Initializes the {@link com.android.volley.Response.ErrorListener} with the associated
     * {@link ProgressDialog}.
     *
     * @param dialog the dialog indicating that a network operation is pending
     */
    public DistanceErrorListener(ProgressDialog dialog) {
      this.dialog = dialog;
    }

    /**
     * Displays the error message in the dialog.
     *
     * @param error the error object for the error that occurred
     */
    @Override
    public void onErrorResponse(VolleyError error) {
      dialog.setIndeterminate(false);
      dialog.setMax(0);
      dialog.setMessage(error.toString());
      Log.d("RESPONSE ERROR", error.toString());
    }
  }

  /**
   * Cancels the network request if the associated {@link ProgressDialog} gets cancelled.
   */
  public static class OnCancelCancelRequestListener implements DialogInterface.OnCancelListener {
    private Request request;

    /**
     * Initializes the {@link android.content.DialogInterface.OnCancelListener} with the
     * {@link Request} that would be cancelled.
     *
     * @param request the request to be cancelled on dialog cancellation
     */
    public OnCancelCancelRequestListener(Request request) {
      this.request = request;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
      request.cancel();
      Log.d("REQUEST CANCEL", "cancelled");
    }
  }

  /**
   * Keeps track of the set of quick access items the user wishes to add to add to the list.
   */
  public static class OnMultiChoiceFavoriteListener
      implements DialogInterface.OnMultiChoiceClickListener {
    private Set<Integer> choices;

    /**
     * Initializes the {@link DialogInterface.OnMultiChoiceClickListener} with the {@link Set} where
     * the checked items will be recorded.
     *
     * @param choices the set of checked items
     */
    public OnMultiChoiceFavoriteListener(Set<Integer> choices) {
      this.choices = choices;
    }

    /**
     * Adds or removes an item to/from the set of checked items.
     *
     * @param dialog where the list is being displayed
     * @param which the item to be added/removed
     * @param isChecked the current state of the item
     */
    @Override
    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
      if (isChecked) {
        choices.add(which);
      } else {
        choices.remove(which);
      }
    }
  }

  /**
   * Adds the checked quick access items to the list of waypoints when the 'add' button has been
   * clicked.
   */
  public static class OnClickAddFavoritesListener implements DialogInterface.OnClickListener {
    private final Set<Integer> choices;
    private final WaypointsAdapter adapter;
    private final FloatingActionButton add;
    private final FloatingActionButton compute;

    /**
     * Initializes the {@link android.content.DialogInterface.OnClickListener} with a reference to
     * the {@link Set} of checked items and the UI objects that could be affected by adding
     * waypoints.
     *
     * @param choices the set of checked items
     * @param adapter the {@link WaypointsAdapter} for adding the items to the list
     * @param add the {@link FloatingActionButton} associated with adding waypoints for future use
     * @param compute the {@link FloatingActionButton} associated with the calculation for future use
     */
    public OnClickAddFavoritesListener(Set<Integer> choices, WaypointsAdapter adapter,
        FloatingActionButton add, FloatingActionButton compute) {
      this.choices = choices;
      this.adapter = adapter;
      this.add = add;
      this.compute = compute;
    }

    /**
     * Adds the waypoints to the {@link WaypointsAdapter} and updates UI elements.
     *
     * @param dialog where the clicked button lives
     * @param which which button was clicked (in this case {@link DialogInterface#BUTTON_POSITIVE})
     */
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

  /**
   * Applies an {@link android.content.DialogInterface.OnClickListener} to the neutral button once
   * the dialog has been shown.
   *
   * <p>A hack for making a {@link AlertDialog} button non-dismissive, courtesy of
   * <a href="http://stackoverflow.com/a/7636468">Tom Bollwitt and Dmitry Ryadnenko</a>.
   */
  public static class OnShowSetOnClickInvertSelectionListener implements DialogInterface.OnShowListener {
    private final Set<Integer> choices;
    private final WaypointsAdapter adapter;

    /**
     * Initializes the {@link android.content.DialogInterface.OnShowListener} with a reference to
     * the {@link Set} of checked items.
     *
     * @param choices the set of checked items that'll be inverted
     * @param adapter the {@link WaypointsAdapter} for getting the complementary items
     */
    public OnShowSetOnClickInvertSelectionListener(Set<Integer> choices, WaypointsAdapter adapter) {
      this.choices = choices;
      this.adapter = adapter;
    }

    /**
     * Applies the {@link android.content.DialogInterface.OnClickListener} to the button.
     *
     * <p>The listener will invert the set of clicked items once triggered.
     *
     * @param dialog which dialog is being shown
     */
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
