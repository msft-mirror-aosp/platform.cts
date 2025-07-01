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

package android.server.wm.activity;

import static android.server.wm.second.Components.SECOND_TRAMPOLINE_ACTIVITY;
import static android.server.wm.second.Components.SECOND_TRANSLUCENT_FLOATING_ACTIVITY;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.second.Components;

import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

public abstract class ActivityRecordInputSinkTestsBase extends ActivityManagerTestBase {
    final ComponentName mTestActivity =
            new ComponentName(mContext, ActivityRecordInputSinkTestsActivity.class);
    static final ComponentName OVERLAY_IN_DIFFERENT_UID = SECOND_TRANSLUCENT_FLOATING_ACTIVITY;
    static final ComponentName TRAMPOLINE_DIFFERENT_UID = SECOND_TRAMPOLINE_ACTIVITY;

    private int mTouchCount;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        ActivityRecordInputSinkTestsActivity.sButtonClickCount.set(0);
    }

    @After
    public void tearDown() {
        Components.forceStopPackage();
        mWmState.waitForAppTransitionIdleOnDisplay(getMainDisplayId());
    }

    void launchActivityInSameTask(ComponentName componentName) {
        launchActivityInSameTask(componentName,  /* extras */ null);
    }

    void launchActivityInSameTask(ComponentName componentName, @Nullable Bundle extras) {
        launchActivityInSameTask(componentName, extras, /* options */ null);
    }

    void launchActivityInSameTask(
            ComponentName componentName, @Nullable Bundle extras, @Nullable Bundle options) {
        final Intent intent = new Intent(ActivityRecordInputSinkTestsActivity.LAUNCH_ACTIVITY_ACTION);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(ActivityRecordInputSinkTestsActivity.COMPONENT_EXTRA, componentName);
        intent.putExtra(ActivityRecordInputSinkTestsActivity.EXTRA_EXTRA, extras);
        intent.putExtra(ActivityRecordInputSinkTestsActivity.EXTRA_OPTIONS, options);
        mContext.sendBroadcast(intent);
    }


    void touchButtonsAndAssert(boolean expectTouchesToReachActivity) {
        touchButtonsAndAssert(expectTouchesToReachActivity, true /* waitForAnimation */);
    }

    void touchButtonsAndAssert(boolean expectTouchesToReachActivity, boolean waitForAnimation) {
        final WindowManagerState.Activity activity = mWmState.getActivity(mTestActivity);
        final int displayId = activity.getTask().mDisplayId;
        final Rect bounds = activity.getBounds();
        bounds.offset(0, -bounds.height() / 3);
        mTouchHelper.tapOnCenter(bounds, displayId, waitForAnimation);
        mTouchCount += (expectTouchesToReachActivity ? 1 : 0);
        mInstrumentation.waitForIdleSync();
        assertThat(ActivityRecordInputSinkTestsActivity.sButtonClickCount.get())
                .isEqualTo(mTouchCount);

        bounds.offset(0, 2 * bounds.height() / 3);
        mTouchHelper.tapOnCenter(bounds, displayId, waitForAnimation);
        mTouchCount += (expectTouchesToReachActivity ? 1 : 0);
        mInstrumentation.waitForIdleSync();
        assertThat(ActivityRecordInputSinkTestsActivity.sButtonClickCount.get())
                .isEqualTo(mTouchCount);
    }
}
