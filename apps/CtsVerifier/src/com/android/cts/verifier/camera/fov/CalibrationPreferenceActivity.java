// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.cts.verifier.camera.fov;

import com.android.cts.verifier.R;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

/**
 * Preferences for the LightCycle calibration app.
 *
 */
public class CalibrationPreferenceActivity extends FragmentActivity {
  public static final String OPTION_MARKER_DISTANCE = "markerDistance";
  public static final String OPTION_TARGET_DISTANCE = "targetDistanceCm";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(R.style.Theme_AppCompat);
    getSupportFragmentManager().beginTransaction().add(android.R.id.content,
            new CalibrationPreferenceFragment()).commit();
  }
}
