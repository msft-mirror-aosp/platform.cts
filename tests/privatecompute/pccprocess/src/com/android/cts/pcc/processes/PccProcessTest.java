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

package com.android.cts.pcc.processes;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.widget.Toast;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccProcessTest {

    private static final int TIMEOUT_SECONDS = 5;
    private static final String CUSTOM_PCC_ALLOWED_PERMISSION =
            "com.android.cts.pcc.PCC_ALLOWED_PERMISSION";
    private static final String CUSTOM_PCC_DENIED_PERMISSION =
            "com.android.cts.pcc.PCC_DENIED_PERMISSION";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testIsPccProcess() {
        assertTrue(
                "Process should be a PCC process",
                Process.isPrivateComputeCoreUid(Process.myUid()));
    }

    @Test
    public void testBroadcastCanBeSentToPccProcess() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger receivedUid = new AtomicInteger(-1);
        final ITestBinder binder =
                new ITestBinder.Stub() {
                    @Override
                    public void sendUid(int uid) {
                        receivedUid.set(uid);
                        latch.countDown();
                    }
                };

        Intent intent = new Intent(PccBroadcastReceiver.ACTION_TEST_BROADCAST);
        intent.setPackage(mContext.getPackageName());
        Bundle extras = new Bundle();
        extras.putBinder(PccBroadcastReceiver.EXTRA_BINDER, binder.asBinder());
        intent.putExtras(extras);
        mContext.sendBroadcast(intent);

        assertTrue(
                "Broadcast was not received by the PCC process within the timeout",
                latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(
                "Received UID should be a PCC UID",
                Process.isPrivateComputeCoreUid(receivedUid.get()));
    }

    @Test
    public void testDynamicBroadcastReceiver() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger receivedUid = new AtomicInteger(-1);
        final String dynamicAction = "com.android.cts.pcc.processes.ACTION_DYNAMIC_BROADCAST";

        BroadcastReceiver dynamicReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (dynamicAction.equals(intent.getAction())) {
                            latch.countDown();
                        }
                    }
                };

        IntentFilter filter = new IntentFilter(dynamicAction);
        mContext.registerReceiver(dynamicReceiver, filter, Context.RECEIVER_EXPORTED);

        try {
            Intent intent = new Intent(dynamicAction);
            intent.setPackage(mContext.getPackageName());
            mContext.sendBroadcast(intent);

            assertTrue(
                    "Dynamic broadcast was not received by the PCC process within the timeout",
                    latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            mContext.unregisterReceiver(dynamicReceiver);
        }
    }

    @Test
    public void testBroadcastCanBeSentToSecondPccProcess() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger receivedUid = new AtomicInteger(-1);
        final ITestBinder binder =
                new ITestBinder.Stub() {
                    @Override
                    public void sendUid(int uid) {
                        receivedUid.set(uid);
                        latch.countDown();
                    }
                };

        Intent intent =
                new Intent(PccSecondProcessBroadcastReceiver.ACTION_TEST_BROADCAST_SECOND_PROCESS);
        intent.setPackage(mContext.getPackageName());
        Bundle extras = new Bundle();
        extras.putBinder(PccBroadcastReceiver.EXTRA_BINDER, binder.asBinder());
        intent.putExtras(extras);
        mContext.sendBroadcast(intent);

        assertTrue(
                "Broadcast was not received by the separate PCC process within the timeout",
                latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(
                "Received UID from separate process should be a PCC UID",
                Process.isPrivateComputeCoreUid(receivedUid.get()));
    }

    @Test
    public void testContentProviderInPccProcess() {
        Uri uri = Uri.parse("content://" + PccContentProvider.AUTHORITY);
        Bundle result =
                mContext.getContentResolver()
                        .call(uri, PccContentProvider.METHOD_GET_UID, null, null);
        assertNotNull("Result from content provider should not be null", result);
        int providerUid = result.getInt(PccContentProvider.KEY_UID);
        assertTrue(
                "Provider UID should be a PCC UID", Process.isPrivateComputeCoreUid(providerUid));
        assertEquals(
                "Provider should run in the same process as the test",
                Process.myUid(),
                providerUid);
    }

    @Test
    public void testContentProviderInSecondPccProcess() {
        Uri uri = Uri.parse("content://" + PccSecondProcessContentProvider.AUTHORITY_SECOND);
        Bundle result =
                mContext.getContentResolver()
                        .call(uri, PccContentProvider.METHOD_GET_UID, null, null);
        assertNotNull("Result from second content provider should not be null", result);
        int providerUid = result.getInt(PccContentProvider.KEY_UID);
        assertTrue(
                "Second provider UID should be a PCC UID",
                Process.isPrivateComputeCoreUid(providerUid));
    }

    @Test
    public void testActivityInPccProcess() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger receivedUid = new AtomicInteger(-1);
        final ITestBinder binder =
                new ITestBinder.Stub() {
                    @Override
                    public void sendUid(int uid) {
                        receivedUid.set(uid);
                        latch.countDown();
                    }
                };

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(mContext, BaseActivity.PccActivity.class));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle extras = new Bundle();
        extras.putBinder(BaseActivity.EXTRA_BINDER, binder.asBinder());
        intent.putExtras(extras);
        mContext.startActivity(intent);

        assertTrue(
                "Did not receive UID from activity within the timeout",
                latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(
                "Activity UID should be a PCC UID",
                Process.isPrivateComputeCoreUid(receivedUid.get()));
    }

    @Test
    public void testActivityInSecondPccProcess() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger receivedUid = new AtomicInteger(-1);
        final ITestBinder binder =
                new ITestBinder.Stub() {
                    @Override
                    public void sendUid(int uid) {
                        receivedUid.set(uid);
                        latch.countDown();
                    }
                };

        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(mContext, BaseActivity.PccSecondProcessActivity.class));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle extras = new Bundle();
        extras.putBinder(BaseActivity.EXTRA_BINDER, binder.asBinder());
        intent.putExtras(extras);
        mContext.startActivity(intent);

        assertTrue(
                "Did not receive UID from activity within the timeout",
                latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(
                "Activity UID should be a PCC UID",
                Process.isPrivateComputeCoreUid(receivedUid.get()));
    }

    @Test
    public void testPccActivityAlias() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger receivedUid = new AtomicInteger(-1);
        final ITestBinder binder =
                new ITestBinder.Stub() {
                    @Override
                    public void sendUid(int uid) {
                        receivedUid.set(uid);
                        latch.countDown();
                    }
                };

        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(mContext, mContext.getPackageName() + ".PccActivityAlias"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle extras = new Bundle();
        extras.putBinder(BaseActivity.EXTRA_BINDER, binder.asBinder());
        intent.putExtras(extras);
        mContext.startActivity(intent);

        assertTrue(
                "Did not receive UID from activity within the timeout",
                latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(
                "PCC activity alias UID should be a PCC UID",
                Process.isPrivateComputeCoreUid(receivedUid.get()));
    }

    @Test
    public void testPccAliasForNonPccActivity() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger receivedUid = new AtomicInteger(-1);
        final ITestBinder binder =
                new ITestBinder.Stub() {
                    @Override
                    public void sendUid(int uid) {
                        receivedUid.set(uid);
                        latch.countDown();
                    }
                };

        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        mContext, mContext.getPackageName() + ".PccAliasForNonPccActivity"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle extras = new Bundle();
        extras.putBinder(BaseActivity.EXTRA_BINDER, binder.asBinder());
        intent.putExtras(extras);
        mContext.startActivity(intent);

        assertTrue(
                "Did not receive UID from activity within the timeout",
                latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(
                "PCC alias for non-PCC activity UID should be a PCC UID",
                Process.isPrivateComputeCoreUid(receivedUid.get()));
    }

    @Test
    public void testPccAddToast_dontThrowException() throws Exception {
        Exception[] exceptions = new Exception[1];
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            try {
                                // This should be run on main thread
                                Toast.makeText(mContext, getClass().getName(), Toast.LENGTH_SHORT)
                                        .show();
                            } catch (Exception e) {
                                // Catching the exception here so that we throw it from test thread,
                                // so that JUnit could fail the test properly if an exception
                                // has been thrown
                                exceptions[0] = e;
                            }
                        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    @Test
    public void testRestrictedPccPermissions() throws Exception {
        assertTrue(
                "Process should be a PCC process",
                Process.isPrivateComputeCoreUid(Process.myUid()));

        final int allowedPermissionResult =
                mContext.checkSelfPermission(CUSTOM_PCC_ALLOWED_PERMISSION);
        assertEquals(
                "PCC process should hold a PCC allowed permission",
                allowedPermissionResult,
                PERMISSION_GRANTED);

        final int deniedPermissionResult =
                mContext.checkSelfPermission(CUSTOM_PCC_DENIED_PERMISSION);
        assertEquals(
                "PCC process should not hold a PCC-denied permission",
                deniedPermissionResult,
                PERMISSION_DENIED);
    }
}
