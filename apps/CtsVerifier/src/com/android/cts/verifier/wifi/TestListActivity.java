/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.verifier.wifi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

/**
 * Activity listing all Wi-Fi Wifi tests.
 */
public class TestListActivity extends PassFailButtons.TestListActivity {
    private static final String TAG = "TestListActivity";

    private WifiManager mWifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (mWifiManager == null) {
            Log.wtf(TAG,
                    "Can't get WIFI_SERVICE. Should be gated by 'test_required_features'!?");
            return;
        }
        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.wifi_test, R.string.wifi_test_info, 0);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        // Add the sub-test/categories
        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        adapter.add(TestListItem.newBuilder(this, R.string.wifi_test_network_request).build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.wifi_test_network_request_specific)
                        .setTestName(
                                NetworkRequestSpecificNetworkSpecifierTestActivity.class.getName())
                        .setIntent(
                                new Intent(
                                        this,
                                        NetworkRequestSpecificNetworkSpecifierTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.wifi_test_network_request_pattern)
                        .setTestName(
                                NetworkRequestPatternNetworkSpecifierTestActivity.class.getName())
                        .setIntent(
                                new Intent(
                                        this,
                                        NetworkRequestPatternNetworkSpecifierTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.wifi_test_network_request_unavailable)
                        .setTestName(
                                NetworkRequestUnavailableNetworkSpecifierTestActivity.class
                                        .getName())
                        .setIntent(
                                new Intent(
                                        this,
                                        NetworkRequestUnavailableNetworkSpecifierTestActivity
                                                .class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.wifi_test_network_request_invalid_credential)
                        .setTestName(
                                NetworkRequestInvalidCredentialNetworkSpecifierTestActivity.class
                                        .getName())
                        .setIntent(
                                new Intent(
                                        this,
                                        NetworkRequestInvalidCredentialNetworkSpecifierTestActivity
                                                .class))
                        .build());
        adapter.add(TestListItem.newBuilder(this, R.string.wifi_test_network_suggestion).build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.wifi_test_network_suggestion_ssid)
                        .setTestName(NetworkSuggestionSsidTestActivity.class.getName())
                        .setIntent(new Intent(this, NetworkSuggestionSsidTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.wifi_test_network_suggestion_ssid_bssid)
                        .setTestName(NetworkSuggestionSsidBssidTestActivity.class.getName())
                        .setIntent(new Intent(this, NetworkSuggestionSsidBssidTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(
                                this, R.string.wifi_test_network_suggestion_ssid_post_connect)
                        .setTestName(NetworkSuggestionSsidPostConnectTestActivity.class.getName())
                        .setIntent(
                                new Intent(
                                        this, NetworkSuggestionSsidPostConnectTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(
                                this, R.string.wifi_test_network_suggestion_connection_failure)
                        .setTestName(NetworkSuggestionConnectionFailureTestActivity.class.getName())
                        .setIntent(
                                new Intent(
                                        this, NetworkSuggestionConnectionFailureTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(
                                this, R.string.wifi_test_network_suggestion_modification_in_place)
                        .setTestName(
                                NetworkSuggestionModificationInPlaceTestActivity.class.getName())
                        .setIntent(
                                new Intent(
                                        this,
                                        NetworkSuggestionModificationInPlaceTestActivity.class))
                        .build());

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }

            @Override
            public void onInvalidated() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);
    }

    @Override
    protected void handleItemClick(ListView listView, View view, int position, long id) {
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!mWifiManager.isWifiEnabled() || !locationManager.isLocationEnabled()) {
            showWifiAndLocationEnableDialog();
            return;
        }
        super.handleItemClick(listView, view, position, id);
    }

    /**
     * Show the dialog to jump to system settings in order to enable WiFi & location.
     */
    private void showWifiAndLocationEnableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(R.string.wifi_location_not_enabled);
        builder.setMessage(R.string.wifi_location_not_enabled_message);
        builder.setPositiveButton(R.string.wifi_settings,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                });
        builder.setPositiveButton(R.string.location_settings,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                });
        builder.create().show();
    }
}
