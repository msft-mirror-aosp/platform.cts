/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.cts.nfc.multidevice.emulator;

import static android.content.Context.RECEIVER_EXPORTED;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.cts.nfc.multidevice.utils.SnippetBroadcastReceiver;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

public class CtsNfcEmulatorDeviceSnippet implements Snippet {

    protected static final String TAG = "CtsNfcEmulatorDeviceSnippet";
    private BaseEmulatorActivity mActivity;
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static final long TIMEOUT_MS = 10_000L;

    /** Opens single Non Payment emulator */
    @Rpc(description = "Open single non payment emulator activity")
    public void startSingleNonPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                SingleNonPaymentEmulatorActivity.class.getName());

        mActivity = (SingleNonPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens single payment emulator activity */
    @Rpc(description = "Open single payment emulator activity")
    public void startSinglePaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), SinglePaymentEmulatorActivity.class.getName());

        mActivity = (SinglePaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens dual payment emulator activity */
    @Rpc(description = "Opens dual payment emulator activity")
    public void startDualPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), DualPaymentEmulatorActivity.class.getName());

        mActivity = (DualPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens foreground payment emulator activity */
    @Rpc(description = "Opens foreground payment emulator activity")
    public void startForegroundPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                ForegroundPaymentEmulatorActivity.class.getName());

        mActivity = (ForegroundPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens dynamic AID emulator activity */
    @Rpc(description = "Opens dynamic AID emulator activity")
    public void startDynamicAidEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), DynamicAidEmulatorActivity.class.getName());

        mActivity = (DynamicAidEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens prefix payment emulator activity */
    @Rpc(description = "Opens prefix payment emulator activity")
    public void startPrefixPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), PrefixPaymentEmulatorActivity.class.getName());

        mActivity = (PrefixPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens prefix payment emulator 2 activity */
    @Rpc(description = "Opens prefix payment emulator 2 activity")
    public void startPrefixPaymentEmulator2Activity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), PrefixPaymentEmulator2Activity.class.getName());

        mActivity = (PrefixPaymentEmulator2Activity) instrumentation.startActivitySync(intent);
    }

    /** Opens dual non payment activity */
    @Rpc(description = "Opens dual non-payment prefix emulator activity")
    public void startDualNonPaymentPrefixEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                DualNonPaymentPrefixEmulatorActivity.class.getName());

        mActivity =
                (DualNonPaymentPrefixEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens off host emulator activity */
    @Rpc(description = "Open off host emulator activity")
    public void startOffHostEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), OffHostEmulatorActivity.class.getName());

        mActivity = (OffHostEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens on and off host emulator activity */
    @Rpc(description = "Open on and off host emulator activity")
    public void startOnAndOffHostEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), OnAndOffHostEmulatorActivity.class.getName());

        mActivity = (OnAndOffHostEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens dual non-payment emulator activity */
    @Rpc(description = "Opens dual non-payment emulator activity")
    public void startDualNonPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), DualNonPaymentEmulatorActivity.class.getName());

        mActivity = (DualNonPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens foreground non-payment emulator activity */
    @Rpc(description = "Opens foreground non-payment emulator activity")
    public void startForegroundNonPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                ForegroundNonPaymentEmulatorActivity.class.getName());

        mActivity =
                (ForegroundNonPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens throughput emulator activity */
    @Rpc(description = "Opens throughput emulator activity")
    public void startThroughputEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), ThroughputEmulatorActivity.class.getName());

        mActivity = (ThroughputEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens tap test emulator activity */
    @Rpc(description = "Opens tap test emulator activity")
    public void startTapTestEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), TapTestEmulatorActivity.class.getName());

        mActivity = (TapTestEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens large num AIDs emulator activity */
    @Rpc(description = "Opens large num AIDs emulator activity")
    public void startLargeNumAidsEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), LargeNumAidsEmulatorActivity.class.getName());

        mActivity = (LargeNumAidsEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens screen off emulator activity */
    @Rpc(description = "Opens screen off emulator activity")
    public void startScreenOffPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                ScreenOffPaymentEmulatorActivity.class.getName());

        mActivity = (ScreenOffPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens conflicting non-payment emulator activity */
    @Rpc(description = "Opens conflicting non-payment emulator activity")
    public void startConflictingNonPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                ConflictingNonPaymentEmulatorActivity.class.getName());
        mActivity =
                (ConflictingNonPaymentEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens conflicting non-payment prefix emulator activity */
    @Rpc(description = "Opens conflicting non-payment prefix emulator activity")
    public void startConflictingNonPaymentPrefixEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                ConflictingNonPaymentPrefixEmulatorActivity.class.getName());
        mActivity =
                (ConflictingNonPaymentPrefixEmulatorActivity)
                        instrumentation.startActivitySync(intent);
    }

    /** Opens protocol params emulator activity */
    @Rpc(description = "Opens protocol params emulator activity")
    public void startProtocolParamsEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), ProtocolParamsEmulatorActivity.class.getName());

        mActivity = (ProtocolParamsEmulatorActivity) instrumentation.startActivitySync(intent);
    }

    /** Registers receiver for Test Pass event */
    @AsyncRpc(description = "Waits for Test Pass event")
    public void asyncWaitForTestPass(String callbackId, String eventName) {
        registerSnippetBroadcastReceiver(
                callbackId, eventName, BaseEmulatorActivity.ACTION_TEST_PASSED);
    }

    /** Registers receiver for Role Held event */
    @AsyncRpc(description = "Waits for Role Held event")
    public void asyncWaitForRoleHeld(String callbackId, String eventName) {
        registerSnippetBroadcastReceiver(
                callbackId, eventName, BaseEmulatorActivity.ACTION_ROLE_HELD);
    }

    /** Registers receiver for Screen Off event */
    @AsyncRpc(description = "Waits for Screen Off event")
    public void asyncWaitForScreenOff(String callbackId, String eventName) {
        registerSnippetBroadcastReceiver(callbackId, eventName, Intent.ACTION_SCREEN_OFF);
    }

    /** Closes emulator activity */
    @Rpc(description = "Close activity if one was opened.")
    public void closeActivity() {
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    /** Turns device screen off */
    @Rpc(description = "Turns device screen off")
    public void turnScreenOff() {
        try {
            mDevice.sleep();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException", e);
        }
    }

    /** Turns device screen on */
    @Rpc(description = "Turns device screen on")
    public void turnScreenOn() {
        try {
            mDevice.wakeUp();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException", e);
        }
    }

    /** Press device menu button to return device to home screen between tests. */
    @Rpc(description = "Press menu button")
    public void pressMenu() {
        mDevice.pressMenu();
    }

    /** Automatically selects TransportService2 from list of services. */
    @Rpc(description = "Automatically selects TransportService2 from list of services.")
    public void selectItem() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        String text = instrumentation.getTargetContext().getString(R.string.transportService2);
        Log.d(TAG, text);
        try {
            UiScrollable listView = new UiScrollable(new UiSelector());
            listView.scrollTextIntoView(text);
            listView.waitForExists(TIMEOUT_MS);
            UiObject listViewItem =
                    listView.getChildByText(
                            new UiSelector().className(android.widget.TextView.class.getName()),
                            "" + text + "");
            if (listViewItem.exists()) {
                listViewItem.click();
                Log.d(TAG, text + " ListView item was clicked.");
            } else {
                Log.e(TAG, "UI Object does not exist.");
            }
        } catch (UiObjectNotFoundException e) {
            Log.e(TAG, "Ui Object not found.");
        }
    }

    /** Creates a SnippetBroadcastReceiver that listens for when the specified action is received */
    private void registerSnippetBroadcastReceiver(
            String callbackId, String eventName, String action) {
        IntentFilter filter = new IntentFilter(action);
        mContext.registerReceiver(
                new SnippetBroadcastReceiver(
                        mContext, new SnippetEvent(callbackId, eventName), action),
                filter,
                RECEIVER_EXPORTED);
    }
}
