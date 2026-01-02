/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.content.pm.cts.allowcomponentaccess;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the behavior of service binding from client applications to a server application,
 * considering the presence or absence of the {@code <allow-component-access>} tag in the source and
 * target apps' manifests.
 */
@AppModeFull(reason = "Test relies on other app to connect to. Instant apps can't see other apps")
@RunWith(AndroidJUnit4.class)
public class AllowComponentAccessTest {

    private static final int TIMEOUT_SECONDS = 3;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // --- Egress Tests (Source Constraints) ---

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceAllow_targetNoTag_bindSucceed() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_ALLOW,
                        Constants.PKG_TARGET_NO_TAG,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceBlock_targetNoTag_bindBlocked() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_BLOCK,
                        Constants.PKG_TARGET_NO_TAG,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceNoTag_targetNoTag_bindSucceed() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_NO_TAG,
                        Constants.PKG_TARGET_NO_TAG,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isTrue();
    }

    // --- Ingress Tests (Target Constraints) ---

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceNoTag_targetAllow_bindSucceed() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_NO_TAG,
                        Constants.PKG_TARGET_ALLOW,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceNoTag_targetBlock_bindBlocked() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_NO_TAG,
                        Constants.PKG_TARGET_BLOCK,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isFalse();
    }

    // --- Interaction Tests (Both sides have tags) ---

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceAllow_targetAllow_bindSucceed() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_ALLOW,
                        Constants.PKG_TARGET_ALLOW,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceAllow_targetBlock_bindBlocked() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_ALLOW,
                        Constants.PKG_TARGET_BLOCK,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceBlock_targetBlock_bindBlocked() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_BLOCK,
                        Constants.PKG_TARGET_BLOCK,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_sourceBlock_targetAllow_bindBlocked() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_BLOCK,
                        Constants.PKG_TARGET_ALLOW,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isFalse();
    }

    // --- Broadcast Tests ---

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBroadcast_sourceNoTag_targetBlock_broadcastBlocked() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_NO_TAG,
                        Constants.PKG_TARGET_BLOCK,
                        Constants.ACTION_TYPE_BROADCAST);
        assertThat(success).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBroadcast_sourceNoTag_targetAllow_broadcastSucceed() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_NO_TAG,
                        Constants.PKG_TARGET_ALLOW,
                        Constants.ACTION_TYPE_BROADCAST);
        assertThat(success).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBroadcast_sourceBlock_targetNoTag_broadcastBlocked() throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_BLOCK,
                        Constants.PKG_TARGET_NO_TAG,
                        Constants.ACTION_TYPE_BROADCAST);
        assertThat(success).isFalse();
    }

    // --- Legacy / Flag Disabled Test ---

    @Test
    @RequiresFlagsDisabled(
            android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testBindService_flagDisabled_sourceAllow_targetNoTag_bindSucceed()
            throws Exception {
        boolean success =
                triggerSourceAction(
                        Constants.PKG_SOURCE_ALLOW,
                        Constants.PKG_TARGET_NO_TAG,
                        Constants.ACTION_TYPE_BIND);
        assertThat(success).isTrue();
    }

    /**
     * Helper: Triggers the ActionRelayReceiver in the Source app to attempt a connection to the
     * Target app.
     *
     * @param sourcePkg The package name of the app initiating the connection.
     * @param targetPkg The package name of the app receiving the connection.
     * @param actionType "BIND" or "BROADCAST"
     * @return true if the connection succeeded (Access Allowed), false if it timed out (Access
     *     Blocked).
     */
    private boolean triggerSourceAction(String sourcePkg, String targetPkg, String actionType)
            throws InterruptedException {

        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        Intent intent = new Intent(Constants.ACTION_RELAY);
        intent.setComponent(new ComponentName(sourcePkg, Constants.RELAY_RECEIVER_CLASS));
        intent.putExtra(Constants.TARGET_PKG, targetPkg);
        intent.putExtra(Constants.TEST_ACTION, actionType);

        final CountDownLatch latch = new CountDownLatch(1);
        ITestCallback callback =
                new ITestCallback.Stub() {
                    @Override
                    public void onActionReceived() {
                        latch.countDown();
                    }
                };

        Bundle extras = new Bundle();
        extras.putBinder(Constants.CALLBACK_BINDER, callback.asBinder());
        intent.putExtras(extras);

        context.sendBroadcast(intent);

        return latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Helper: Attempts to bind to the Target Service directly from the Test Runner process. Use
     * this when the Test Runner has adopted special permissions (like Shell/System).
     *
     * @param targetPkg The package name of the app receiving the connection.
     */
    private boolean bindToTargetDirectly(String targetPkg) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        ServiceConnection conn =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        latch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(targetPkg, Constants.TARGET_SERVICE_CLASS));

        try {
            boolean bindInitiated = context.bindService(intent, conn, Context.BIND_AUTO_CREATE);
            if (!bindInitiated) return false;

            return latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            // Clean up connection to avoid leaking
            try {
                context.unbindService(conn);
            } catch (Exception e) {
            }
        }
    }
}
