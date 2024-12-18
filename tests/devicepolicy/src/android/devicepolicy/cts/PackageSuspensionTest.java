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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.flags.Flags;
import android.content.ComponentName;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.SuspendPackage;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PackageSuspensionTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final String sNonexistentPackage = "android.devicepolicy.cts.non-existentapp";

    private ComponentName mAdmin;

    @Before
    public void setup() {
        mAdmin = sDeviceState.dpc().componentName();
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void isPackageSuspended_packageIsSuspended_returnsTrue() throws Exception {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        try {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp}, /* suspended */ true);

            assertThat(
                    sDeviceState.dpc().devicePolicyManager().isPackageSuspended(mAdmin, testApp)
            ).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp},  /* suspended */ false);
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void isPackageSuspended_packageIsNotSuspended_returnFalse() throws Exception {
        String testApp = sDeviceState.testApp().packageName();
        skipRoleHolderTestIfFlagNotEnabled();
        sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                mAdmin, new String[] {testApp},  /* suspended */ false);

        assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(mAdmin, testApp))
                .isFalse();
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackagesSuspended_suspendSuccessful() throws Exception {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp},  /* suspended */ true)
            ).isEmpty();
            assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                    mAdmin, testApp)
            ).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp},  /* suspended */ false);
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackagesSuspended_suspendAlreadySuspendedPackage_returnsEmpty() {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        try (TestAppInstance anotherApp = sDeviceState.testApps().any().install()) {
            String anotherTestApp = anotherApp.packageName();
            try {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{testApp, anotherTestApp},  /* suspended */ true);

                assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{testApp, anotherTestApp},  /* suspended */ true)
                ).isEmpty();
            } finally {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{testApp, anotherTestApp},  /* suspended */ false);
            }
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void setPackagesSuspended_suspendUninstalledPackage_notSuspended() throws Exception {
        skipRoleHolderTestIfFlagNotEnabled();
        assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                mAdmin, new String[] {sNonexistentPackage},  /* suspended */ true)
        ).isEqualTo(new String[] {sNonexistentPackage});
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackagesSuspended_unsuspendSuccessful() throws Exception {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        try {
            // Start with suspended package
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp},  /* suspended */ true);

            assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp},  /* suspended */ false)
            ).isEmpty();
            assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                    mAdmin, testApp)
            ).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp},  /* suspended */ false);
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackagesSuspended_unsuspendAlreadyUnsuspendedPackage_returnsEmpty() {
        skipRoleHolderTestIfFlagNotEnabled();
        assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                mAdmin,
                new String[] {sDeviceState.testApp().packageName()},
                /* suspended */ false)
        ).isEmpty();
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void setPackagesSuspended_unsuspendNonexistentPackage_remainUnuspended() {
        skipRoleHolderTestIfFlagNotEnabled();
        assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                mAdmin,
                new String[] {sNonexistentPackage},
                /* suspended */ false)).isEqualTo(new String[] {sNonexistentPackage});
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackagesSuspended_suspendMultipleTimes_allPackagesSuspended() throws Exception {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        try (TestAppInstance anotherApp = sDeviceState.testApps().any().install()) {
            String anotherTestApp = anotherApp.packageName();
            try {
                // Suspend both packages in two calls
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[] {testApp},  /* suspended */ true);
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[] {testApp, anotherTestApp}, /* suspended */ true);
                // Assert suspension state
                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, testApp)).isTrue();
                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, anotherTestApp)).isTrue();
            } finally {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{testApp, anotherTestApp}, /* suspended */ false);
            }
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackagesSuspended_unsuspendMultipleTimes_allPackagesUnsuspended()
            throws Exception {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        try (TestAppInstance anotherApp = sDeviceState.testApps().any().install()) {
            String anotherTestApp = anotherApp.packageName();
            try {
                // Start with packages suspended
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{testApp, anotherTestApp}, /* suspended */ true);
                // Unsuspend packages in two calls
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{anotherTestApp},  /* suspended */ false);
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{testApp, anotherTestApp},  /* suspended */ false);
                // Assert suspension state
                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, testApp)).isFalse();
                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, anotherTestApp)).isFalse();

            } finally {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[]{testApp, anotherTestApp},  /* suspended */ false);
            }
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackagesSuspended_suspendMixedPackages_onlySomeSuspended() throws Exception {
        String testApp = sDeviceState.testApp().packageName();
        skipRoleHolderTestIfFlagNotEnabled();
        try {
            // Suspend both packages
            assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp, sNonexistentPackage}, /* suspended */ true))
                    .isEqualTo(new String[] {sNonexistentPackage});
            // Assert suspension state
            assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                    mAdmin, testApp)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    mAdmin, new String[] {testApp, sNonexistentPackage},  /* suspended */ false);
        }
    }

    //@MostRestrictiveCoexistenceTest(policy = SuspendPackage.class)
    // MostRestrictiveCoexistenceTest does not work for policies which have 0 or 2+ permissions
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner(isPrimary = true)
    @RequireFlagsEnabled(Flags.FLAG_UNMANAGED_MODE_MIGRATION)
    @Postsubmit(reason = "new test")
    @Test
    @EnsureTestAppInstalled
    public void setPackagesSuspended_multipleAdminsSuspendPackages_success() throws Exception {
        String testApp = sDeviceState.testApp().packageName();
        try (TestAppInstance anotherApp = sDeviceState.testApps().any().install()) {
            String anotherTestApp = anotherApp.packageName();
            try {
                // Suspend packages from different admins
                assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        sDeviceState.dpc().componentName(), new String[]{testApp},
                        /* suspended */ true)
                ).isEmpty();
                assertThat(sDeviceState.dpmRoleHolder().devicePolicyManager().setPackagesSuspended(
                        null, new String[]{testApp, anotherTestApp},  /* suspended */ true)
                ).isEmpty();
                // Assert package state
                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, testApp)).isTrue();
                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, anotherTestApp)).isTrue();
            } finally {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        sDeviceState.dpc().componentName(), new String[]{testApp},
                        /* suspended */ false);
                sDeviceState.dpmRoleHolder().devicePolicyManager().setPackagesSuspended(
                        null, new String[]{testApp, anotherTestApp},
                        /* suspended */ false);
            }
        }
    }

    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner(isPrimary = true)
    @RequireFlagsEnabled(Flags.FLAG_UNMANAGED_MODE_MIGRATION)
    @Postsubmit(reason = "new test")
    @Test
    @EnsureTestAppInstalled
    public void setPackagesSuspended_adminsUnsuspendOtherAdmin_fail() throws Exception {
        String testApp = sDeviceState.testApp().packageName();
        try (TestAppInstance anotherApp = sDeviceState.testApps().any().install()) {
            String anotherTestApp = anotherApp.packageName();
            try {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        sDeviceState.dpc().componentName(), new String[]{testApp},
                        /* suspended */ true);
                sDeviceState.dpmRoleHolder().devicePolicyManager().setPackagesSuspended(
                        null, new String[]{anotherTestApp},  /* suspended */ true);

                assertThat(sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        sDeviceState.dpc().componentName(), new String[]{testApp, anotherTestApp},
                        /* suspended */ false)
                ).isEqualTo(new String[]{anotherTestApp});

                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, testApp)).isFalse();
                assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                        mAdmin, anotherTestApp)).isTrue();
            } finally {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        sDeviceState.dpc().componentName(), new String[]{testApp},
                        /* suspended */ false);
                sDeviceState.dpmRoleHolder().devicePolicyManager().setPackagesSuspended(
                        null, new String[]{anotherTestApp},  /* suspended */ false);
            }
        }
    }

    @CannotSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void isPackageSuspended_notAllowed_throwsException() {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().isPackageSuspended(mAdmin, testApp));
    }

    @CannotSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    @EnsureTestAppInstalled
    public void setPackageSuspended_notAllowed_throwsException() {
        skipRoleHolderTestIfFlagNotEnabled();
        String testApp = sDeviceState.testApp().packageName();
        try {
            assertThrows(SecurityException.class, () ->
                    sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                                    mAdmin, new String[] {testApp},  /* suspended */ true));
        } finally {
            try {
                sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                        mAdmin, new String[] {testApp},  /* suspended */ false);
            } catch (SecurityException ex) {
                // Expected
            }
        }
    }

    private void skipRoleHolderTestIfFlagNotEnabled() {
        try {
            if (sDeviceState.dpc() == sDeviceState.dpmRoleHolder()) {
                assumeTrue("This test only runs with flag "
                        + Flags.FLAG_UNMANAGED_MODE_MIGRATION
                        + " is enabled", Flags.unmanagedModeMigration());
            }
        } catch (IllegalStateException e) {
            // Fine - DMRH is not set
        }
    }
}
