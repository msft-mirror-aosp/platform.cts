/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.car.cts;

import static android.os.Process.myUid;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissions;
import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn;
import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.car.Car;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CarUserManagerTest extends CarApiTestBase {

    private static final String TAG = CarUserManagerTest.class.getSimpleName();

    /**
     * Constant used to wait for a condition that is triggered by checking a condition.
     */
    private static final int SWITCH_TIMEOUT_USING_CHECK_MS = 30_000;

    /**
     * Constant used to wait blindly, when there is no condition that can be checked.
     */
    private static final int SWITCH_TIMEOUT_WITHOUT_CHECK_MS = 10_000;

    /**
     * How long to sleep (multiple times) while waiting for a condition.
     */
    private static final int SMALL_NAP_MS = 500;

    private static CarUserManager sCarUserManager;

    private static int sInitialUserId = UserHandle.myUserId();
    private static int sNewUserId = UserHandle.USER_NULL;

    private final Executor mNoOpExecutor = (r) -> {};
    private final UserLifecycleListener mNoOpListener = (e) -> {};

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // TODO: ideally it should be created on @BeforeClass, but it relies on getCar()
        if (sNewUserId != UserHandle.USER_NULL) {
            Log.d(TAG, "setUp(): already set static stuff");
            return;
        }

        sCarUserManager = (CarUserManager) getCar().getCarManager(Car.CAR_USER_SERVICE);
        sNewUserId = createNewUser("CarUserManagerTest");
        Log.i(TAG, "setUp(): myUid=" + myUid() + ", currentUser=" + sInitialUserId
                + ", newUser=" + sNewUserId);
    }

    @AfterClass
    public static void resetUsers() {
        switchUser(sInitialUserId);
        if (sNewUserId != UserHandle.USER_NULL) {
            removeUser(sNewUserId);
        }
    }

    @Test
    public void testAddListener_noPermission() throws Exception {
        toggleInteractAcrossUsersPermission(false);
        try {
            assertThrows(SecurityException.class,
                    () -> sCarUserManager.addListener(mNoOpExecutor, mNoOpListener));
        } finally {
            toggleInteractAcrossUsersPermission(true);
        }
    }

    @Test
    public void testRemoveListener_noPermission() throws Exception {
        toggleInteractAcrossUsersPermission(false);
        try {
            assertThrows(SecurityException.class,
                    ()-> sCarUserManager.removeListener(mNoOpListener));
        } finally {
            toggleInteractAcrossUsersPermission(true);
        }
    }

    // TODO(b/144120654): tag with @CddTest
    @Test
    public void testLifecycleListener() throws Exception {
        // TODO(b/144120654): listen to other event types

        int oldUserId = sInitialUserId;
        int newUserId = sNewUserId;

        // TODO: move listener to its own class (if it becomes too hard to read)
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> bgExceptionRef = new AtomicReference<>();
        AtomicBoolean expectingEventRef = new AtomicBoolean(true);

        UserLifecycleListener listener = (event) -> {
            boolean expectingEvent = expectingEventRef.get();
            Log.d(TAG, "received event (expecting=" + expectingEvent + "): "  + event);
            latch.countDown();
            if (!expectingEvent) {
                bgExceptionRef.set(new IllegalStateException("Received event when it shouldn't: "
                        + event));
                return;
            }
            // Verify event
            List<String> errors = new ArrayList<>();
            int actualType = event.getEventType();
            if (actualType != CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING) {
                errors.add("wrong event; expected SWITCHING, got "
                        + CarUserManager.lifecycleEventTypeToString(actualType));
            }
            UserHandle actualUserHandle = event.getUserHandle();
            if (actualUserHandle == null) {
                errors.add("no user handle");
            } else if (actualUserHandle.getIdentifier() != newUserId) {
                errors.add("wrong user: expected " + newUserId + ", got " + actualUserHandle);
            }

            // TODO(b/144120654): check for previous handle (not set yet)
            if (false) {
                UserHandle previousUserHandle = event.getPreviousUserHandle();
                if (previousUserHandle == null) {
                    errors.add("no previous user handle");
                } else if (previousUserHandle.getIdentifier() != oldUserId) {
                    errors.add("wrong previous user: expected " + oldUserId + ", got "
                            + previousUserHandle);
                }
            }

            if (!errors.isEmpty()) {
                bgExceptionRef.set(new IllegalArgumentException(
                        "Received wrong event (" + event + "): " + errors));
            }
        };
        Log.d(TAG, "registering listener: " + listener);


        AtomicBoolean executedRef = new AtomicBoolean();
        sCarUserManager.addListener((r) -> {
            executedRef.set(true);
            r.run();
        }, listener);

        // Switch while listener is registered
        switchUser(newUserId);
        if (!latch.await(SWITCH_TIMEOUT_USING_CHECK_MS, TimeUnit.MILLISECONDS)) {
            fail("listener not called in " + SWITCH_TIMEOUT_USING_CHECK_MS + "ms");
        }

        // Make sure it was executed in the proper threaqd
        assertWithMessage("not executed on executor").that(executedRef.get()).isTrue();

         // Then switch back when it isn't
        // TODO(b/144120654): the current mechanism is not thread safe because if an event is
        // received before this line, it wouldn't be detected. But that's fine for now, as this test
        // will be refactored once it's expecting more events (like STARTING before SWITCHING)
        expectingEventRef.set(false);

        Log.d(TAG, "unregistering listener: " + listener);
        sCarUserManager.removeListener(listener);
        switchUser(oldUserId);
        // Wait until it's switched...
        waitForCurrentUser(oldUserId, SWITCH_TIMEOUT_USING_CHECK_MS);
        // .. then a little bit longer to make sure the callback was not called
        SystemClock.sleep(SWITCH_TIMEOUT_WITHOUT_CHECK_MS);

        Exception bgException = bgExceptionRef.get();
        if (bgException != null) {
            throw bgException;
        }
    }

    /**
     * Used to temporarily revoke the permission.
     */
    private void toggleInteractAcrossUsersPermission(boolean enabled) {
        String permission = "android.permission.INTERACT_ACROSS_USERS";
        String pkgName = sContext.getPackageName();
        UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        if (enabled) {
            Log.d(TAG,"re-enabling " + permission + " to " + pkgName);
            invokeMethodWithShellPermissionsNoReturn(automan, (a) -> a
                    .grantRuntimePermission(pkgName, permission));
        } else {
            Log.d(TAG,"temporarily disabing " + permission + " for " + pkgName);
            invokeMethodWithShellPermissionsNoReturn(automan, (a) -> a
                    .revokeRuntimePermission(pkgName, permission));
        }
    }

    /**
     * Creates a new Android user.
     */
    private int createNewUser(String name) {
        Log.i(TAG, "Creating new user " + name);
        int newUserId = invokeMethodWithShellPermissions(sCarUserManager,
                (um) -> um.createUser(name));
        Log.i(TAG, "New user created with id " + newUserId);
        return newUserId;
    }

    /**
     * Removes an Android user.
     */
    private static void removeUser(int userId) {
        Log.i(TAG, "Removing user " + userId);
        invokeMethodWithShellPermissionsNoReturn(sCarUserManager, (um) -> um.removeUser(userId));
    }

    /**
     * Switches to the given Android user.
     */
    private static void switchUser(int userId) {
        Log.i(TAG, "Switching to user " + userId);
        ActivityManager activityManager = sContext.getSystemService(ActivityManager.class);
        boolean success = invokeMethodWithShellPermissions(activityManager,
                (am) -> am.switchUser(UserHandle.of(userId)));
        if (!success) {
            fail("Could not switch to user " + userId + " using ActivityManager");
        }
    }

    /**
     * Waits until the current Android user is {@code userId}, or fail if it times out.
     */
    private static void waitForCurrentUser(int userId, long timeoutMs) {
        Log.i(TAG, "Waiting until current user is " + userId);
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            int actualUserId = getCurrentUser();
            if (actualUserId == userId) {
                Log.d(TAG, "the wait is over!");
                return;
            }
            Log.d(TAG, "still on " + actualUserId + "; sleeping " + SMALL_NAP_MS + "ms");
            SystemClock.sleep(SMALL_NAP_MS);
        } while (System.currentTimeMillis() < deadline);
        fail("didn't switch to user " + userId + " in " + timeoutMs + " ms");
    }

    private static int getCurrentUser() {
        // TODO: should use Activity.getCurrentUser(), but that's a @SystemApi (not @TestApi)
        return Integer.parseInt(runShellCommand("am get-current-user"));
    }
}
