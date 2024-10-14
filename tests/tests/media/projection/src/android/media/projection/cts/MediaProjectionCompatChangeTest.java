/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.projection.cts;

import static android.media.cts.MediaProjectionActivity.CANCEL_RESOURCE_ID;
import static android.media.cts.MediaProjectionActivity.ENTIRE_SCREEN_STRING_RES_NAME;
import static android.media.cts.MediaProjectionActivity.SCREEN_SHARE_OPTIONS_RES_PATTERN;
import static android.media.cts.MediaProjectionActivity.SINGLE_APP_STRING_RES_NAME;
import static android.media.cts.MediaProjectionActivity.getResourceString;
import static android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay;
import static android.media.projection.MediaProjectionConfig.createConfigForUserChoice;
import static android.media.projection.MediaProjectionManager.OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION;
import static android.media.projection.cts.MediaProjectionPermissionDialogTestActivity.EXTRA_MEDIA_PROJECTION_CONFIG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.NonMainlineTest;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

/**
 * Test {@link MediaProjection} compat change dependent logic
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionCompatChangeTest
 */
@NonMainlineTest
public class MediaProjectionCompatChangeTest {
    private static final String LOG_COMPAT_CHANGE = "android.permission.LOG_COMPAT_CHANGE";
    private static final String READ_COMPAT_CHANGE_CONFIG =
            "android.permission.READ_COMPAT_CHANGE_CONFIG";

    private static UiDevice sDevice;
    private static boolean sIsWatch;
    private static boolean sSupportsPartialScreenshare;
    private static String sEntireScreenString;
    private static String sSingleAppString;

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @ClassRule
    public static ActivityTestRule<MediaProjectionPermissionDialogTestActivity> sActivityRule =
            new ActivityTestRule<>(MediaProjectionPermissionDialogTestActivity.class, false, false);

    /** Set up necessary values which only need to be set once */
    @BeforeClass
    public static void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(LOG_COMPAT_CHANGE, READ_COMPAT_CHANGE_CONFIG);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        sEntireScreenString = getResourceString(context, ENTIRE_SCREEN_STRING_RES_NAME);
        sSingleAppString = getResourceString(context, SINGLE_APP_STRING_RES_NAME);
        sIsWatch = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        sSupportsPartialScreenshare = testMediaProjectionPermissionDialog(null, sSingleAppString);
    }

    @Before
    public void setUpTest() {
        assumeFalse(sIsWatch);
    }

    @After
    public void tearDown() {
        sActivityRule.finishActivity();
    }

    // MediaProjectionConfig#createConfigForDefaultDisplay should cause the single app option to be
    // disabled and entire screen to be the default option. This test ensures that when the
    // per-app override is enabled, that the MediaProjectionConfig is overridden, and the single app
    // option is enabled and the default option.
    @Test
    @EnableCompatChanges({OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION})
    public void testMediaProjectionPermissionDialog_overrideDefaultDisplayConfig() {
        boolean correctSpinnerString = testMediaProjectionPermissionDialog(
                createConfigForDefaultDisplay(), sSingleAppString);
        assertThat(correctSpinnerString).isTrue();
    }

    // MediaProjectionConfig#createConfigForUserChoice should cause the single app option to be
    // enabled and be the default option. This test ensures that when the per-app override is
    // enabled, this behaviour is not changed.
    @Test
    @EnableCompatChanges({OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION})
    public void testMediaProjectionPermissionDialog_overrideUserChoiceConfig() {
        assumeTrue(sSupportsPartialScreenshare);
        boolean correctSpinnerString = testMediaProjectionPermissionDialog(
                createConfigForUserChoice(), sSingleAppString);
        assertThat(correctSpinnerString).isTrue();
    }

    // MediaProjectionConfig#createConfigForDefaultDisplay should cause the single app option to be
    // disabled and entire screen to be the default option. This test ensures that this behaviour is
    // not changed.
    @Test
    @DisableCompatChanges({OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION})
    public void testMediaProjectionPermissionDialog_defaultDisplayConfig() {
        boolean correctSpinnerString = testMediaProjectionPermissionDialog(
                createConfigForDefaultDisplay(), sEntireScreenString);
        assertThat(correctSpinnerString).isTrue();
    }

    // MediaProjectionConfig#createConfigForUserChoice should cause the single app option to be
    // enabled and be the default option. This test ensures that this behaviour is not changed.
    @Test
    @DisableCompatChanges({OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION})
    public void testMediaProjectionPermissionDialog_userChoiceConfig() {
        assumeTrue(sSupportsPartialScreenshare);
        boolean correctSpinnerString = testMediaProjectionPermissionDialog(
                createConfigForUserChoice(), sSingleAppString);
        assertThat(correctSpinnerString).isTrue();
    }

    private static boolean testMediaProjectionPermissionDialog(
            MediaProjectionConfig config, String expectedSpinnerString) {
        Intent testActivityIntent = null;
        if (config != null) {
            testActivityIntent = new Intent().putExtra(EXTRA_MEDIA_PROJECTION_CONFIG, config);
        }
        sActivityRule.launchActivity(testActivityIntent);
        sDevice.waitForIdle();

        // check if we can find a view which has the expected default option
        boolean foundOptionString = sDevice.hasObject(
                By.res(SCREEN_SHARE_OPTIONS_RES_PATTERN)
                        .hasDescendant(
                                By.text(expectedSpinnerString)));

        // close the dialog so it doesn't linger for subsequent tests
        UiObject2 cancelButton = sDevice.findObject(By.res(CANCEL_RESOURCE_ID));
        cancelButton.click();

        return foundOptionString;
    }
}
