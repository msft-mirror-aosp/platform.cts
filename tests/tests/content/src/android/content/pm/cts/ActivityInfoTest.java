/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.StringBuilderPrinter;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ActivityInfo}.
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ActivityInfoTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final String TEST_PKG = "android.content.cts";
    private static final String TEST_ACTIVITY = TEST_PKG + ".MockActivity";

    ActivityInfo mActivityInfo;

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testSimple() {
        ActivityInfo info = new ActivityInfo();
        new ActivityInfo(info);
        assertNotNull(info.toString());
        info.dump(new StringBuilderPrinter(new StringBuilder()), "");
    }

    @Test
    public void testConstructor() {
        new ActivityInfo();

        ActivityInfo info = new ActivityInfo();
        new ActivityInfo(info);

        try {
            new ActivityInfo(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    public void testWriteToParcel() throws NameNotFoundException {
        ComponentName componentName = new ComponentName(TEST_PKG, TEST_ACTIVITY);

        mActivityInfo = getContext().getPackageManager().getActivityInfo(
                componentName, PackageManager.GET_META_DATA);

        Parcel p = Parcel.obtain();
        mActivityInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(p);
        assertInfosAreEqual(mActivityInfo, info);

        try {
            mActivityInfo.writeToParcel(null, 0);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    public void testCopyConstructor() throws NameNotFoundException {
        ComponentName componentName = new ComponentName(TEST_PKG, TEST_ACTIVITY);

        mActivityInfo = getContext().getPackageManager().getActivityInfo(
                componentName, PackageManager.GET_META_DATA);

        ActivityInfo info = new ActivityInfo(mActivityInfo);
        assertInfosAreEqual(mActivityInfo, info);

        try {
            mActivityInfo.writeToParcel(null, 0);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    public void testGetThemeResource() throws NameNotFoundException {
        ComponentName componentName = new ComponentName(TEST_PKG, TEST_ACTIVITY);

        mActivityInfo = getContext().getPackageManager().getActivityInfo(
                componentName, PackageManager.GET_META_DATA);

        assertEquals(mActivityInfo.applicationInfo.theme, mActivityInfo.getThemeResource());
        mActivityInfo.theme = 1;
        assertEquals(mActivityInfo.theme, mActivityInfo.getThemeResource());
    }

    @Test
    public void testToString() throws NameNotFoundException {
        mActivityInfo = new ActivityInfo();
        assertNotNull(mActivityInfo.toString());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    public void testDescribeContents() throws NameNotFoundException {
        mActivityInfo = new ActivityInfo();
        assertEquals(0, mActivityInfo.describeContents());

        ComponentName componentName = new ComponentName(TEST_PKG, TEST_ACTIVITY);

        mActivityInfo = getContext().getPackageManager().getActivityInfo(
                componentName, PackageManager.GET_META_DATA);

        assertEquals(0, mActivityInfo.describeContents());
    }

    @Test
    public void testDump() {
        mActivityInfo = new ActivityInfo();

        StringBuilder sb = new StringBuilder();
        assertEquals(0, sb.length());
        StringBuilderPrinter p = new StringBuilderPrinter(sb);

        String prefix = "";
        mActivityInfo.dump(p, prefix);

        assertNotNull(sb.toString());
        assertTrue(sb.length() > 0);

        try {
            mActivityInfo.dump(null, "");
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
            // expected
        }
    }

    /**
     * Asserts that the infos provided are equal.
     */
    private static void assertInfosAreEqual(ActivityInfo info1, ActivityInfo info2) {
        assertEquals(info1.theme, info2.theme);
        assertEquals(info1.launchMode, info2.launchMode);
        assertEquals(info1.documentLaunchMode, info2.documentLaunchMode);
        assertEquals(info1.permission, info2.permission);
        assertEquals(info1.getKnownActivityEmbeddingCerts(),
                info2.getKnownActivityEmbeddingCerts());
        assertEquals(info1.taskAffinity, info2.taskAffinity);
        assertEquals(info1.targetActivity, info2.targetActivity);
        assertEquals(info1.flags, info2.flags);
        assertEquals(info1.privateFlags, info2.privateFlags);
        assertEquals(info1.screenOrientation, info2.screenOrientation);
        assertEquals(info1.configChanges, info2.configChanges);
        assertEquals(info1.softInputMode, info2.softInputMode);
        assertEquals(info1.uiOptions, info2.uiOptions);
        assertEquals(info1.parentActivityName, info2.parentActivityName);
        assertEquals(info1.maxRecents, info2.maxRecents);
        assertEquals(info1.lockTaskLaunchMode, info2.lockTaskLaunchMode);
        assertEquals(info1.windowLayout, info2.windowLayout);
        assertEquals(info1.resizeMode, info2.resizeMode);
        assertEquals(info1.requestedVrComponent, info2.requestedVrComponent);
        assertEquals(info1.rotationAnimation, info2.rotationAnimation);
        assertEquals(info1.colorMode, info2.colorMode);
        assertEquals(info1.getMaxAspectRatio(), info2.getMaxAspectRatio(), Math.ulp(1f));
        assertEquals(info1.getMinAspectRatio(), info2.getMinAspectRatio(), Math.ulp(1f));
        assertEquals(info1.supportsSizeChanges, info2.supportsSizeChanges);
        assertEquals(info1.requiredDisplayCategory, info2.requiredDisplayCategory);
        assertEquals(info1.requireContentUriPermissionFromCaller,
                info2.requireContentUriPermissionFromCaller);
        assertEquals(info1.launchToken, info2.launchToken);
        assertEquals(info1.persistableMode, info2.persistableMode);
    }
}
