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

package android.packageinstaller.criticaluserjourney.cts;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for PackageInstaller CUJs to uninstall an app with Multi-Users.
 */
@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "Cannot query other apps if instant")
@AppModeNonSdkSandbox(reason = "SDK sandboxes cannot query other apps")
@EnsureHasWorkProfile
public class UninstallationWithMultiUsersTest extends UninstallationTestBase {

    private Context mPrimaryUserContext;
    private Context mWorkProfileUserContext;
    private UserReference mPrimaryUser;
    private UserReference mWorkProfileUser;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        assumeTrue(UserManager.supportsMultipleUsers());
        mPrimaryUser = sDeviceState.initialUser();
        mWorkProfileUser = sDeviceState.workProfile();

        installExistingPackageOnUser(getContext().getPackageName(), mWorkProfileUser.id());
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        mPrimaryUserContext = getContext().createContextAsUser(mPrimaryUser.userHandle(),
                /* flags= */ 0);
        mWorkProfileUserContext = getContext().createContextAsUser(mWorkProfileUser.userHandle(),
                /* flags= */ 0);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        mPrimaryUserContext = null;
        mWorkProfileUserContext = null;
        mPrimaryUser = null;
        mWorkProfileUser = null;
        super.tearDown();
    }

    @Test
    public void actionDeleteIntent_uninstallOnWorkProfile_okButton_success() throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);

        startUninstallationViaIntentActionDeleteForUser(mWorkProfileUserContext);

        waitForUiIdle();

        clickUninstallAppFromWorkProfileOkButton();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void actionDeleteIntent_uninstallOnPrimaryUser_okButton_success() throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);

        startUninstallationViaIntentActionDeleteForUser(mPrimaryUserContext);

        waitForUiIdle();

        clickUninstallOkButton();

        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallOnWorkProfile_okButton_success()
            throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);

        startUninstallationViaIntentActionUninstallPackageForUser(mWorkProfileUserContext);

        waitForUiIdle();

        clickUninstallAppFromWorkProfileOkButton();

        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageNotInstalledOnUser(mWorkProfileUserContext);
    }

    @Test
    public void uninstallPackageIntent_uninstallOnPrimaryUser_okButton_success() throws Exception {
        installTestPackage();
        assertTestPackageInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);

        startUninstallationViaIntentActionUninstallPackageForUser(mPrimaryUserContext);

        waitForUiIdle();

        clickUninstallOkButton();

        assertTestPackageNotInstalledOnUser(mPrimaryUserContext);
        assertTestPackageInstalledOnUser(mWorkProfileUserContext);
    }
}
