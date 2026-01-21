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

package android.security.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IRecoverySystem;
import android.os.RecoverySystem;
import android.os.UserManager;
import android.os.image.DynamicSystemManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48614 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 430568718)
    public void testPocCVE_2025_48614() {
        try {
            final AtomicBoolean wipeDataCommandReceived = new AtomicBoolean(false);
            try {
                // Use reflection to invoke the vulnerable method 'rebootWipeUserData()'.
                RecoverySystem.class
                        .getMethod(
                                "rebootWipeUserData",
                                Context.class,
                                boolean.class,
                                String.class,
                                boolean.class,
                                boolean.class)
                        .invoke(
                                null,
                                createMockContext(wipeDataCommandReceived),
                                false /* shutdown */,
                                "cve_2025_48614_reason",
                                true /* force */,
                                false /* wipeEuicc */);
            } catch (SecurityException se) {
                // With the fix, a SecurityException is thrown; return immediately.
                return;
            } catch (Throwable throwable) {
                // Ignore Other Exception
            }
            assertWithMessage(
                            "Device is vulnerable to b/430568718"
                                    + "!! Wiping data is not allowed while in DSU mode.")
                    .that(wipeDataCommandReceived.get())
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private Context createMockContext(AtomicBoolean wipeDataCommandReceived) throws Exception {
        // Create mock objects for the dependencies
        final UserManager mockUserManager = mock(UserManager.class);
        final DynamicSystemManager mockDynamicSystemManager = mock(DynamicSystemManager.class);
        final IRecoverySystem mockIRecoverySystem = mock(IRecoverySystem.class);

        // Mock getSystemService and getContentResolver calls
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.USER_SERVICE)).thenReturn(mockUserManager);
        when(mockContext.getSystemService(Context.DYNAMIC_SYSTEM_SERVICE))
                .thenReturn(mockDynamicSystemManager);
        when(mockContext.getSystemService(Context.RECOVERY_SERVICE))
                .thenReturn(new RecoverySystem(mockIRecoverySystem));
        when(mockContext.getContentResolver())
                .thenReturn(getInstrumentation().getContext().getContentResolver());

        // Ensure the DSU mode check is in use, return true
        when(mockDynamicSystemManager.isInUse()).thenReturn(true);

        // Mock the ordered broadcast to immediately call the receiver, bypassing
        // condition.block()
        doAnswer(
                        invocation -> {
                            final BroadcastReceiver receiver =
                                    invocation.getArgument(3 /* BroadcastReceiver */);
                            receiver.onReceive(mockContext, new Intent());
                            return null;
                        })
                .when(mockContext)
                .sendOrderedBroadcastAsUser(
                        any(Intent.class),
                        any(),
                        anyString(),
                        any(BroadcastReceiver.class),
                        any(),
                        anyInt(),
                        any(),
                        any());

        // Mock rebootRecoveryWithCommand on the binder object and check for "--wipe_data"
        doAnswer(
                        invocation -> {
                            String command = invocation.getArgument(0 /* command */);
                            if (command != null && command.contains("--wipe_data")) {
                                wipeDataCommandReceived.set(true);
                            }
                            return null;
                        })
                .when(mockIRecoverySystem)
                .rebootRecoveryWithCommand(anyString());

        return mockContext;
    }
}
