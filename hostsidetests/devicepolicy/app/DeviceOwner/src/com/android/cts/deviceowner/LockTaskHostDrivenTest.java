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
package com.android.cts.deviceowner;

import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;

import static com.android.cts.locktask.LockTaskActivityStateHelper.EXTRA_START_LOCK_TASK;
import static com.android.cts.locktask.LockTaskActivityStateHelper.LOCK_TASK_ACTIVITY;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.locktask.LockTaskActivityStateHelper;

/**
 * Test class that is meant to be driven from the host and can't be run alone, which is required for
 * tests that include rebooting or other connection-breaking steps. For this reason, this class does
 * not override tearDown and setUp just initializes the test state, changing nothing in the device.
 * Therefore, the host is responsible for making sure the tests leave the device in a clean state
 * after running.
 */
public class LockTaskHostDrivenTest extends BaseDeviceOwnerTest {

    private static final String TAG = LockTaskHostDrivenTest.class.getName();
    private static final int LOCK_TASK_TIMEOUT = 20000; // 20 seconds

    private ActivityManager mAm;
    private DevicePolicyManager mDpm;

    /** Sets up the test. */
    public void setUp() {
        mAm = mContext.getSystemService(ActivityManager.class);
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
    }

    public void testStartLockTask() {
        Log.d(TAG, "testStartLockTask_noAsserts on host-driven test (no cleanup)");
        setLockTaskPackages(mContext.getPackageName(), LOCK_TASK_ACTIVITY.getPackageName());
        setDefaultHomeActivity();
        launchLockTaskActivity();

        waitForLockTaskActivityResumed();
        waitForLockTaskModeLocked();
    }

    public void testCleanupLockTask_noAsserts() {
        Log.d(TAG, "testCleanupLockTask_noAsserts on host-driven test");
        clearDefaultHomeActivity();
        setLockTaskPackages();
        mDpm.setLockTaskFeatures(getWho(), 0);
    }

    public void testLockTaskIsActive() throws Exception {
        Log.d(TAG, "testLockTaskActive on host-driven test");
        waitForLockTaskActivityResumed();
        waitForLockTaskModeLocked();
    }

    private void waitForLockTaskModeLocked() {
        PollingCheck.waitFor(
                LOCK_TASK_TIMEOUT, () -> mAm.getLockTaskModeState() == LOCK_TASK_MODE_LOCKED);
    }

    private void launchLockTaskActivity() {
        Intent intent = new Intent();
        intent.setComponent(LOCK_TASK_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_START_LOCK_TASK, true);
        mContext.startActivity(intent);
    }

    private void waitForLockTaskActivityResumed() {
        PollingCheck.waitFor(
                LOCK_TASK_TIMEOUT,
                () -> LockTaskActivityStateHelper.isLockTaskActivityResumed(mContext),
                "Lock task activity wasn't resumed in time");
    }

    private void setLockTaskPackages(String... packages) {
        mDpm.setLockTaskPackages(getWho(), packages);
    }

    private void setDefaultHomeActivity() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        intentFilter.addCategory(Intent.CATEGORY_HOME);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        mDpm.addPersistentPreferredActivity(getWho(), intentFilter, LOCK_TASK_ACTIVITY);
    }

    private void clearDefaultHomeActivity() {
        mDpm.clearPackagePersistentPreferredActivities(
                getWho(), LOCK_TASK_ACTIVITY.getPackageName());
    }
}
