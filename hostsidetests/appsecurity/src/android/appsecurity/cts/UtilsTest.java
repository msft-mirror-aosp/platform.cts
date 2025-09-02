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
package android.appsecurity.cts;

import static android.appsecurity.cts.Utils.getAllUsers;

import static com.android.tradefed.device.UserInfo.USER_SYSTEM;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Run as {@code atest AppSecurityHostUnitTestCases:UtilsTest}. */
public final class UtilsTest {

    @Rule public final Expect expect = Expect.create();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock private ITestDevice mMockDevice;

    @Test
    public void testGetAllUsers_null() {
        assertThrows(NullPointerException.class, () -> getAllUsers(null));
    }

    // TODO(b/380907032): add tests for invalid cases like empty users (current code doesn't handle
    // it)

    @Test
    public void testGetAllUsers_oneUser() throws Exception {
        mockListUsers(USER_SYSTEM);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(USER_SYSTEM);
    }

    @Test
    public void testGetMultipleUsers_hsum_mainUserFirst() throws Exception {
        mockHsum(true);
        mockMainUserId(42);
        mockListUsers(USER_SYSTEM, 4, 8, 15, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(42, 4, 8, 15, 16, 23)
                .inOrder();
    }

    @Test
    public void testGetMultipleUsers_interactivehsum_mainUserFirst() throws Exception {
        mockInteractiveHsum(true);
        mockMainUserId(42);
        mockListUsers(USER_SYSTEM, 4, 8, 15, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(USER_SYSTEM, 4, 8, 15, 16, 23, 42)
                .inOrder();
    }

    @Test
    public void testGetMultipleUsers_nonHsum_systemUserFirst() throws Exception {
        mockHsum(false);
        mockMainUserId(42);
        mockListUsers(USER_SYSTEM, 4, 8, 15, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(USER_SYSTEM, 4, 8, 15, 16, 23, 42)
                .inOrder();
    }

    private void mockListUsers(int... userIds) throws DeviceNotAvailableException {
        var ids = Arrays.stream(userIds).boxed().collect(Collectors.toCollection(ArrayList::new));
        when(mMockDevice.listUsers()).thenReturn(ids);
    }

    private void mockHsum(boolean value) throws DeviceNotAvailableException {
        when(mMockDevice.isHeadlessSystemUserMode()).thenReturn(value);
    }

    private void mockInteractiveHsum(boolean value) throws DeviceNotAvailableException {
        mockHsum(true);
        when(mMockDevice.canSwitchToHeadlessSystemUser()).thenReturn(value);
    }

    private void mockMainUserId(int userId) throws DeviceNotAvailableException {
        when(mMockDevice.getMainUserId()).thenReturn(userId);
    }
}
