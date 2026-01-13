/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.telephony.cts;

import static android.service.messaging.AlternativeMessageTransportService.UPGRADE_STATUS_ACCEPTED;

import static com.android.internal.telephony.flags.Flags.FLAG_MESSAGE_PROMOTION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.role.RoleManager;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.MessageUpgradeController;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class MessageUpgradeControllerTest {
    private static final long TIMEOUT_MS = 10000;
    private static final String MESSAGE_UPGRADE_TEST_APP_PACKAGE =
            "android.telephony.cts.msgupgrade";

    private Context mContext;
    private MessageUpgradeController mController;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mController = new MessageUpgradeController(mContext);
        setSmsRole(MESSAGE_UPGRADE_TEST_APP_PACKAGE);
    }

    @After
    public void tearDown() throws Exception {
        setSmsRole(null);
    }

    /** Tests that the device properly upgrades an SMS/MMS message. */
    @Test
    @RequiresFlagsEnabled(FLAG_MESSAGE_PROMOTION)
    public void testPromoteMessage() {
        final Uri contentUri = Uri.parse("content://mms/123");

        CompletableFuture<Integer> upgradeStatusFuture = new CompletableFuture<>();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mController,
                (controller) ->
                        controller.upgradeMessage(
                                contentUri, Runnable::run, upgradeStatusFuture::complete));

        try {
            int upgradeStatus = upgradeStatusFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertEquals(
                    "PromotionResult should be UPGRADE_STATUS_ACCEPTED",
                    UPGRADE_STATUS_ACCEPTED,
                    upgradeStatus);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("upgradeMessage timed out or failed to receive upgrade status: " + e.getMessage());
        }
    }

    private void setSmsRole(String packageName) throws Exception {
        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (packageName == null) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    roleManager,
                    (rm) ->
                            rm.removeRoleHolderAsUser(
                                    RoleManager.ROLE_SMS,
                                    MESSAGE_UPGRADE_TEST_APP_PACKAGE,
                                    0,
                                    Process.myUserHandle(),
                                    Runnable::run,
                                    future::complete));
        } else {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    roleManager,
                    (rm) ->
                            rm.addRoleHolderAsUser(
                                    RoleManager.ROLE_SMS,
                                    packageName,
                                    0,
                                    Process.myUserHandle(),
                                    Runnable::run,
                                    future::complete));
        }
        assertTrue("Failed to set SMS role holder", future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }
}
