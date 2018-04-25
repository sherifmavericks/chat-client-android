package com.openchat.secureim.components.location;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.LatLng;

import com.openchat.secureim.util.JsonUtils;

import java.io.IOException;

public class openchatPlace {

  private static final String URL = "https://maps.google.com/maps?q=%s,%s";
  private static final String TAG = openchatPlace.class.getSimpleName();

  @JsonProperty
  private CharSequence name;

  @JsonProperty
  private CharSequence address;

  @JsonProperty
  private double latitude;

  @JsonProperty
  private double longitude;

  public openchatPlace(Place place) {
    this.name      = place.getName();
    this.address   = place.getAddress();
    this.latitude  = place.getLatLng().latitude;
    this.longitude = place.getLatLng().longitude;
  }

  public openchatPlace() {}

  @JsonIgnore
  public LatLng getLatLong() {
    return new LatLng(latitude, longitude);
  }

  @JsonIgnore
  public String getDescription() {
    String description = "";

    if (!TextUtils.isEmpty(name)) {
      description += (name + "\n");
    }

    if (!TextUtils.isEmpty(address)) {
      description += (address + "\n");
    }

    description += String.format(URL, latitude, longitude);

    return description;
  }

  public @Nullable String serialize() {
    try {
      return JsonUtils.toJson(this);
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public static openchatPlace deserialize(@NonNull  String serialized) throws IOException {
    return JsonUtils.fromJson(serialized, openchatPlace.class);
  }
}
