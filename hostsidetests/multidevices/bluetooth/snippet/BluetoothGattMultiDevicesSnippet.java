/*
 * Copyright 2023 The Android Open Source Project
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

package com.google.snippet.bluetooth;

import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class BluetoothGattMultiDevicesSnippet implements Snippet {
    private static final String TAG = "BluetoothGattMultiDevicesSnippet";

    private BluetoothGattMultiDevicesServer mGattServer;
    private BluetoothGattMultiDevicesClient mGattClient;

    private Context mContext;
    private BluetoothManager mBluetoothManager;

    public BluetoothGattMultiDevicesSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        var uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
    }

    @Rpc(description = "Reset the state of client + server")
    public void reset() {
        mGattServer = new BluetoothGattMultiDevicesServer(mContext, mBluetoothManager);
        mGattClient = new BluetoothGattMultiDevicesClient(mContext, mBluetoothManager);
    }

    @Rpc(description = "Creates Bluetooth GATT server with a given UUID and advertises it.")
    public void createAndAdvertiseServer(String uuid) {
        mGattServer.createAndAdvertiseServer(uuid);
    }

    @Rpc(
            description =
                    "Creates Bluetooth GATT server with a given UUID and ties it to an"
                            + " advertisement.")
    public void createAndAdvertiseIsolatedServer(String uuid) {
        mGattServer.createAndAdvertiseIsolatedServer(uuid);
    }

    @Rpc(description = "Connect to the peer device advertising the specified UUID")
    public String connectGatt(String uuid) throws JSONException {
        return Utils.convertBtDeviceToJson(mGattClient.connect(uuid));
    }

    @Rpc(description = "Disconnect to the peer device advertising the specified UUID")
    public boolean disconnectGatt(String uuid) throws JSONException {
        return mGattClient.disconnect(uuid);
    }

    @Rpc(description = "Get all the devices connected to the GATT server")
    public JSONArray getConnectedDevices() throws JSONException {
        return Utils.convertBtDevicesToJson(mGattServer.getConnectedDevices());
    }

    @Rpc(description = "Generate local OOB data to used for bonding with the server")
    public JSONObject generateServerLocalOobData() throws JSONException {
        return Utils.convertOobDataToJson(mGattServer.generateLocalOObData());
    }

    @Rpc(description = "Create a bond with the server using local OOB data generated on the server")
    public String createBondOob(String uuid, JSONObject jsonObject) throws JSONException {
        return Utils.convertBtDeviceToJson(mGattClient.createBondOob(
                uuid, Utils.convertJsonToOobData(jsonObject)));
    }

    @Rpc(description = "Create a bond with the server using local OOB data generated on the server")
    public boolean removeBond(String uuid) {
        return mGattClient.removeBond(uuid);
    }

    @Rpc(description = "Enables Bluetooth")
    public void enableBluetooth() {
        mBluetoothManager.getAdapter().enable();
    }

    @Rpc(description = "Disable Bluetooth")
    public void disableBluetooth() {
        mBluetoothManager.getAdapter().disable();
    }

    @Rpc(description = "Checks Bluetooth state")
    public boolean isBluetoothOn() {
        return mBluetoothManager.getAdapter().isEnabled();
    }

    @Rpc(description = "Whether the connected peer has a service of the given UUID")
    public boolean containsService(String uuid) {
        return mGattClient.containsService(uuid);
    }
}
