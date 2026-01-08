/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.cts.verifier.p2p;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ListView;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

/**
 * Activity that lists all the WiFi Direct tests.
 */
public class P2pTestListActivity extends PassFailButtons.TestListActivity {

    /*
     * BroadcastReceiver to check p2p status.
     * If WiFi Direct is disabled, show the dialog message to user.
     */
    private final P2pBroadcastReceiver mReceiver = new P2pBroadcastReceiver();
    private final IntentFilter mIntentFilter = new IntentFilter();
    private boolean mIsP2pEnabled = false;

    /**
     * Constructor
     */
    public P2pTestListActivity() {
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.p2p_test, R.string.p2p_test_info, 0);
        setPassFailButtonClickListeners();

        getPassButton().setEnabled(false);

        /**
         * Added WiFiDirect test activity to the list.
         */
        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);

        // TODO(b/184183917): Remove check for automotive once this issues is resolved.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            adapter.add(TestListItem.newBuilder(this, R.string.p2p_group_formation).build());
            adapter.add(
                    TestListItem.newBuilder(this, R.string.p2p_go_neg_responder_test)
                            .setTestName(GoNegResponderTestActivity.class.getName())
                            .setIntent(new Intent(this, GoNegResponderTestActivity.class))
                            .build());
            adapter.add(
                    TestListItem.newBuilder(this, R.string.p2p_go_neg_requester_test)
                            .setTestName(GoNegRequesterTestListActivity.class.getName())
                            .setIntent(new Intent(this, GoNegRequesterTestListActivity.class))
                            .build());
        }

        adapter.add(TestListItem.newBuilder(this, R.string.p2p_join).build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_group_owner_test)
                        .setTestName(GoTestActivity.class.getName())
                        .setIntent(new Intent(this, GoTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_group_client_test)
                        .setTestName(P2pClientTestListActivity.class.getName())
                        .setIntent(new Intent(this, P2pClientTestListActivity.class))
                        .build());

        adapter.add(TestListItem.newBuilder(this, R.string.p2p_join_with_config).build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_group_owner_with_config_test)
                        .setTestName(GoWithConfigTestActivity.class.getName())
                        .setIntent(new Intent(this, GoWithConfigTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_group_client_with_config_test)
                        .setTestName(P2pClientWithConfigTestListActivity.class.getName())
                        .setIntent(new Intent(this, P2pClientWithConfigTestListActivity.class))
                        .build());
        adapter.add(TestListItem.newBuilder(this, R.string.p2p_join_with_config_2g_band).build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_group_owner_with_config_2g_band_test)
                        .setTestName(GoWithConfig2gBandTestActivity.class.getName())
                        .setIntent(new Intent(this, GoWithConfig2gBandTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_group_client_with_config_2g_band_test)
                        .setTestName(P2pClientWithConfig2gBandTestListActivity.class.getName())
                        .setIntent(
                                new Intent(this, P2pClientWithConfig2gBandTestListActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_join_with_config_fixed_frequency)
                        .build());
        adapter.add(
                TestListItem.newBuilder(
                                this, R.string.p2p_group_owner_with_config_fixed_frequency_test)
                        .setTestName(GoWithConfigFixedFrequencyTestActivity.class.getName())
                        .setIntent(new Intent(this, GoWithConfigFixedFrequencyTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(
                                this, R.string.p2p_group_client_with_config_fixed_frequency_test)
                        .setTestName(
                                P2pClientWithConfigFixedFrequencyTestListActivity.class.getName())
                        .setIntent(
                                new Intent(
                                        this,
                                        P2pClientWithConfigFixedFrequencyTestListActivity.class))
                        .build());

        adapter.add(TestListItem.newBuilder(this, R.string.p2p_service_discovery).build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_service_discovery_responder_test)
                        .setTestName(ServiceResponderTestActivity.class.getName())
                        .setIntent(new Intent(this, ServiceResponderTestActivity.class))
                        .build());
        adapter.add(
                TestListItem.newBuilder(this, R.string.p2p_service_discovery_requester_test)
                        .setTestName(ServiceRequesterTestListActivity.class.getName())
                        .setIntent(new Intent(this, ServiceRequesterTestListActivity.class))
                        .build());

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onResume();
        unregisterReceiver(mReceiver);
    }

    /**
     * Launch the activity when its {@link ListView} item is clicked.
     * If WiFi Direct is disabled, show the dialog to jump to system setting activity.
     **/
    @Override
    protected void handleItemClick(ListView listView, View view, int position, long id) {
        if (!mIsP2pEnabled) {
            showP2pEnableDialog();
            return;
        }
        super.handleItemClick(listView, view, position, id);
    }

    /**
     * Show the dialog to jump to system settings in order to enable
     * WiFi Direct.
     */
    private void showP2pEnableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(R.string.p2p_not_enabled);
        builder.setMessage(R.string.p2p_not_enabled_message);
        builder.setPositiveButton(R.string.p2p_settings,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        builder.create().show();
    }

    /**
     * Receive the WIFI_P2P_STATE_CHANGED_ACTION action.
     */
    class P2pBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if ((state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)) {
                    mIsP2pEnabled = true;
                }
            }
        }
    }
}
