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
import static android.appsecurity.cts.Utils.getFirstNonSystemUserId;

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

import javax.annotation.Nullable;

/** Run as {@code atest AppSecurityHostUnitTestCases:UtilsTest}. */
public final class UtilsTest {

    @Rule public final Expect expect = Expect.create();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock private ITestDevice mMockDevice;

    @Test
    public void testGetAllUsers_null() {
        assertThrows(NullPointerException.class, () -> getAllUsers(null));
    }

    @Test
    public void testGetAllUsers_nullUsers() throws Exception {
        mockListUsers(null);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(USER_SYSTEM);
    }

    @Test
    public void testGetAllUsers_emptyUsers() throws Exception {
        mockListUsers();

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(USER_SYSTEM);
    }

    @Test
    public void testGetAllUsers_currentUserNotFound() throws Exception {
        mockCurrentUser(108);
        mockListUsers(4, 8, 15, USER_SYSTEM, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(USER_SYSTEM, 4, 8, 15, 16, 23, 42)
                .inOrder();
    }

    @Test
    public void testGetAllUsers_currentUserAndSystemUserNotFound() throws Exception {
        mockCurrentUser(108);
        mockListUsers(4, 8, 15, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(USER_SYSTEM, 4, 8, 15, 16, 23, 42)
                .inOrder();
    }

    @Test
    public void testGetAllUsers_oneUser() throws Exception {
        mockCurrentUser(42);
        mockListUsers(42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(42);
    }

    @Test
    public void testGetMultipleUsers_currentUserFirst() throws Exception {
        mockCurrentUser(4);
        mockListUsers(4, 8, 15, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(4, 8, 15, 16, 23, 42)
                .inOrder();
    }

    @Test
    public void testGetMultipleUsers_currentUserOnMiddle() throws Exception {
        mockCurrentUser(16);
        mockListUsers(4, 8, 15, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(16, 4, 8, 15, 23, 42)
                .inOrder();
    }

    @Test
    public void testGetMultipleUsers_currentUserLast() throws Exception {
        mockCurrentUser(42);
        mockListUsers(4, 8, 15, 16, 23, 42);

        expect.withMessage("getAllUsers()")
                .that(getAllUsers(mMockDevice))
                .asList()
                .containsExactly(42, 4, 8, 15, 16, 23)
                .inOrder();
    }

    @Test
    public void testGetFirstNonSystemUserId_null() {
        assertThrows(NullPointerException.class, () -> getFirstNonSystemUserId(null));
    }

    @Test
    public void testGetFirstNonSystemUserId_notFound() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> getFirstNonSystemUserId());
        expect.withMessage("exception message")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo("Not found. Users: []");

        thrown =
                assertThrows(
                        IllegalArgumentException.class, () -> getFirstNonSystemUserId(USER_SYSTEM));
        expect.withMessage("exception message")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo("Not found. Users: [0]");
    }

    @Test
    public void testGetFirstNonSystemUserId() {
        expect.withMessage("getFirstNonSystemUserId(42)")
                .that(getFirstNonSystemUserId(42))
                .isEqualTo(42);

        expect.withMessage("getFirstNonSystemUserId(%s, 42)", USER_SYSTEM)
                .that(getFirstNonSystemUserId(USER_SYSTEM, 42))
                .isEqualTo(42);

        expect.withMessage("getFirstNonSystemUserId(42, %s)", USER_SYSTEM)
                .that(getFirstNonSystemUserId(42, USER_SYSTEM))
                .isEqualTo(42);
    }

    private void mockListUsers(@Nullable int... userIds) throws DeviceNotAvailableException {
        if (userIds == null) {
            when(mMockDevice.listUsers()).thenReturn(null);
            return;
        }
        var ids = Arrays.stream(userIds).boxed().collect(Collectors.toCollection(ArrayList::new));
        when(mMockDevice.listUsers()).thenReturn(ids);
    }

    private void mockCurrentUser(int userId) throws DeviceNotAvailableException {
        when(mMockDevice.getCurrentUser()).thenReturn(userId);
    }
}
