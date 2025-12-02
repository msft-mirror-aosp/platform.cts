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

package com.android.cts.pcc.featuretests;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.privatecompute.PccSandboxManager;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerTest {
    private PccSandboxManager mPccSandboxManager;
    private Context mContext;

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPccSandboxManager = mContext.getSystemService(PccSandboxManager.class);
        assertNotNull("PccSandboxManager service not available", mPccSandboxManager);
    }

    @Test
    public void testIsPrivateComputeServicesUid_forNonAppUid_returnsFalse() {
        assertFalse(
                "PCS UID must be in the Application UID range",
                mPccSandboxManager.isPrivateComputeServicesUid(Process.LAST_APPLICATION_UID + 1));
        assertFalse(
                "PCS UID must be in the Application UID range",
                mPccSandboxManager.isPrivateComputeServicesUid(Process.FIRST_APPLICATION_UID - 1));
    }

    @Test
    public void testIsPrivateComputeServicesUid_forSystemUid_returnsFalse() {
        assertFalse(
                "PCC UID should not be a PCS process",
                mPccSandboxManager.isPrivateComputeServicesUid(Process.SYSTEM_UID));
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES)
    public void testIsPrivateComputeServicesUid_withoutPermission_returnsFalse() {
        int testAppUid = Process.myUid();
        assertFalse(
                "isPrivateComputeServicesUid should return false for UID without the permission",
                mPccSandboxManager.isPrivateComputeServicesUid(testAppUid));
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES)
    public void testIsPrivateComputeServicesUid_withPermission_returnsTrue() {
        int testAppUid = Process.myUid();
        assertTrue(
                "isPrivateComputeServicesUid should return true for UID with the permission",
                mPccSandboxManager.isPrivateComputeServicesUid(testAppUid));
    }

    @Test
    public void testWriteToAuditLog_exampleBundle_doesNotThrowsException() {
        PersistableBundle data = new PersistableBundle();
        data.putString("test_key", "test_value");

        mPccSandboxManager.writeToAuditLog(data);

        // By design, this API does not provide any feedback to the caller.
        // We are just checking that it does not throw an exception.
    }

    @Test
    public void testWriteToAuditLog_emptyBundle_doesNotThrowsException() {
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());

        // By design, this API does not provide any feedback to the caller.
        // We are just checking that it does not throw an exception.
    }
}
