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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.exceptions.verification.WantedButNotInvoked;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class BluetoothHciVendorSpecificTest {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final BluetoothAdapter sAdapter = BlockingBluetoothAdapter.getAdapter();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    @Test
    public void register() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        // Check permission
        assertThrows(
                SecurityException.class,
                () ->
                        sAdapter.registerBluetoothHciVendorSpecificCallback(
                                Set.of(), sContext.getMainExecutor(), callback));

        // Check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    null, sContext.getMainExecutor(), callback));

            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(), null, callback));

            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(), sContext.getMainExecutor(), null));
        }

        // Check event codes
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(-1, 0x51, 0x60, 0xff),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x52, 0x60, 0xff),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x5f, 0xff),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x60, 0x100),
                                    sContext.getMainExecutor(),
                                    callback));
        }

        // Check multiple registration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(0, 0x51, 0x60, 0xff), sContext.getMainExecutor(), callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x60, 0xff),
                                    sContext.getMainExecutor(),
                                    mock(
                                            BluetoothAdapter.BluetoothHciVendorSpecificCallback
                                                    .class)));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }
    }

    @Test
    public void unregister() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        // Check permission
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);
        }

        assertThrows(
                SecurityException.class,
                () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    NullPointerException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(null));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check unknown unregistration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));

            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.unregisterBluetoothHciVendorSpecificCallback(
                                    mock(
                                            BluetoothAdapter.BluetoothHciVendorSpecificCallback
                                                    .class)));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check multiple unregistration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));
        }
    }

    @Test
    public void sendCommand() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        // Check permission
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);
        }

        assertThrows(
                SecurityException.class,
                () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, new byte[] {}));

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    NullPointerException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, null));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check unregistered callbacks
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, new byte[0]));
        }

        // Check ocf values
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(-1, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0x150, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0x15f, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0x400, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, new byte[256]));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }
    }

    @Test
    public void getVendorCapabilities() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        ArgumentCaptor<byte[]> return_parameters = ArgumentCaptor.forClass(byte[].class);

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            // Send the command Get Vendor Capabilities.
            sAdapter.sendBluetoothHciVendorSpecificCommand(0x153, new byte[] {});

            try {
                verify(callback, timeout(1_000))
                        .onCommandComplete(eq(0x153), return_parameters.capture());

            } catch (WantedButNotInvoked e) {
                // If the command complete event is not received, the controller should have sent a
                // command status event with status UNKNOWN_HCI_COMMAND instead.
                verify(callback).onCommandStatus(eq(0x153), eq(0x1));
                return;
            }

            // The command complete event should include at least 1 byte for the status code.
            assertThat(return_parameters.getValue().length).isAtLeast(1);

            // If the status code is SUCCESS, the response should include at least 9 bytes
            // as specified in the first version of the Get Vendor Capabilities command.
            int status = return_parameters.getValue()[0];
            int length_until_version_number = 9;
            if (status == 0) {
                assertThat(return_parameters.getValue().length)
                        .isAtLeast(length_until_version_number);
            }

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_REPORT_VENDOR_EVENTS_FROM_ACL)
    @Test
    public void register_withAclHandleSet() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        // Check permission
        assertThrows(
                SecurityException.class,
                () ->
                        sAdapter.registerBluetoothHciVendorSpecificCallback(
                                Set.of(), Set.of(), sContext.getMainExecutor(), callback));

        // Check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    null, Set.of(), sContext.getMainExecutor(), callback));

            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(), Set.of(), null, callback));

            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(), Set.of(), sContext.getMainExecutor(), null));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(), null, sContext.getMainExecutor(), callback));
        }

        // Check event codes
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(-1, 0x51, 0x60, 0xff),
                                    Set.of(),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x52, 0x60, 0xff),
                                    Set.of(),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x5f, 0xff),
                                    Set.of(),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x60, 0x100),
                                    Set.of(),
                                    sContext.getMainExecutor(),
                                    callback));
        }

        // Check acl handles
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(),
                                    Set.of(0, 0x123, 0x456, 0x789, 0xfff),
                                    sContext.getMainExecutor(),
                                    callback));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(),
                                    Set.of(-1, 0x123, 0x456, 0x789, 0xfff),
                                    sContext.getMainExecutor(),
                                    callback));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(),
                                    Set.of(0x001, 0x123, 0x456, 0x789, 0x1000),
                                    sContext.getMainExecutor(),
                                    callback));
        }

        // Check multiple registration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(0, 0x51, 0x60, 0xff),
                    Set.of(1, 0xfff),
                    sContext.getMainExecutor(),
                    callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(),
                                    sContext.getMainExecutor(),
                                    mock(
                                            BluetoothAdapter.BluetoothHciVendorSpecificCallback
                                                    .class)));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(),
                                    Set.of(),
                                    sContext.getMainExecutor(),
                                    mock(
                                            BluetoothAdapter.BluetoothHciVendorSpecificCallback
                                                    .class)));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_REPORT_VENDOR_EVENTS_FROM_ACL)
    @Test
    public void unregister_withAclHandleSetRegistered() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(0x01), Set.of(0x001), sContext.getMainExecutor(), callback);
        }
        // check permission
        assertThrows(
                SecurityException.class,
                () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    NullPointerException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(null));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // check unknown unregistration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));

            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.unregisterBluetoothHciVendorSpecificCallback(
                                    mock(
                                            BluetoothAdapter.BluetoothHciVendorSpecificCallback
                                                    .class)));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check multiple unregistration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), Set.of(), sContext.getMainExecutor(), callback);

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));
        }
    }

    // Android doesn't provide method without side-effect and therefore this is not testable in CTS
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        callback.onCommandStatus(0, 0);
        callback.onEvent(0, null);
    }

    // Android doesn't provide method without side-effect and therefore this is not testable in CTS
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    // TODO: Remove this test once the flag is fully rolled out.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REPORT_VENDOR_EVENTS_FROM_ACL)
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage_onAclEvent() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);
        callback.onAclEvent(0, null);
    }
}
