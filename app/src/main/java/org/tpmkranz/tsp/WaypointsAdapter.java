package org.tpmkranz.tsp;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.LinearLayout;
import android.widget.TextSwitcher;
import android.widget.TextView;
import com.google.android.gms.location.places.Place;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WaypointsAdapter
    extends RecyclerView.Adapter<WaypointsAdapter.ViewHolder>
    implements Serializable {
  public static final String JSON_ORIGIN = "origin";
  public static final String JSON_DESTINATIONS = "destinations";
  public static final String JSON_LATITUDE = "latitude";
  public static final String JSON_LONGITUDE = "longitude";
  public static final String JSON_LABEL = "label";
  public static final String JSON_DETAILS = "details";
  public static final int MAXIMUM_WAYPOINTS = 12;
  public static final String PREFS_ORIGIN = "origin";
  public static final String PREFS_FAVORITES = "favorites";
  public static final String JSON_ATTRIBUTIONS = "attributions";

  private List<SerializablePlace> points;
  private CardView originView;
  private SharedPreferences sharedPreferences;
  private SortedSet<String> unusedFavorites;

  public WaypointsAdapter() {
    if (this.points == null) {
      this.points = new ArrayList<>(MAXIMUM_WAYPOINTS + 1);
    }
  }

  public void setOriginView(CardView originView) {
    this.originView = originView;
  }

  public void setSharedPreferences(SharedPreferences sharedPreferences) {
    this.sharedPreferences = sharedPreferences;
    if (sharedPreferences.contains(PREFS_ORIGIN)) {
      try {
        if (points.size() == 0) {
          points.add(new SerializablePlace(sharedPreferences.getString(PREFS_ORIGIN, "")));
        }
      } catch (IllegalArgumentException e) {
        Log.e("LOAD ORIGIN", e.toString());
      }
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
    holder.set(points.get(position + 1),
        points.get(position + 1).isFavorite(sharedPreferences) != null);
  }

  @Override
  public int getItemCount() {
    if (points.size() == 0) {
      return 0;
    } else {
      return points.size() - 1;
    }
  }

  public void addWaypoint(Place place) {
    points.add(new SerializablePlace(place));
    if (points.size() == 1) {
      redrawOrigin(true);
    } else {
      notifyItemInserted(points.size() - 2);
    }
  }

  public void removeWaypoint(int adapterPosition) {
    points.remove(adapterPosition + 1);
    notifyItemRemoved(adapterPosition);
  }

  public void redrawOrigin(boolean fieldsChanged) {
    LinearLayout placeLayout = (LinearLayout) originView.getChildAt(0);
    TextInputLayout labelView = (TextInputLayout) placeLayout.getChildAt(0);
    TextSwitcher detailsView = (TextSwitcher) placeLayout.getChildAt(1);
    LinearLayout buttonLayout = (LinearLayout) placeLayout.getChildAt(2);
    ImageSwitcher lockButton = (ImageSwitcher) buttonLayout.getChildAt(0);
    ImageSwitcher favoriteButton = (ImageSwitcher) buttonLayout.getChildAt(1);
    if (points.size() > 0) {
      originView.getChildAt(1).setVisibility(View.GONE);
      placeLayout.setVisibility(View.VISIBLE);
      if (fieldsChanged) {
        labelView.getEditText().setText(points.get(0).getLabel());
        detailsView.setText(points.get(0).getDetails());
      }
      lockButton.setImageResource(isOriginLocked() ?
          R.drawable.ic_lock_24px : R.drawable.ic_lock_open_24px);
      favoriteButton.setImageResource(points.get(0).isFavorite(sharedPreferences) != null ?
          R.drawable.ic_star_24px : R.drawable.ic_star_border_24px);
      Log.d("ISORIGINLOCKED", String.valueOf(isOriginLocked()));
    } else {
      originView.getChildAt(1).setVisibility(View.VISIBLE);
      placeLayout.setVisibility(View.GONE);
    }
  }

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

  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    out.writeObject(points);
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    points = (List<SerializablePlace>) in.readObject();
  }

  private void readObjectNoData() throws ObjectStreamException {
    points = new ArrayList<>(12);
  }

  public void makeOrigin(int index) {
    SerializablePlace o = points.get(0);
    points.set(0, points.get(index));
    points.set(index, o);
    notifyItemChanged(index-1);
    redrawOrigin(true);
  }

  public boolean isOriginLocked() {
    boolean locked = false;
    try {
      locked = sharedPreferences.contains(PREFS_ORIGIN)
          && points.get(0).isSameAs(new SerializablePlace(sharedPreferences.getString(PREFS_ORIGIN, "")));
    } catch (IllegalArgumentException e) {
      Log.e("LOAD ORIGIN", e.toString());
    }
    return locked;
  }

  public void lockOrigin() {
    Editor e = sharedPreferences.edit();
    e.putString(PREFS_ORIGIN, points.get(0).toString());
    e.apply();
    redrawOrigin(false);
  }

  public void unlockOrigin() {
    Editor e = sharedPreferences.edit();
    e.remove(PREFS_ORIGIN);
    e.apply();
    redrawOrigin(false);
  }

  public void makeFavorite(int index) {
    Editor e = sharedPreferences.edit();
    SerializablePlace current = points.get(index);
    Set<String> favs = new HashSet<>(sharedPreferences.getStringSet(PREFS_FAVORITES, new HashSet<String>()));
    String savedString = current.isFavorite(sharedPreferences);
    if (savedString != null) {
      favs.remove(savedString);
    } else {
      favs.add(current.toString());
    }
    e.putStringSet(PREFS_FAVORITES, favs);
    e.apply();
    if (index < 1) {
      redrawOrigin(false);
    } else {
      notifyItemChanged(index - 1);
    }
  }

  public SortedSet<String> getUnusedFavorites() {
    unusedFavorites = new TreeSet<>(sharedPreferences.getStringSet(PREFS_FAVORITES, new TreeSet<String>()));
    for (String f : sharedPreferences.getStringSet(PREFS_FAVORITES, new TreeSet<String>())) {
      for (SerializablePlace p : points) {
        if (p.isSameAs(new SerializablePlace(f))) {
          unusedFavorites.remove(f);
        }
      }
    }
    return unusedFavorites;
  }

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
    if (insertedIndices.size() > 0 && insertedIndices.get(0) == 0) {
      redrawOrigin(true);
      insertedIndices.remove(0);
    }
    if (insertedIndices.size() > 0) {
      int first = insertedIndices.get(0);
      notifyItemRangeInserted(first, insertedIndices.get(insertedIndices.size()-1) - (first-1));
    }
    return uninsertedPlaces;
  }

  public void reorder(byte[] shortestRoute) {
    ArrayList<SerializablePlace> newOrder = new ArrayList<>(MAXIMUM_WAYPOINTS);
    for (int i = 0; i < shortestRoute.length; i++) {
      newOrder.add(points.get(shortestRoute[i]));
    }
    points = newOrder;
    notifyDataSetChanged();
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    private TextInputLayout label;
    private TextSwitcher details;
    private ImageSwitcher favorite;

    public ViewHolder(ViewGroup itemView) {
      super(itemView);
      ViewGroup g = (ViewGroup) itemView.getChildAt(0);
      label = (TextInputLayout) g.getChildAt(0);
      details = (TextSwitcher) g.getChildAt(1);
      favorite = (ImageSwitcher) ((ViewGroup) g.getChildAt(2)).getChildAt(1);
    }

    public void set(SerializablePlace p, boolean isFavorite) {
      if (!label.getEditText().getText().equals(p.getLabel())) {
        label.getEditText().setText(p.getLabel());
      }
      if (!((TextView) details.getCurrentView()).getText().equals(p.getDetails())) {
        details.setText(p.getDetails());
      }
      favorite.setImageResource(isFavorite ?
          R.drawable.ic_star_24px : R.drawable.ic_star_border_24px);
    }
  }

  public static class SerializablePlace implements Serializable {
    private double longitude;
    private double latitude;
    private CharSequence label;
    private CharSequence details;
    private CharSequence attributions;

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

    public boolean isSameAs(SerializablePlace p) {
      return p.latitude == this.latitude && p.longitude == this.longitude;
    }

    public String isFavorite(SharedPreferences p) {
      for (String f : p.getStringSet(PREFS_FAVORITES, new HashSet<String>())) {
        if (this.isSameAs(new SerializablePlace(f))) {
          return f;
        }
      }
      return null;
    }
  }
}
