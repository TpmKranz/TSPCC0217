package org.tpmkranz.tsp;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.gms.location.places.Place;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Maintains the list of waypoints and offers access to all kinds of data about them.
 */
public class WaypointsAdapter
    extends RecyclerView.Adapter<WaypointsAdapter.ViewHolder>
    implements Serializable {

  /** The JSON key for the starting point. */
  public static final String JSON_ORIGIN = "origin";
  /** The JSON key for the destinations array. */
  public static final String JSON_DESTINATIONS = "destinations";
  /** The JSON key for latitude attributes. */
  public static final String JSON_LATITUDE = "latitude";
  /** The JSON key for longitude attributes. */
  public static final String JSON_LONGITUDE = "longitude";
  /** The JSON key for label attributes. */
  public static final String JSON_LABEL = "label";
  /** The JSON key for details attributes. */
  public static final String JSON_DETAILS = "details";
  /** The JSON key for attributions attributes. */
  public static final String JSON_ATTRIBUTIONS = "attributions";

  static final int MAXIMUM_WAYPOINTS = 12;
  static final String PREFS_ORIGIN = "origin";
  static final String PREFS_FAVORITES = "favorites";

  List<SerializablePlace> points;

  private SharedPreferences sharedPreferences;
  private SortedSet<String> unusedFavorites;

  /**
   * Initializes the adapter with an empty {@link ArrayList}.
   */
  public WaypointsAdapter() {
    if (this.points == null) {
      this.points = new ArrayList<>(MAXIMUM_WAYPOINTS + 1);
    }
  }

  @Override
  public WaypointsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    CardView l = (CardView) LayoutInflater
        .from(parent.getContext())
        .inflate(R.layout.view_waypoint, parent, false);
    return new ViewHolder(l);
  }

  @Override
  public void onBindViewHolder(WaypointsAdapter.ViewHolder holder, int position) {
    holder.setData(position, this);
  }

  @Override
  public int getItemCount() {
    return points.size();
  }

  /**
   * Attaches a {@link SharedPreferences} object to this adapter for maintaining user settings.
   *
   * @param sharedPreferences the preferences to attach
   */
  public void setSharedPreferences(SharedPreferences sharedPreferences) {
    this.sharedPreferences = sharedPreferences;
    if (sharedPreferences.contains(PREFS_ORIGIN) && points.size() == 0) {
      try {
        points.add(new SerializablePlace(sharedPreferences.getString(PREFS_ORIGIN, "")));
      } catch (IllegalArgumentException e) {
        Log.e("LOAD ORIGIN", e.toString());
      }
    }
  }

  /**
   * Adds a {@link Place} to the list and animates the addition.
   *
   * @param place the waypoint to add
   */
  public void addWaypoint(Place place) {
    SerializablePlace p = new SerializablePlace(place);
    if (!points.contains(p)) {
      points.add(p);
      notifyItemInserted(points.size() - 1);
    }
  }

  /**
   * Removes a waypoint from the list and animates the removal.
   *
   * @param adapterPosition which waypoint to remove
   */
  public void removeWaypoint(int adapterPosition) {
    boolean wasOriginLocked = isOriginLocked();
    points.remove(adapterPosition);
    notifyItemRemoved(adapterPosition);
    if (adapterPosition == 0) {
      if (wasOriginLocked) {
        Editor e = sharedPreferences.edit();
        e.remove(PREFS_ORIGIN);
        e.apply();
      }
      notifyItemChanged(0);
    }
  }

  /**
   * Empties the list of waypoints, except for the starting point if it is locked.
   */
  public void empty() {
    int end = isOriginLocked() ? 1 : 0;
    for (int i = points.size() - 1; i >= end; i--) {
      points.remove(i);
      notifyItemRemoved(i);
    }
  }

  /**
   * Sets a point as the starting point and puts the former starting point in its place.
   *
   * @param index the point to make into the new starting point
   */
  public void makeOrigin(int index) {
    SerializablePlace o = points.get(0);
    points.set(0, points.get(index));
    points.set(index, o);
    notifyItemChanged(0);
    notifyItemChanged(index);
  }

  /**
   * Saves the current starting point in the {@link SharedPreferences}.
   */
  public void lockOrigin() {
    Editor e = sharedPreferences.edit();
    if (isOriginLocked()) {
      e.remove(PREFS_ORIGIN);
    } else {
      e.putString(PREFS_ORIGIN, points.get(0).toString());
    }
    e.apply();
    notifyItemChanged(0);
  }

  /**
   * Saves a {@link SerializablePlace} to the quick access list or removes it if it's already there.
   *
   * @param index the point to save
   */
  public void makeFavorite(int index) {
    Editor e = sharedPreferences.edit();
    SerializablePlace current = points.get(index);
    Set<String> favs = new HashSet<>(
        sharedPreferences.getStringSet(PREFS_FAVORITES, new HashSet<String>())
    );
    String savedString = current.isFavorite(sharedPreferences);
    if (savedString != null) {
      favs.remove(savedString);
    } else {
      favs.add(current.toString());
    }
    e.putStringSet(PREFS_FAVORITES, favs);
    e.apply();
    notifyItemChanged(index);
  }

  /**
   * Checks if the current starting point is saved in the {@link SharedPreferences}.
   *
   * @return true if the preferences contain a point with the same coordinates, false else
   */
  public boolean isOriginLocked() {
    boolean locked = false;
    try {
      locked = sharedPreferences.contains(PREFS_ORIGIN)
          && points.get(0).isSameAs(
          new SerializablePlace(sharedPreferences.getString(PREFS_ORIGIN, "")));
    } catch (IllegalArgumentException e) {
      Log.e("LOAD ORIGIN", e.toString());
    }
    return locked;
  }

  /**
   * Adds a subset of the formerly unused quick access items ({@link #getUnusedFavorites()} to the
   * list of waypoints.
   *
   * @param choices the items to add
   * @return items that could not be added
   */
  public Set<SerializablePlace> addFavorites(Set<Integer> choices) {
    List<Integer> insertedIndices = new ArrayList<>(MAXIMUM_WAYPOINTS+1);
    Set<SerializablePlace> uninsertedPlaces = new HashSet<>(MAXIMUM_WAYPOINTS+1);
    for (int c : choices) {
      int index = 0;
      for (String f : unusedFavorites) {
        if (index++ == c) {
          if (points.size() <= MAXIMUM_WAYPOINTS) {
            insertedIndices.add(points.size());
            points.add(new SerializablePlace(f));
          } else {
            uninsertedPlaces.add(new SerializablePlace(f));
          }
        }
      }
    }
    if (insertedIndices.size() > 0) {
      int first = insertedIndices.get(0);
      notifyItemRangeInserted(first, insertedIndices.get(insertedIndices.size()-1) - (first-1));
    }
    return uninsertedPlaces;
  }

  /**
   * Creates a {@link SortedSet} of points that are on the quick access list but not currently part
   * of the list of waypoints.
   *
   * @return the quick access points minus the waypoints
   */
  public SortedSet<String> getUnusedFavorites() {
    unusedFavorites = new TreeSet<>(
        sharedPreferences.getStringSet(PREFS_FAVORITES, new TreeSet<String>())
    );
    for (String f : sharedPreferences.getStringSet(PREFS_FAVORITES, new TreeSet<String>())) {
      for (SerializablePlace p : points) {
        if (p.isSameAs(new SerializablePlace(f))) {
          unusedFavorites.remove(f);
        }
      }
    }
    return unusedFavorites;
  }

  /**
   * Returns the complement of a subset of the unused quick access items (within the unused items).
   *
   * @param choices the subset to invert
   * @return the complement of choices
   */
  public Set<Integer> invertUnusedFavorites(Set<Integer> choices) {
    Set<Integer> inverted = new HashSet<>(MAXIMUM_WAYPOINTS + 1);
    for (int i = 0; i < unusedFavorites.size(); i++) {
      if (!choices.contains(i)) {
        inverted.add(i);
      }
    }
    return inverted;
  }

  /**
   * Applies a new label to a {@link SerializablePlace} and its saved representations.
   *
   * @param index the point to manipulate
   * @param label the new label
   */
  public void relabel(int index, String label) {
    SerializablePlace p = points.get(index);
    String favString = p.isFavorite(sharedPreferences);
    boolean isLocked = false;
    try {
      isLocked = p.isSameAs(new SerializablePlace(sharedPreferences.getString(PREFS_ORIGIN, "")));
    } catch (IllegalArgumentException e) {
    }
    p.setLabel(label);
    Editor e = sharedPreferences.edit();
    if (favString != null) {
      Set<String> favs = new HashSet<>(
          sharedPreferences.getStringSet(PREFS_FAVORITES, new HashSet<String>())
      );
      favs.remove(favString);
      favs.add(p.toString());
      e.putStringSet(PREFS_FAVORITES, favs);
    }
    if (isLocked) {
      e.putString(PREFS_ORIGIN, p.toString());
    }
    e.apply();
  }

  /**
   * Creates a {@link JSONObject} representation of the list of waypoints.
   *
   * @return a representation of all the vital information contained in this adapter
   */
  public JSONObject toJson() {
    JSONObject representation = new JSONObject();
    if (points.size() > 0) {
      try {
        representation.put(JSON_ORIGIN, points.get(0).toJson());
        JSONArray destinations = new JSONArray();
        for (int i = 1; i < points.size(); i++) {
          destinations.put(points.get(i).toJson());
        }
        representation.put(JSON_DESTINATIONS, destinations);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }
    }
    return representation;
  }

  /**
   * Creates a string representing the list of waypoints for retrieving the
   * <a href="http://project-osrm.org/docs/v5.6.0/api/#table-service">distance matrix</a>.
   *
   * @return a {@link String} that can be passed to an OSRM instance
   */
  public String toOsrmString() {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < points.size(); i++) {
      SerializablePlace p = points.get(i);
      b.append(String.format(Locale.US, "%f,%f", p.getLongitude(), p.getLatitude()));
      if (i < points.size() - 1) {
        b.append(";");
      }
    }
    return b.toString();
  }

  /**
   * A {@link android.support.v7.widget.RecyclerView.ViewHolder} for a waypoint and the actions the
   * user can take on it.
   */
  public static class ViewHolder extends RecyclerView.ViewHolder {
    private CardView root;
    private TextInputLayout label;
    private TextView details;
    private ImageView origin;
    private ImageView favorite;
    private TextWatcher watcher = null;

    /**
     * Saves references to all the {@link android.view.View}s that are used to display data or
     * gather input.
     *
     * @param itemView the {@link CardView} holding the UI elements
     */
    public ViewHolder(ViewGroup itemView) {
      super(itemView);
      root = (CardView) itemView;
      ViewGroup g = (ViewGroup) root.getChildAt(0);
      label = (TextInputLayout) g.getChildAt(0);
      details = (TextView) g.getChildAt(1);
      origin = (ImageView) ((ViewGroup) g.getChildAt(2)).getChildAt(0);
      favorite = (ImageView) ((ViewGroup) g.getChildAt(2)).getChildAt(1);
    }

    /**
     * Displays a {@link SerializablePlace} in a {@link CardView} and prepares it for user
     * manipulation.
     *
     * @param index the point to display
     * @param adapter the {@link WaypointsAdapter} for information retrieval/manipulation
     */
    public void setData(final int index, final WaypointsAdapter adapter) {
      SerializablePlace p = adapter.points.get(index);
      boolean isFavorite = p.isFavorite(adapter.sharedPreferences) != null;
      boolean isOrigin = index == 0;
      boolean originLocked = adapter.isOriginLocked();
      if (watcher != null) {
        label.getEditText().removeTextChangedListener(watcher);
      }
      label.getEditText().setText(p.getLabel());
      details.setText(p.getDetails());
      root.setCardBackgroundColor(
          root.getResources().getColor(
              isOrigin ?
                  R.color.colorAccentLight :
                  R.color.cardview_light_background
          )
      );
      origin.setImageResource(
          isOrigin ?
              (originLocked ? R.drawable.ic_lock_24px : R.drawable.ic_lock_open_24px) :
              R.drawable.ic_pin_drop_24px
      );
      favorite.setImageResource(
          isFavorite ?
              R.drawable.ic_star_24px :
              R.drawable.ic_star_border_24px
      );
      watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
          adapter.relabel(index, s.toString());
        }
      };
      label.getEditText().addTextChangedListener(watcher);
    }
  }

  /**
   * A minimal projection of a {@link Place}.
   */
  public static class SerializablePlace implements Serializable {
    private double longitude;
    private double latitude;
    private CharSequence label;
    private CharSequence details;
    private CharSequence attributions;

    /**
     * Initializes the point from a JSON {@link String}.
     *
     * @param json the string containing all the point's information
     */
    public SerializablePlace(String json) {
      try {
        JSONObject object = new JSONObject(json);
        this.longitude = object.getDouble(JSON_LONGITUDE);
        this.latitude = object.getDouble(JSON_LATITUDE);
        this.label = object.getString(JSON_LABEL);
        this.details = object.getString(JSON_DETAILS);
        this.attributions = object.getString(JSON_ATTRIBUTIONS);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    }

    /**
     * Initializes the point from a {@link Place}.
     *
     * @param p the original {@link Place}, as returned by the {@link com.google.android.gms.location.places.ui.PlacePicker}
     */
    public SerializablePlace(Place p) {
      this.longitude = p.getLatLng().longitude;
      this.latitude = p.getLatLng().latitude;
      this.label = p.getName();
      this.details = p.getAddress();
      this.attributions = p.getAttributions();
    }

    public double getLongitude() {
      return longitude;
    }

    public double getLatitude() {
      return latitude;
    }

    public CharSequence getLabel() {
      return label;
    }

    public void setLabel(CharSequence label) {
      this.label = label;
    }

    public CharSequence getDetails() {
      return details;
    }

    public CharSequence getAttributions() {
      return attributions;
    }

    /**
     * Compares this object to another {@link SerializablePlace}.
     *
     * @param p the point to compare with
     * @return true if both describe the same geolocation, false else
     */
    public boolean isSameAs(SerializablePlace p) {
      return p.latitude == this.latitude && p.longitude == this.longitude;
    }

    /**
     * Checks if this object is part of the quick access list.
     *
     * @param p the {@link SharedPreferences} that holds the quick access list
     * @return true if this is a quick access item, false else
     */
    public String isFavorite(SharedPreferences p) {
      for (String f : p.getStringSet(PREFS_FAVORITES, new HashSet<String>())) {
        if (this.isSameAs(new SerializablePlace(f))) {
          return f;
        }
      }
      return null;
    }

    /**
     * Creates a {@link JSONObject} representation of the data contained in this object.
     *
     * @return the JSON representation
     */
    public JSONObject toJson() {
      JSONObject representation = new JSONObject();
      try {
        representation.put(JSON_LONGITUDE, this.longitude);
        representation.put(JSON_LATITUDE, this.latitude);
        representation.put(JSON_LABEL, this.label == null ? "" : this.label);
        representation.put(JSON_DETAILS, this.details == null ? "" : this.details);
        representation.put(JSON_ATTRIBUTIONS, this.attributions == null ? "" : this.attributions);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }
      return representation;
    }

    @Override
    public String toString() {
      return toJson().toString();
    }
  }

  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    out.writeObject(points);
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    points = (List<SerializablePlace>) in.readObject();
  }

  private void readObjectNoData() throws ObjectStreamException {
    points = new ArrayList<>(MAXIMUM_WAYPOINTS + 1);
  }
}
