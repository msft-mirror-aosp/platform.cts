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
package android.contentcaptureservice.cts;

import static android.Manifest.permission.SET_CONTENT_PROTECTION_ALLOWLIST;

import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.contentcapture.ContentCaptureManager;
import android.view.contentprotection.flags.Flags;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ActivitiesWatcher.ActivityWatcher;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Set;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
@RequiresFlagsEnabled(Flags.FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
public class ContentProtectionAllowlistTest
        extends AbstractContentCaptureIntegrationAutoActivityLaunchTest<BlankActivity> {

    private static final ActivityTestRule<BlankActivity> sActivityRule =
            new ActivityTestRule<>(BlankActivity.class, false, false);

    private static final Set<String> TEST_SET = Set.of("com.test.package");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private BlankActivity mActivity;

    private ActivityWatcher mWatcher;

    private ContentCaptureManager mContentCaptureManager;

    public ContentProtectionAllowlistTest() {
        super(BlankActivity.class);
    }

    @Override
    protected ActivityTestRule<BlankActivity> getActivityTestRule() {
        return sActivityRule;
    }

    @Before
    public void setUp() throws Exception {
        CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        mWatcher = startWatcher();
        mActivity = launchActivity();
        mWatcher.waitFor(RESUMED);

        mContentCaptureManager = mActivity.getContentCaptureManager();
        assertThat(mContentCaptureManager).isNotNull();
    }

    @After
    public void shotDown() throws Exception {
        mActivity.finish();
        mWatcher.waitFor(DESTROYED);
    }

    @Test
    public void testSetContentProtectionAllowlist_withoutPermissions() {
        mContentCaptureManager.setContentProtectionAllowlist(Set.of());
    }

    @Test
    public void testSetContentProtectionAllowlist_withPermissions() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContentCaptureManager.setContentProtectionAllowlist(Set.of()),
                SET_CONTENT_PROTECTION_ALLOWLIST);
    }

    @Test
    public void testSetContentProtectionAllowlist_withoutPermissions_onUiThread() {
        mActivity.syncRunOnUiThread(
                () -> mContentCaptureManager.setContentProtectionAllowlist(TEST_SET));
    }

    @Test
    public void testSetContentProtectionAllowlist_withPermissions_onUiThread() {
        mActivity.syncRunOnUiThread(
                () ->
                        SystemUtil.runWithShellPermissionIdentity(
                                () ->
                                        mContentCaptureManager.setContentProtectionAllowlist(
                                                TEST_SET),
                                SET_CONTENT_PROTECTION_ALLOWLIST));
    }
}
