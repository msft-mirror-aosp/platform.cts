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
import android.os.IBinder;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the behavior of service binding from client applications to a server application,
 * considering the presence or absence of the {@code <allow-component-access>} tag in the client's
 * manifest. These tests establish baseline behavior in a standard environment.
 */
@RunWith(AndroidJUnit4.class)
public class AllowComponentAccessTest {

    private static final String SERVER_PKG = "com.android.cts.allowcomponentaccess.server";
    private static final String CLIENT_ALLOW_PKG =
            "com.android.cts.allowcomponentaccess.client_allow";
    private static final String CLIENT_NO_TAG_PKG =
            "com.android.cts.allowcomponentaccess.client_notag";
    private static final int BIND_TIMEOUT_SECONDS = 2;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testEgress_whenAllowed_succeeds() throws Exception {
        Context clientContext = getContextForPackage(CLIENT_ALLOW_PKG);
        boolean isBound = bindToService(clientContext);
        assertThat(isBound).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testEgress_whenNoTag_isNotAffected() throws Exception {
        Context clientContext = getContextForPackage(CLIENT_NO_TAG_PKG);
        boolean isBound = bindToService(clientContext);
        assertThat(isBound).isTrue();
    }

    @Test
    @RequiresFlagsDisabled(
            android.app.privatecompute.flags.Flags.FLAG_ENABLE_ALLOW_COMPONENT_ACCESS)
    public void testEgress_flagDisabled_behaviorUnchanged() throws Exception {
        Context clientContext = getContextForPackage(CLIENT_ALLOW_PKG);
        boolean isBound = bindToService(clientContext);
        assertThat(isBound).isTrue();
    }

    private Context getContextForPackage(String packageName) throws Exception {
        return InstrumentationRegistry.getInstrumentation()
                .getContext()
                .createPackageContext(
                        packageName,
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
    }

    private boolean bindToService(Context clientContext) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        latch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };

        Intent intent = new Intent();
        intent.setClassName(
                AllowComponentAccessTest.SERVER_PKG,
                "com.android.cts.allowcomponentaccess.server.MyService");

        boolean bindResult = false;
        try {
            bindResult = clientContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);

            if (!bindResult) {
                return false;
            }

            // Wait for a short period to see if the connection is established.
            // In the denied case, onServiceConnected should not be called.
            return latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            if (bindResult) {
                try {
                    clientContext.unbindService(connection);
                } catch (Exception e) {
                    // Log error during unbind, but don't fail the test for it
                    Log.e("AllowComponentAccessTest", "Error unbinding service: " + e);
                }
            }
        }
    }
}
