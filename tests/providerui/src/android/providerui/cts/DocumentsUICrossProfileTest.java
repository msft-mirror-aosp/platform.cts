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

package android.providerui.cts;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.multiuser.Flags.FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.multiuser.annotations.RequireRunOnPrivateProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(BedsteadJUnit4.class)
public class DocumentsUICrossProfileTest {
    private Context mContext;
    private UserManager mUserManager;
    private UiDevice mDevice;
    private GetResultActivity mActivity;
    private static final long TIMEOUT_MILLIS = 10 * DateUtils.SECOND_IN_MILLIS;
    private static final String PERSONAL_TAB_LABEL = "Personal";
    private static final int REQUEST_CODE = 42;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = InstrumentationRegistry.getTargetContext();
        mUserManager = mContext.getSystemService(UserManager.class);
        mDevice = UiDevice.getInstance(mInstrumentation);
        final Intent intent = new Intent(mContext, GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity = (GetResultActivity) mInstrumentation.startActivitySync(intent);
        mInstrumentation.waitForIdleSync();
        mActivity.clearResult();
        mDevice.wakeUp();
    }

    @After
    public void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    @Test
    @RequireRunOnWorkProfile
    public void testOpenDocumentsUi_noUserExcluded_work() throws Exception {
        assumeTrue(supportsHardware());
        final Intent intent = getBaseIntent();

        mActivity.startActivityForResult(intent, REQUEST_CODE);

        assertNotNull(findByLabel(mUserManager.getProfileLabel()));
    }

    @Test
    @RequireRunOnPrivateProfile
    public void testOpenDocumentsUi_noUserExcluded_private() throws Exception {
        assumeTrue(supportsHardware());

        final Intent intent = getBaseIntent();
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        assertNotNull(findByLabel(mUserManager.getProfileLabel()));
    }

    @Test
    @RequireRunOnPrivateProfile
    @RequiresFlagsEnabled(FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE)
    public void testOpenDocumentsUi_excludeSelf_private() throws Exception {
        assumeTrue(supportsHardware());

        final Intent intent = getBaseIntent();
        intent.putExtra(
                DocumentsContract.EXTRA_EXCLUDED_USERS,
                new ArrayList<UserHandle>(Arrays.asList(mContext.getUser())));
        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {

            mActivity.startActivityForResult(intent, REQUEST_CODE);

            assertNull(findByLabel(mUserManager.getProfileLabel()));
        }
    }

    @Test
    @RequireRunOnWorkProfile
    @RequiresFlagsEnabled(FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE)
    public void testOpenDocumentsUi_excludeSelf_work() throws Exception {
        assumeTrue(supportsHardware());

        final Intent intent = getBaseIntent();
        intent.putExtra(
                DocumentsContract.EXTRA_EXCLUDED_USERS,
                new ArrayList<UserHandle>(Arrays.asList(mContext.getUser())));
        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {

            mActivity.startActivityForResult(intent, REQUEST_CODE);

            assertNull(findByLabel(mUserManager.getProfileLabel()));
        }
    }

    @Test
    @RequireRunOnPrivateProfile
    @RequiresFlagsEnabled(FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE)
    public void testOpenDocumentsUi_excludeAllUsers() throws Exception {
        assumeTrue(supportsHardware());

        final Intent intent = getBaseIntent();
        intent.putExtra(
                DocumentsContract.EXTRA_EXCLUDED_USERS,
                new ArrayList<UserHandle>(
                        Arrays.asList(
                                mContext.getUser(), TestApis.users().initial().userHandle())));

        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {

            mActivity.startActivityForResult(intent, REQUEST_CODE);

            // If all users are excluded, no user should be hidden
            assertNotNull(findByLabel(mUserManager.getProfileLabel()));
            assertNotNull(findByLabel(PERSONAL_TAB_LABEL));
        }
    }

    @Test
    @RequireRunOnPrivateProfile
    @RequiresFlagsDisabled(FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE)
    public void testOpenDocumentsUi_excludeSelf_flagDisabled() throws Exception {
        assumeTrue(supportsHardware());

        final Intent intent = getBaseIntent();
        intent.putExtra(
                DocumentsContract.EXTRA_EXCLUDED_USERS,
                new ArrayList<UserHandle>(Arrays.asList(mContext.getUser())));
        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {

            mActivity.startActivityForResult(intent, REQUEST_CODE);

            assertNotNull(findByLabel(mUserManager.getProfileLabel()));
        }
    }

    private UiObject2 findByLabel(String label) {
        return mDevice.wait(Until.findObject(By.text(label)), TIMEOUT_MILLIS);
    }

    private boolean supportsHardware() {
        final PackageManager pm = mContext.getPackageManager();
        return !pm.hasSystemFeature("android.hardware.type.television")
                && !pm.hasSystemFeature("android.hardware.type.watch");
    }

    private Intent getBaseIntent() {
        return new Intent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
    }
}
