/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED;

import static com.android.bedstead.harrier.UserType.INITIAL_USER;
import static com.android.bedstead.harrier.UserType.PRIVATE_PROFILE;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.devicepolicy.cts.utils.BundleUtils;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.policies.ApplicationRestrictions;
import com.android.bedstead.harrier.policies.ApplicationRestrictionsManagingPackage;
import com.android.bedstead.harrier.policies.DmrhOnlyApplicationRestrictions;
import com.android.bedstead.harrier.policies.DpcOnlyApplicationRestrictions;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BedsteadJUnit4.class)
public final class ApplicationRestrictionsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TAG = ApplicationRestrictionsTest.class.getSimpleName();

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static final TestApp sDifferentTestApp = sDeviceState.testApps().any();

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = DpcOnlyApplicationRestrictions.class)
    public void setApplicationRestrictions_applicationRestrictionsAreSet() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_applicationRestrictionsAreSet");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            BundleUtils.assertEqualToBundle(
                    "setApplicationRestrictions_applicationRestrictionsAreSet",
                    testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

  @Postsubmit(reason = "New test")
  @PolicyAppliesTest(policy = DpcOnlyApplicationRestrictions.class)
  @Ignore("b/290932414")
  public void setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            new Bundle());
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

      BundleUtils.assertEqualToBundle(
          "setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions",
          testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = {
            ApplicationRestrictions.class, DmrhOnlyApplicationRestrictions.class})
    //TODO: wait for b/332662548 so we can test DMRH calling
    // dpm.getParentInstance().getApplicationRestrictions()
    public void getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
    Bundle bundle =
        BundleUtils.createBundle(
            "getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions");

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

      BundleUtils.assertEqualToBundle(
          "getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions",
          sDeviceState
              .dpc()
              .devicePolicyManager()
              .getApplicationRestrictions(
                  sDeviceState.dpc().componentName(), sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_differentPackage_throwsException() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getApplicationRestrictions_differentPackage_throwsException");

        try (TestAppInstance differentTestApp = sDifferentTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThrows(SecurityException.class,
                    () -> differentTestApp.userManager().getApplicationRestrictions(
                            sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_setForOtherPackage_returnsNull() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getApplicationRestrictions_setForOtherPackage_returnsNull");

        try (TestAppInstance differentTestApp = sDifferentTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            BundleUtils.assertNotEqualToBundle(
                    "getApplicationRestrictions_setForOtherPackage_returnsNull",
                    differentTestApp.userManager().getApplicationRestrictions(
                    sDifferentTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager().getApplicationRestrictions(
                        sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

      BundleUtils.assertNotEqualToBundle(
          "setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet",
          testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_cannotSetPolicy_throwsException() {
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_cannotSetPolicy_throwsException");
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);
        });
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .getApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName());
        });
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class, singleTestOnly = true)
    public void setApplicationRestrictions_nullComponent_throwsException() {
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_nullComponent_throwsException");
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(null,
                        sTestApp.packageName(), bundle));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = {
            ApplicationRestrictions.class, DmrhOnlyApplicationRestrictions.class})
    public void setApplicationRestrictions_restrictionsChangedBroadcastIsReceived() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_restrictionsChangedBroadcastIsReceived");

        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.registerReceiver(new IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED),
                    RECEIVER_EXPORTED);

            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThat(testApp.events().broadcastReceived().whereIntent().action().isEqualTo(
                    ACTION_APPLICATION_RESTRICTIONS_CHANGED)).eventOccurred();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictionsManagingPackage.class)
    public void setApplicationRestrictionsManagingPackage_applicationRestrictionsManagingPackageIsSet()
            throws Exception {
        final String originalApplicationRestrictionsManagingPackage =
                sDeviceState.dpc().devicePolicyManager().getApplicationRestrictionsManagingPackage(
                        sDeviceState.dpc().componentName());
        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictionsManagingPackage(
                    sDeviceState.dpc().componentName(), sTestApp.packageName());

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getApplicationRestrictionsManagingPackage(sDeviceState.dpc().componentName()))
                    .isEqualTo(sTestApp.packageName());
        } finally {
            try {
                sDeviceState.dpc().devicePolicyManager().setApplicationRestrictionsManagingPackage(
                        sDeviceState.dpc().componentName(),
                        originalApplicationRestrictionsManagingPackage);
            } catch (Throwable expected) {
                // If the original has been removed this can throw
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictionsManagingPackage.class)
    public void setApplicationRestrictionsManagingPackage_appNotInstalled_throwsException() {
        sDifferentTestApp.uninstall();

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setApplicationRestrictionsManagingPackage(
                                sDeviceState.dpc().componentName(),
                                sDifferentTestApp.packageName()));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = {
            ApplicationRestrictions.class, DmrhOnlyApplicationRestrictions.class})
    public void setApplicationRestrictions_logged() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle("setApplicationRestrictions_logged");

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create();
             TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_APPLICATION_RESTRICTIONS_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereStrings().contains(sTestApp.packageName())
                    .whereStrings().size().isEqualTo(1))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = {
            ApplicationRestrictions.class, DmrhOnlyApplicationRestrictions.class})
    public void setApplicationRestrictions_invalidPackageName_throwsException() {
        Bundle bundle = BundleUtils.createBundle(
                "setApplicationRestrictions_invalidPackageName_throwsException");
        assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                        sDeviceState.dpc().componentName(), "/../blah", bundle));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = {
            ApplicationRestrictions.class, DmrhOnlyApplicationRestrictions.class})
    public void getApplicationRestrictionsPerAdmin_restrictionsSetForOneAdmin_returnsApplicationRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getApplicationRestrictionsPerAdmin_applicationRestrictionsAreSetForOneAdmin"
                        + "_returnsApplicationRestrictions");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            List<Bundle> restrictions = testApp.restrictionsManager()
                    .getApplicationRestrictionsPerAdmin();
            assertThat(restrictions.size()).isEqualTo(1);
            BundleUtils.assertEqualToBundle("getApplicationRestrictionsPerAdmin"
                            + "_applicationRestrictionsAreSetForOneAdmin"
                            + "_returnsApplicationRestrictions",
                    restrictions.get(0));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = DmrhOnlyApplicationRestrictions.class)
    public void roleHolderSetApplicationRestrictions_UserManagerReturnsNull() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "roleHolderSetApplicationRestrictions_UserManagerReturnsNull");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThat(testApp.userManager()
                    .getApplicationRestrictions(testApp.packageName()).isEmpty()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @EnsureHasDevicePolicyManagerRoleHolder(isPrimary = true, onUser = WORK_PROFILE)
    @EnsureHasWorkProfile(isOrganizationOwned = true)
    @UserTest({INITIAL_USER, PRIVATE_PROFILE})
    @Test
    public void roleHolderSetApplicationRestrictionsOnParent_successWithBroadcastSent() {
        ComponentName admin = sDeviceState.dpc().componentName();
        Bundle originalApplicationRestrictions = sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(admin, sTestApp.packageName());

        String bundleName = "parentUserBundle";

        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.registerReceiver(new IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED),
                    RECEIVER_EXPORTED);

            sDeviceState.dpc().devicePolicyManager()
                    .getParentProfileInstance(admin)
                    .setApplicationRestrictions(
                    admin, sTestApp.packageName(), BundleUtils.createBundle(bundleName));

            List<Bundle> restrictions = testApp.restrictionsManager()
                    .getApplicationRestrictionsPerAdmin();
            assertThat(restrictions.size()).isEqualTo(1);
            BundleUtils.assertEqualToBundle(bundleName, restrictions.get(0));

            assertThat(testApp.events().broadcastReceived().whereIntent().action().isEqualTo(
                    ACTION_APPLICATION_RESTRICTIONS_CHANGED)).eventOccurred();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    admin, sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @EnsureHasDevicePolicyManagerRoleHolder(isPrimary = true, onUser = WORK_PROFILE)
    @EnsureHasWorkProfile(isOrganizationOwned = false)
    @RequireRunOnInitialUser
    @Test
    public void roleHolderSetApplicationRestrictionsOnParent_throwExceptionIfNotCope() {
        assertThrows(IllegalStateException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().getParentProfileInstance(null)
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            new Bundle());
        });
    }

    /**
     * Verifies that DMRH and DPC can both set application restrictions without overwriting each
     * other.
     *
     * To ensure this works correctly on HSUM, make sure DMRH, DPC and the TestApp all run on the
     * instrumented user, using test annotations.
     */
    @Postsubmit(reason = "New test")
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasProfileOwner(isPrimary = true)
    @RequireRunOnInitialUser
    @Test
    public void dpcAndRoleHolderSetApplicationRestrictions_doesNotOverlap() {
        ComponentName dpcAdmin = sDeviceState.dpc().componentName();
        ComponentName dmrhAdmin = sDeviceState.dpmRoleHolder().componentName();
        Bundle originalDpcAppRestrictions = sDeviceState.dpc().devicePolicyManager()
                .getApplicationRestrictions(dpcAdmin, sTestApp.packageName());
        Bundle originalRoleHolderAppRestrictions = sDeviceState.dpmRoleHolder()
                .devicePolicyManager()
                .getApplicationRestrictions(dmrhAdmin, sTestApp.packageName());

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    dpcAdmin, sTestApp.packageName(), BundleUtils.createBundle("dpcBundle"));
            sDeviceState.dpmRoleHolder().devicePolicyManager().setApplicationRestrictions(
                    null, sTestApp.packageName(), BundleUtils.createBundle("dmrhBundle"));

            BundleUtils.assertEqualToBundle("dpcBundle",
                    testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));

            List<Bundle> restrictions = testApp.restrictionsManager()
                    .getApplicationRestrictionsPerAdmin();
            BundleUtils.assertEqualToBundleList(restrictions, "dpcBundle", "dmrhBundle");
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    dpcAdmin, sTestApp.packageName(), originalDpcAppRestrictions);
            sDeviceState.dpmRoleHolder().devicePolicyManager().setApplicationRestrictions(
                    dmrhAdmin, sTestApp.packageName(), originalRoleHolderAppRestrictions);
        }
    }
}
