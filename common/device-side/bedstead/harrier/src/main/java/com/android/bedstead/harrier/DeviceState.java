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

package com.android.bedstead.harrier;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip;
import static com.android.bedstead.harrier.AnnotationExecutorUtil.failOrSkip;
import static com.android.bedstead.harrier.annotations.EnsureHasAccount.DEFAULT_ACCOUNT_KEY;
import static com.android.bedstead.harrier.annotations.EnsureTestAppInstalled.DEFAULT_KEY;
import static com.android.bedstead.harrier.annotations.UsesAnnotationExecutorKt.getAnnotationExecutorClass;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_CLONE_PROFILE;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_MANAGED_PROFILE;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_PRIVATE_PROFILE;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_USER;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_BLUETOOTH;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.bedstead.nene.utils.Versions.meetsSdkVersionRequirements;
import static com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator.REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import com.android.bedstead.enterprise.DeviceOwnerComponent;
import com.android.bedstead.enterprise.EnterpriseComponent;
import com.android.bedstead.enterprise.ProfileOwnersComponent;
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwnerKt;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceDisabled;
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceEnabled;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.harrier.annotations.EnsureHasAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasTestContentSuggestionsService;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureNoPackageRespondsToIntent;
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled;
import com.android.bedstead.harrier.annotations.EnsurePackageRespondsToIntent;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePasswordSet;
import com.android.bedstead.harrier.annotations.EnsurePolicyOperationUnsafe;
import com.android.bedstead.harrier.annotations.EnsurePropertySet;
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet;
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.EnsureUsingDisplayTheme;
import com.android.bedstead.harrier.annotations.EnsureUsingScreenOrientation;
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled;
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.RequireHasDefaultBrowser;
import com.android.bedstead.harrier.annotations.RequireInstantApp;
import com.android.bedstead.harrier.annotations.RequireNoPackageRespondsToIntent;
import com.android.bedstead.harrier.annotations.RequireNotInstantApp;
import com.android.bedstead.harrier.annotations.RequirePackageInstalled;
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled;
import com.android.bedstead.harrier.annotations.RequirePackageRespondsToIntent;
import com.android.bedstead.harrier.annotations.RequireQuickSettingsSupport;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable;
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion;
import com.android.bedstead.harrier.annotations.RequireTelephonySupport;
import com.android.bedstead.harrier.annotations.TestTag;
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.meta.EnsureHasNoProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.EnsureHasProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequireRunOnProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4;
import com.android.bedstead.multiuser.UsersComponent;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountReference;
import com.android.bedstead.nene.devicepolicy.CommonDevicePolicy;
import com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.OperationSafetyReason;
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.display.Display;
import com.android.bedstead.nene.display.DisplayProperties;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.logcat.SystemServerException;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver;
import com.android.bedstead.nene.utils.FailureDumper;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ResolveInfoWrapper;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator;
import com.android.bedstead.remotedpc.RemoteDevicePolicyManagerRoleHolder;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.remotedpc.RemotePolicyManager;
import com.android.bedstead.testapp.NotFoundException;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.bedstead.testapp.TestAppQueryBuilder;
import com.android.eventlib.EventLogs;
import com.android.queryable.annotations.Query;

import junit.framework.AssertionFailedError;

import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A Junit rule which exposes methods for efficiently changing and querying device state.
 *
 * <p>States set by the methods on this class will by default be cleaned up after the test.
 *
 *
 * <p>Using this rule also enforces preconditions in annotations from the
 * {@code com.android.comaptibility.common.util.enterprise.annotations} package.
 * <p>
 * {@code assumeTrue} will be used, so tests which do not meet preconditions will be skipped.
 */
public final class DeviceState extends HarrierRule {
    private static final String SWITCHED_TO_PARENT_USER = "switchedToParentUser";
    public static final String INSTALL_INSTRUMENTED_APP = "installInstrumentedApp";
    private static final String IS_QUIET_MODE_ENABLED = "isQuietModeEnabled";
    public static final String FOR_USER = "forUser";
    public static final String DPC_IS_PRIMARY = "dpcIsPrimary";
    public static final String AFFILIATION_IDS = "affiliationIds";
    private static final String USE_PARENT_INSTANCE_OF_DPC = "useParentInstanceOfDpc";

    private final Context mContext = TestApis.context().instrumentedContext();
    private static final String SKIP_TEST_TEARDOWN_KEY = "skip-test-teardown";
    private static final String SKIP_CLASS_TEARDOWN_KEY = "skip-class-teardown";
    private static final String SKIP_TESTS_REASON_KEY = "skip-tests-reason";
    private static final String MIN_SDK_VERSION_KEY = "min-sdk-version";
    private static final String ADDITIONAL_FEATURES_KEY = "additional-features";
    private boolean mSkipTestTeardown;
    private final boolean mSkipClassTeardown;
    private boolean mSkipTests;
    private boolean mFailTests;
    private boolean mUsingBedsteadJUnit4 = false;
    private String mSkipTestsReason;
    private String mFailTestsReason;
    private final List<String> mAdditionalFeatures;
    // The minimum version supported by tests, defaults to current version
    private final int mMinSdkVersion;
    private int mMinSdkVersionCurrentTest;

    private boolean mNextSafetyOperationSet = false;

    private static final String TV_PROFILE_TYPE_NAME = "com.android.tv.profile";
    private static final String CLONE_PROFILE_TYPE_NAME = "android.os.usertype.profile.CLONE";
    private static final String PRIVATE_PROFILE_TYPE_NAME = "android.os.usertype.profile.PRIVATE";


    // We timeout 10 seconds before the infra would timeout
    private static final Duration MAX_TEST_DURATION =
            Duration.ofMillis(
                    Long.parseLong(TestApis.instrumentation().arguments().getString(
                            "timeout_msec", "600000")) - 2000);

    // We allow overriding the limit on a class-by-class basis
    private final Duration mMaxTestDuration;

    private ExecutorService mTestExecutor;
    private Thread mTestThread;

    private final PackageManager mPackageManager = sContext.getPackageManager();

    private final BedsteadServiceLocator mLocator = new BedsteadServiceLocator();

    public static final class Builder {

        private Duration mMaxTestDuration = MAX_TEST_DURATION;

        private Builder() {

        }

        public Builder maxTestDuration(Duration maxTestDuration) {
            mMaxTestDuration = maxTestDuration;
            return this;
        }

        public DeviceState build() {
            return new DeviceState(mMaxTestDuration);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public DeviceState(Duration maxTestDuration) {
        mLocator.loadModules(new MainLocatorModule(this));
        mUsersComponent = mLocator.get(UsersComponent.class);
        mEnterpriseComponent = mLocator.get(EnterpriseComponent.class);
        mDeviceOwnerComponent = mLocator.get(DeviceOwnerComponent.class);
        mProfileOwnersComponent = mLocator.get(ProfileOwnersComponent.class);
        mTestAppsComponent = mLocator.get(TestAppsComponent.class);
        mContentTestApp = testApps()
                .query()
                .wherePackageName()
                .isEqualTo("com.android.ContentTestApp")
                .get();
        mMaxTestDuration = maxTestDuration;
        mSkipTestTeardown = TestApis.instrumentation().arguments().getBoolean(
                SKIP_TEST_TEARDOWN_KEY, false);
        mSkipClassTeardown = TestApis.instrumentation().arguments().getBoolean(
                SKIP_CLASS_TEARDOWN_KEY, false);

        mSkipTestsReason = TestApis.instrumentation().arguments().getString(SKIP_TESTS_REASON_KEY,
                "");
        mSkipTests = !mSkipTestsReason.isEmpty();
        mMinSdkVersion = TestApis.instrumentation().arguments().getInt(MIN_SDK_VERSION_KEY,
                SDK_INT);
        mAdditionalFeatures = Arrays.asList(TestApis.instrumentation().arguments().getString(
                ADDITIONAL_FEATURES_KEY, "").split(","));
    }

    public DeviceState() {
        this(MAX_TEST_DURATION);
    }

    /**
     * Obtains the instance of the given [clazz] from locator
     * This method shouldn't be used in test directly
     */
    public <T> T getDependency(Class<T> clazz) {
        return mLocator.get(clazz);
    }

    @Override
    void setSkipTestTeardown(boolean skipTestTeardown) {
        mSkipTestTeardown = skipTestTeardown;
    }

    @Override
    void setUsingBedsteadJUnit4(boolean usingBedsteadJUnit4) {
        mUsingBedsteadJUnit4 = usingBedsteadJUnit4;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (description.isTest()) {
            return applyTest(base, description);
        } else if (description.isSuite()) {
            return applySuite(base, description);
        }
        throw new IllegalStateException("Unknown description type: " + description);
    }

    @Override
    protected void releaseResources() {
        Log.i(LOG_TAG, "Releasing resources");
        mLocator.releaseResources();
        mAddedUserRestrictions.clear();
        mRegisteredBroadcastReceivers.clear();
        mOriginalProperties.clear();
        mOriginalGlobalSettings.clear();
        mOriginalSecureSettings.clear();
        mTemporaryContentSuggestionsServiceSet.clear();
        mOriginalDefaultContentSuggestionsServiceEnabled.clear();
        mCreatedAccounts.clear();
        mAccounts.clear();
        mAccountAuthenticators.clear();

        Log.i(LOG_TAG, "Shutting down test thread executor");
        mTestExecutor.shutdown();
    }

    private Statement applyTest(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Future<Throwable> future = mTestExecutor.submit(() -> {
                    try {
                        executeTest(base, description);
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                });

                Throwable t;
                try {
                    t = future.get(mMaxTestDuration.getSeconds(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    StackTraceElement[] stack = mTestThread.getStackTrace();
                    future.cancel(true);

                    AssertionError assertionError = new AssertionError(
                            "Timed out executing test " + description.getDisplayName()
                                    + " after " + MAX_TEST_DURATION, e);
                    assertionError.setStackTrace(stack);
                    onTestFailed(assertionError);
                    throw assertionError;
                }
                if (t != null) {
                    if (t.getStackTrace().length > 0) {
                        if (t.getStackTrace()[0].getMethodName().equals("createExceptionOrNull")) {
                            SystemServerException s = TestApis.logcat().findSystemServerException(
                                    t);
                            if (s != null) {
                                onTestFailed(s);
                                throw s;
                            }
                        }
                    }

                    onTestFailed(t);
                    throw t;
                }
            }
        };
    }

    private void executeTest(Statement base, Description description) throws Throwable {
        String testName = description.getMethodName();

        try {
            prepareTestState(description);
            base.evaluate();
        } finally {
            Log.d(LOG_TAG, "Tearing down state for test " + testName);
            teardownNonShareableState();
            if (!mSkipTestTeardown) {
                teardownShareableState();
            }
            Log.d(LOG_TAG, "Finished tearing down state for test " + testName);
        }
    }

    void prepareTestState(Description description) throws Throwable {
        String testName = description.getMethodName();

        Log.d(LOG_TAG, "Preparing state for test " + testName);
        mLocator.prepareTestState();
        testApps().snapshot();
        Tags.clearTags();
        Tags.addTag(Tags.USES_DEVICESTATE);
        assumeFalse(mSkipTestsReason, mSkipTests);
        assertFalse(mFailTestsReason, mFailTests);
        TestApis.packages().features().addAll(mAdditionalFeatures);

        // Ensure that tests only see events from the current test
        EventLogs.resetLogs();

        // Avoid cached activities on screen
        TestApis.activities().clearAllActivities();

        mMinSdkVersionCurrentTest = mMinSdkVersion;
        List<Annotation> annotations = getAnnotations(description);
        applyAnnotations(annotations, /* isTest= */ true);

        Log.d(LOG_TAG, "Finished preparing state for test " + testName);
    }

    private void applyAnnotations(List<Annotation> annotations, boolean isTest) throws Throwable {
        //TODO b/345391598 move handling annotations to AnnotationExecutors
        Log.d(LOG_TAG, "Applying annotations: " + annotations);
        for (final Annotation annotation : annotations) {
            Log.v(LOG_TAG, "Applying annotation " + annotation);

            Class<? extends Annotation> annotationType = annotation.annotationType();

            EnsureHasNoProfileAnnotation ensureHasNoProfileAnnotation =
                    annotationType.getAnnotation(EnsureHasNoProfileAnnotation.class);
            if (ensureHasNoProfileAnnotation != null) {
                UserType userType = (UserType) annotation.annotationType()
                        .getMethod(FOR_USER).invoke(annotation);
                ensureHasNoProfile(ensureHasNoProfileAnnotation.value(), userType);
                continue;
            }

            EnsureHasProfileAnnotation ensureHasProfileAnnotation =
                    annotationType.getAnnotation(EnsureHasProfileAnnotation.class);
            if (ensureHasProfileAnnotation != null) {
                UserType forUser = (UserType) annotation.annotationType()
                        .getMethod(FOR_USER).invoke(annotation);
                OptionalBoolean installInstrumentedApp = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(INSTALL_INSTRUMENTED_APP).invoke(annotation);

                OptionalBoolean isQuietModeEnabled = OptionalBoolean.ANY;

                try {
                    isQuietModeEnabled = (OptionalBoolean)
                            annotation.annotationType().getMethod(
                                    IS_QUIET_MODE_ENABLED).invoke(annotation);
                } catch (NoSuchMethodException e) {
                    // Expected, we default to ANY
                }

                boolean dpcIsPrimary = false;
                boolean useParentInstance = false;
                TestAppQueryBuilder dpcQuery = null;
                if (ensureHasProfileAnnotation.hasProfileOwner()) {
                    // TODO(b/206441366): Add instant app support
                    requireNotInstantApp(
                            "Instant Apps cannot run Enterprise Tests", FailureMode.SKIP);

                    dpcIsPrimary = (boolean)
                            annotation.annotationType()
                                    .getMethod(DPC_IS_PRIMARY).invoke(annotation);

                    if (dpcIsPrimary) {
                        useParentInstance = (boolean)
                                annotation.annotationType()
                                        .getMethod(USE_PARENT_INSTANCE_OF_DPC).invoke(
                                                annotation);

                    }

                    dpcQuery = getDpcQueryFromAnnotation(annotation);
                }

                OptionalBoolean switchedToParentUser = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(SWITCHED_TO_PARENT_USER).invoke(annotation);

                ensureHasProfile(
                        ensureHasProfileAnnotation.value(), installInstrumentedApp,
                        forUser, ensureHasProfileAnnotation.hasProfileOwner(),
                        dpcIsPrimary, useParentInstance, switchedToParentUser, isQuietModeEnabled,
                        getDpcKeyFromAnnotation(annotation, "dpcKey"), dpcQuery);

                if (ensureHasProfileAnnotation.hasProfileOwner()) {
                    if (isOrganizationOwned(annotation)) {
                        // It doesn't make sense to have COPE + DO
                        mDeviceOwnerComponent.ensureHasNoDeviceOwner();
                    }

                    ((ProfileOwner) profileOwner(
                            workProfile(forUser)).devicePolicyController()).setIsOrganizationOwned(
                            isOrganizationOwned(annotation));
                }

                continue;
            }

            if (annotation instanceof EnsureDefaultContentSuggestionsServiceEnabled ensureDefaultContentSuggestionsServiceEnabledAnnotation) {

                ensureDefaultContentSuggestionsServiceEnabled(
                        ensureDefaultContentSuggestionsServiceEnabledAnnotation.onUser(),
                        /* enabled= */ true
                );
                continue;
            }

            if (annotation instanceof EnsureDefaultContentSuggestionsServiceDisabled ensureDefaultContentSuggestionsServiceDisabledAnnotation) {

                ensureDefaultContentSuggestionsServiceEnabled(
                        ensureDefaultContentSuggestionsServiceDisabledAnnotation.onUser(),
                        /* enabled= */ false
                );
                continue;
            }

            if (annotation instanceof EnsureHasTestContentSuggestionsService ensureHasTestContentSuggestionsServiceAnnotation) {
                ensureHasTestContentSuggestionsService(
                        ensureHasTestContentSuggestionsServiceAnnotation.onUser());
                continue;
            }

            if (annotation instanceof TestTag testTagAnnotation) {
                Tags.addTag(testTagAnnotation.value());
            }

            RequireRunOnProfileAnnotation requireRunOnProfileAnnotation =
                    annotationType.getAnnotation(RequireRunOnProfileAnnotation.class);
            if (requireRunOnProfileAnnotation != null) {
                OptionalBoolean installInstrumentedAppInParent = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod("installInstrumentedAppInParent")
                                .invoke(annotation);

                OptionalBoolean switchedToParentUser = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(SWITCHED_TO_PARENT_USER).invoke(annotation);


                boolean dpcIsPrimary = false;
                Set<String> affiliationIds = null;
                TestAppQueryBuilder dpcQuery = null;
                if (requireRunOnProfileAnnotation.hasProfileOwner()) {
                    dpcIsPrimary = (boolean)
                            annotation.annotationType()
                                    .getMethod(DPC_IS_PRIMARY).invoke(annotation);
                    affiliationIds = new HashSet<>(Arrays.asList((String[])
                            annotation.annotationType()
                                    .getMethod(AFFILIATION_IDS).invoke(annotation)));
                    dpcQuery = getDpcQueryFromAnnotation(annotation);
                }

                requireRunOnProfile(requireRunOnProfileAnnotation.value(),
                        installInstrumentedAppInParent,
                        requireRunOnProfileAnnotation.hasProfileOwner(),
                        dpcIsPrimary, /* useParentInstance= */ false,
                        switchedToParentUser, affiliationIds,
                        getDpcKeyFromAnnotation(annotation, "dpcKey"), dpcQuery);

                if (requireRunOnProfileAnnotation.hasProfileOwner()) {
                    if (isOrganizationOwned(annotation)) {
                        // It doesn't make sense to have COPE + DO
                        mDeviceOwnerComponent.ensureHasNoDeviceOwner();
                    }

                    ((ProfileOwner) profileOwner(
                            workProfile()).devicePolicyController()).setIsOrganizationOwned(
                            isOrganizationOwned(annotation));
                }

                continue;
            }

            if (annotation instanceof AdditionalQueryParameters additionalQueryParametersAnnotation) {
                mAdditionalQueryParameters.put(
                        additionalQueryParametersAnnotation.forTestApp(),
                        additionalQueryParametersAnnotation.query());
            }

            if (annotation instanceof EnsureTestAppInstalled ensureTestAppInstalledAnnotation) {
                mTestAppsComponent.ensureTestAppInstalled(
                        ensureTestAppInstalledAnnotation.key(),
                        ensureTestAppInstalledAnnotation.query(),
                        resolveUserTypeToUser(ensureTestAppInstalledAnnotation.onUser()),
                        ensureTestAppInstalledAnnotation.isPrimary()
                );
                continue;
            }

            if (annotation instanceof EnsureTestAppHasPermission ensureTestAppHasPermissionAnnotation) {
                mTestAppsComponent.ensureTestAppHasPermission(
                        ensureTestAppHasPermissionAnnotation.testAppKey(),
                        ensureTestAppHasPermissionAnnotation.value(),
                        ensureTestAppHasPermissionAnnotation.minVersion(),
                        ensureTestAppHasPermissionAnnotation.maxVersion(),
                        ensureTestAppHasPermissionAnnotation.failureMode()
                );
                continue;
            }

            if (annotation instanceof EnsureTestAppDoesNotHavePermission ensureTestAppDoesNotHavePermissionAnnotation) {
                mTestAppsComponent.ensureTestAppDoesNotHavePermission(
                        ensureTestAppDoesNotHavePermissionAnnotation.testAppKey(),
                        ensureTestAppDoesNotHavePermissionAnnotation.value(),
                        ensureTestAppDoesNotHavePermissionAnnotation.failureMode()
                );
                continue;
            }

            if (annotation instanceof EnsureTestAppHasAppOp ensureTestAppHasAppOpAnnotation) {
                mTestAppsComponent.ensureTestAppHasAppOp(
                        ensureTestAppHasAppOpAnnotation.testAppKey(),
                        ensureTestAppHasAppOpAnnotation.value(),
                        ensureTestAppHasAppOpAnnotation.minVersion(),
                        ensureTestAppHasAppOpAnnotation.maxVersion()
                );
                continue;
            }

            if (annotation instanceof RequireSystemServiceAvailable requireSystemServiceAvailableAnnotation) {
                requireSystemServiceAvailable(requireSystemServiceAvailableAnnotation.value(),
                        requireSystemServiceAvailableAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireTargetSdkVersion requireTargetSdkVersionAnnotation) {

                requireTargetSdkVersion(
                        requireTargetSdkVersionAnnotation.min(),
                        requireTargetSdkVersionAnnotation.max(),
                        requireTargetSdkVersionAnnotation.failureMode());

                continue;
            }

            if (annotation instanceof RequireSdkVersion requireSdkVersionAnnotation) {

                if (requireSdkVersionAnnotation.reason().isEmpty()) {
                    requireSdkVersion(
                            requireSdkVersionAnnotation.min(),
                            requireSdkVersionAnnotation.max(),
                            requireSdkVersionAnnotation.failureMode());
                } else {
                    requireSdkVersion(
                            requireSdkVersionAnnotation.min(),
                            requireSdkVersionAnnotation.max(),
                            requireSdkVersionAnnotation.failureMode(),
                            requireSdkVersionAnnotation.reason());
                }

                continue;
            }

            if (annotation instanceof UsesAnnotationExecutor) {
                usesAnnotationExecutor((UsesAnnotationExecutor) annotation);
                continue;
            }

            if (annotation instanceof RequirePackageInstalled requirePackageInstalledAnnotation) {
                requirePackageInstalled(
                        requirePackageInstalledAnnotation.value(),
                        requirePackageInstalledAnnotation.onUser(),
                        requirePackageInstalledAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequirePackageNotInstalled requirePackageNotInstalledAnnotation) {
                requirePackageNotInstalled(
                        requirePackageNotInstalledAnnotation.value(),
                        requirePackageNotInstalledAnnotation.onUser(),
                        requirePackageNotInstalledAnnotation.failureMode()
                );
                continue;
            }

            if (annotation instanceof EnsurePackageNotInstalled ensurePackageNotInstalledAnnotation) {
                ensurePackageNotInstalled(
                        ensurePackageNotInstalledAnnotation.value(),
                        ensurePackageNotInstalledAnnotation.onUser()
                );
                continue;
            }

            if (annotation instanceof EnsurePasswordSet ensurePasswordSetAnnotation) {
                mUsersComponent.ensurePasswordSet(
                        ensurePasswordSetAnnotation.forUser(),
                        ensurePasswordSetAnnotation.password());
                continue;
            }

            if (annotation instanceof EnsurePasswordNotSet ensurePasswordNotSetAnnotation) {
                mUsersComponent.ensurePasswordNotSet(ensurePasswordNotSetAnnotation.forUser());
                continue;
            }

            if (annotation instanceof EnsureBluetoothEnabled) {
                ensureBluetoothEnabled();
                continue;
            }

            if (annotation instanceof EnsureBluetoothDisabled) {
                ensureBluetoothDisabled();
                continue;
            }

            if (annotation instanceof EnsureWifiEnabled) {
                ensureWifiEnabled();
                continue;
            }

            if (annotation instanceof EnsureWifiDisabled) {
                ensureWifiDisabled();
                continue;
            }

            if (annotation instanceof EnsureSecureSettingSet ensureSecureSettingSetAnnotation) {
                ensureSecureSettingSet(
                        ensureSecureSettingSetAnnotation.onUser(),
                        ensureSecureSettingSetAnnotation.key(),
                        ensureSecureSettingSetAnnotation.value());
                continue;
            }

            if (annotation instanceof EnsurePropertySet ensurePropertySetAnnotation) {
                ensurePropertySet(
                        ensurePropertySetAnnotation.key(), ensurePropertySetAnnotation.value());
                continue;
            }

            if (annotation instanceof EnsureUsingDisplayTheme ensureUsingDisplayTheme) {
                ensureUsingDisplayTheme(ensureUsingDisplayTheme.theme());
                continue;
            }

            if (annotation instanceof EnsureUsingScreenOrientation ensureUsingScreenOrientation) {
                ensureUsingScreenOrientation(ensureUsingScreenOrientation.orientation());
                continue;
            }

            if (annotation instanceof EnsureGlobalSettingSet ensureGlobalSettingSetAnnotation) {
                ensureGlobalSettingSet(
                        ensureGlobalSettingSetAnnotation.key(),
                        ensureGlobalSettingSetAnnotation.value());
                continue;
            }

            if (annotation instanceof RequireInstantApp requireInstantAppAnnotation) {
                requireInstantApp(requireInstantAppAnnotation.reason(),
                        requireInstantAppAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireNotInstantApp requireNotInstantAppAnnotation) {
                requireNotInstantApp(requireNotInstantAppAnnotation.reason(),
                        requireNotInstantAppAnnotation.failureMode());
                continue;
            }

            UsesAnnotationExecutor usesAnnotationExecutorAnnotation =
                    annotationType.getAnnotation(UsesAnnotationExecutor.class);
            if (usesAnnotationExecutorAnnotation != null) {
                var executor = usesAnnotationExecutor(usesAnnotationExecutorAnnotation);
                executor.applyAnnotation(annotation);
                continue;
            }


            if (annotation instanceof EnsureHasAccountAuthenticator ensureHasAccountAuthenticatorAnnotation) {
                ensureHasAccountAuthenticator(ensureHasAccountAuthenticatorAnnotation.onUser());
                continue;
            }

            if (annotation instanceof EnsureHasAccount ensureHasAccountAnnotation) {
                ensureHasAccount(
                        ensureHasAccountAnnotation.onUser(),
                        ensureHasAccountAnnotation.key(),
                        ensureHasAccountAnnotation.features());
                continue;
            }

            if (annotation instanceof EnsureHasAccounts ensureHasAccountsAnnotation) {
                ensureHasAccounts(ensureHasAccountsAnnotation.value());
                continue;
            }

            if (annotation instanceof EnsureHasNoAccounts ensureHasNoAccountsAnnotation) {
                ensureHasNoAccounts(ensureHasNoAccountsAnnotation.onUser(),
                        ensureHasNoAccountsAnnotation.allowPreCreatedAccounts(),
                        ensureHasNoAccountsAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof EnsureHasUserRestriction ensureHasUserRestrictionAnnotation) {
                ensureHasUserRestriction(
                        ensureHasUserRestrictionAnnotation.value(),
                        ensureHasUserRestrictionAnnotation.onUser());
                continue;
            }

            if (annotation instanceof EnsureDoesNotHaveUserRestriction ensureDoesNotHaveUserRestrictionAnnotation) {
                ensureDoesNotHaveUserRestriction(
                        ensureDoesNotHaveUserRestrictionAnnotation.value(),
                        ensureDoesNotHaveUserRestrictionAnnotation.onUser());
                continue;
            }

            if (annotation instanceof RequireQuickSettingsSupport requireQuickSettingsSupport) {
                checkFailOrSkip("Device does not have quick settings",
                        TestApis.quickSettings().isSupported(),
                        requireQuickSettingsSupport.failureMode());
                continue;
            }

            if (annotation instanceof RequireHasDefaultBrowser requireHasDefaultBrowser) {
                UserReference user =
                        resolveUserTypeToUser(requireHasDefaultBrowser.forUser());

                checkFailOrSkip("User: " + user + " does not have a default browser",
                        TestApis.roles().hasBrowserRoleHolderAsUser(user),
                        requireHasDefaultBrowser.failureMode());
                continue;
            }

            if (annotation instanceof RequireTelephonySupport requireTelephonySupport) {
                checkFailOrSkip("Device does not have telephony support",
                        mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
                        requireTelephonySupport.failureMode());
                continue;
            }

            if (annotation instanceof EnsurePolicyOperationUnsafe ensurePolicyOperationUnsafeAnnotation) {
                ensurePolicyOperationUnsafe(ensurePolicyOperationUnsafeAnnotation.operation(),
                        ensurePolicyOperationUnsafeAnnotation.reason());

                continue;
            }

            if(annotation instanceof RequirePackageRespondsToIntent requirePackageRespondsToIntentAnnotation) {
                requirePackageRespondsToIntent(
                        requirePackageRespondsToIntentAnnotation.intent(),
                        resolveUserTypeToUser(requirePackageRespondsToIntentAnnotation.user()),
                        requirePackageRespondsToIntentAnnotation.failureMode());
                continue;
            }

            if(annotation instanceof RequireNoPackageRespondsToIntent requireNoPackageRespondsToIntentAnnotation) {
                requireNoPackageRespondsToIntent(
                        requireNoPackageRespondsToIntentAnnotation.intent(),
                        resolveUserTypeToUser(requireNoPackageRespondsToIntentAnnotation.user()),
                        requireNoPackageRespondsToIntentAnnotation.failureMode());
                continue;
            }

            if(annotation instanceof EnsurePackageRespondsToIntent ensurePackageRespondsToIntentAnnotation) {
                ensurePackageRespondsToIntent(
                        ensurePackageRespondsToIntentAnnotation.intent(),
                        resolveUserTypeToUser(ensurePackageRespondsToIntentAnnotation.user()),
                        ensurePackageRespondsToIntentAnnotation.failureMode());
                continue;
            }

            if(annotation instanceof EnsureNoPackageRespondsToIntent ensureNoPackageRespondsToIntentAnnotation) {
                ensureNoPackageRespondsToIntent(
                        ensureNoPackageRespondsToIntentAnnotation.intent(),
                        ensureNoPackageRespondsToIntentAnnotation.user());
                continue;
            }
        }

        requireSdkVersion(/* min= */ mMinSdkVersionCurrentTest,
                /* max= */ Integer.MAX_VALUE, FailureMode.SKIP);
    }

    private static TestAppQueryBuilder getDpcQueryFromAnnotation(Annotation annotation) {
        try {
            Method queryMethod = annotation.annotationType().getMethod("dpc");
            Query query = (Query) queryMethod.invoke(annotation);
            TestAppQueryBuilder queryBuilder = new TestAppProvider().query(query);

            return queryBuilder;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Log.i(LOG_TAG, "Unable to get dpc query value for "
                    + annotation.annotationType().getName(), e);
        }
        return new TestAppProvider().query(); // No dpc specified - use any
    }

    private static String getDpcKeyFromAnnotation(Annotation annotation, String keyName) {
        try {
            Method keyMethod = annotation.annotationType().getMethod(keyName);
            return (String) keyMethod.invoke(annotation);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Log.i(LOG_TAG, "Unable to get dpc key (" + keyName + ") value for "
                    + annotation.annotationType().getName(), e);
            return null;
        }
    }

    private List<Annotation> getAnnotations(Description description) {
        if (mUsingBedsteadJUnit4 && description.isTest()) {
            // The annotations are already exploded for tests
            return new ArrayList<>(description.getAnnotations());
        }

        // Otherwise we should build a new collection by recursively gathering annotations
        // if we find any which don't work without the runner we should error and fail the test
        List<Annotation> annotations = new ArrayList<>();

        if (description.isTest()) {
            annotations =
                    new ArrayList<>(Arrays.asList(description.getTestClass().getAnnotations()));
        }

        annotations.addAll(description.getAnnotations());
        annotations.sort(BedsteadJUnit4::annotationSorter);

        checkAnnotations(annotations);

        BedsteadJUnit4.resolveRecursiveAnnotations(
                this, annotations, /* parameterizedAnnotations= */ List.of());

        checkAnnotations(annotations);

        return annotations;
    }

    private void checkAnnotations(Collection<Annotation> annotations) {
        if (mUsingBedsteadJUnit4) {
            return;
        }
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getAnnotation(RequiresBedsteadJUnit4.class) != null
                    || annotation.annotationType().getAnnotation(
                    ParameterizedAnnotation.class) != null) {
                throw new AssertionFailedError("Test is annotated "
                        + annotation.annotationType().getSimpleName()
                        + " which requires using the BedsteadJUnit4 test runner");
            }
        }
    }

    private Statement applySuite(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mTestExecutor = Executors.newSingleThreadExecutor();

                Future<Thread> testThreadFuture = mTestExecutor.submit(Thread::currentThread);
                try {
                    mTestThread = testThreadFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new AssertionError(
                            "Error setting up DeviceState. Interrupted getting test thread", e);
                }

                checkValidAnnotations(description);

                TestClass testClass = new TestClass(description.getTestClass());

                if (mSkipTests || mFailTests) {
                    Log.d(
                            LOG_TAG,
                            "Skipping suite setup and teardown due to skipTests: "
                                    + mSkipTests
                                    + ", failTests: "
                                    + mFailTests);
                    base.evaluate();
                    return;
                }

                Log.d(LOG_TAG, "Preparing state for suite " + description.getClassName());

                Tags.clearTags();
                Tags.addTag(Tags.USES_DEVICESTATE);
                boolean isInstantApp = TestApis.packages().instrumented().isInstantApp();

                try {
                    TestApis.device().keepScreenOn(true);

                    if (!isInstantApp) {
                        TestApis.device().setKeyguardEnabled(false);
                    }
                    TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.FALSE);

                    try {
                        List<Annotation> annotations = new ArrayList<>(getAnnotations(description));
                        applyAnnotations(annotations, /* isTest= */ false);
                    } catch (AssumptionViolatedException e) {
                        Log.i(LOG_TAG, "Assumption failed during class setup", e);
                        mSkipTests = true;
                        mSkipTestsReason = e.getMessage();
                    } catch (AssertionError e) {
                        Log.i(LOG_TAG, "Assertion failed during class setup", e);
                        mFailTests = true;
                        mFailTestsReason = e.getMessage();
                    }

                    Log.d(
                            LOG_TAG,
                            "Finished preparing state for suite " + description.getClassName());

                    if (!mSkipTests && !mFailTests) {
                        // Tests may be skipped during the class setup
                        runAnnotatedMethods(testClass, BeforeClass.class);
                    }

                    base.evaluate();
                } finally {
                    runAnnotatedMethods(testClass, AfterClass.class);

                    if (!mSkipClassTeardown) {
                        teardownShareableState();
                    }

                    if (!isInstantApp) {
                        TestApis.device().setKeyguardEnabled(true);
                    }
                    // TODO(b/249710985): Reset to the default for the device or the previous value
                    // TestApis.device().keepScreenOn(false);
                    TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.ANY);

                    releaseResources();
                }
            }
        };
    }

    private static final Map<String, String>
            BANNED_ANNOTATIONS_TO_REPLACEMENTS = getBannedAnnotationsToReplacements();

    private static Map<String, String> getBannedAnnotationsToReplacements() {
        Map<String, String> bannedAnnotationsToReplacements = new HashMap<>();
        bannedAnnotationsToReplacements.put(org.junit.BeforeClass.class.getCanonicalName(), BeforeClass.class.getCanonicalName());
        bannedAnnotationsToReplacements.put(org.junit.AfterClass.class.getCanonicalName(), AfterClass.class.getCanonicalName());
        // bannedAnnotationsToReplacements.put("android.platform.test.annotations.RequiresFlagsEnabled", "com.android.bedstead.flags.annotations.RequireFlagsEnabled");
        // bannedAnnotationsToReplacements.put("android.platform.test.annotations.RequiresFlagsDisabled", "com.android.bedstead.flags.annotations.RequireFlagsDisabled");
        return bannedAnnotationsToReplacements;
    }

    private void checkValidAnnotations(Description classDescription) {
        for (Method method : classDescription.getTestClass().getMethods()) {
            for (Map.Entry<String, String> bannedAnnotation : BANNED_ANNOTATIONS_TO_REPLACEMENTS.entrySet()) {

                if (Arrays.stream(method.getAnnotations()).anyMatch((i) -> i.annotationType().getCanonicalName().equals(bannedAnnotation.getKey()))) {
                    throw new IllegalStateException("Do not use "
                            + bannedAnnotation.getKey()
                            + " when using DeviceState, replace with "
                            + bannedAnnotation.getValue());
                }
            }

            if (method.getAnnotation(BeforeClass.class) != null
                    || method.getAnnotation(AfterClass.class) != null) {
                checkPublicStaticVoidNoArgs(method);
            }
        }
    }

    private void checkPublicStaticVoidNoArgs(Method method) {
        if (method.getParameterTypes().length > 0) {
            throw new IllegalStateException(
                    "Method " + method.getName() + " should have no parameters");
        }
        if (method.getReturnType() != Void.TYPE) {
            throw new IllegalStateException("Method " + method.getName() + "() should be void");
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException(
                    "Method " + method.getName() + "() should be static");
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException(
                    "Method " + method.getName() + "() should be public");
        }
    }

    private void runAnnotatedMethods(
            TestClass testClass, Class<? extends Annotation> annotation) throws Throwable {
        List<FrameworkMethod> methods = new ArrayList<>(
                testClass.getAnnotatedMethods(annotation));
        Collections.reverse(methods);
        for (FrameworkMethod method : methods) {
            try {
                method.invokeExplosively(testClass.getJavaClass());
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private void requireRunOnProfile(String userType,
                                     OptionalBoolean installInstrumentedAppInParent,
                                     boolean hasProfileOwner, boolean dpcIsPrimary, boolean useParentInstance,
                                     OptionalBoolean switchedToParentUser, Set<String> affiliationIds,
                                     String dpcKey, TestAppQueryBuilder dpcQuery) {
        UserReference instrumentedUser = TestApis.users().instrumented();

        assumeTrue("This test only runs on users of type " + userType,
                instrumentedUser.type().name().equals(userType));

        if (!mProfiles.containsKey(instrumentedUser.type())) {
            mProfiles.put(instrumentedUser.type(), new HashMap<>());
        }

        mProfiles.get(instrumentedUser.type()).put(instrumentedUser.parent(),
                instrumentedUser);

        if (installInstrumentedAppInParent.equals(OptionalBoolean.TRUE)) {
            TestApis.packages().find(sContext.getPackageName()).installExisting(
                    instrumentedUser.parent());
        } else if (installInstrumentedAppInParent.equals(OptionalBoolean.FALSE)) {
            TestApis.packages().find(sContext.getPackageName()).uninstall(
                    instrumentedUser.parent());
        }

        if (hasProfileOwner) {
            mProfileOwnersComponent.ensureHasProfileOwner(
                    instrumentedUser, dpcIsPrimary, useParentInstance, affiliationIds, dpcKey,
                    dpcQuery);
        } else {
            mProfileOwnersComponent.ensureHasNoProfileOwner(instrumentedUser);
        }

        mUsersComponent.ensureSwitchedToUser(switchedToParentUser, instrumentedUser.parent());
    }

    private void requireFeature(String feature, FailureMode failureMode) {
        checkFailOrSkip("Device must have feature " + feature,
                TestApis.packages().features().contains(feature), failureMode);
    }

    private void requireTargetSdkVersion(
            int min, int max, FailureMode failureMode) {
        int targetSdkVersion = TestApis.packages().instrumented().targetSdkVersion();

        checkFailOrSkip(
                "TargetSdkVersion must be between " + min + " and " + max
                        + " (inclusive) (version is " + targetSdkVersion + ")",
                min <= targetSdkVersion && max >= targetSdkVersion,
                failureMode
        );
    }

    private void requireSdkVersion(int min, int max, FailureMode failureMode) {
        requireSdkVersion(min, max, failureMode,
                "Sdk version must be between " + min + " and " + max + " (inclusive)");
    }

    private void requireSdkVersion(
            int min, int max, FailureMode failureMode, String failureMessage) {
        mMinSdkVersionCurrentTest = min;
        checkFailOrSkip(
                failureMessage + " (version is " + SDK_INT + ")",
                meetsSdkVersionRequirements(min, max),
                failureMode
        );
    }

    private static final String LOG_TAG = "DeviceState";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private final Map<com.android.bedstead.nene.users.UserType, Map<UserReference, UserReference>>
            mProfiles = new HashMap<>();

    private final Map<UserReference, Set<String>> mAddedUserRestrictions = new ConcurrentHashMap<>();
    private final Map<UserReference, Set<String>> mRemovedUserRestrictions = new ConcurrentHashMap<>();
    private final UsersComponent mUsersComponent;
    private final EnterpriseComponent mEnterpriseComponent;
    private final DeviceOwnerComponent mDeviceOwnerComponent;
    private final ProfileOwnersComponent mProfileOwnersComponent;
    private final TestAppsComponent mTestAppsComponent;
    private final List<BlockingBroadcastReceiver> mRegisteredBroadcastReceivers = new ArrayList<>();
    private Boolean mOriginalBluetoothEnabled;
    private DisplayProperties.Theme mOriginalDisplayTheme;
    private DisplayProperties.ScreenOrientation mOriginalScreenOrientation;

    private Boolean mOriginalWifiEnabled;
    private final Map<String, String> mOriginalProperties = new HashMap<>();
    private final Map<String, String> mOriginalGlobalSettings = new HashMap<>();
    public final Map<String, Query> mAdditionalQueryParameters = new HashMap<>();
    private final Map<UserReference, Boolean> mOriginalDefaultContentSuggestionsServiceEnabled =
            new HashMap<>();
    private final Set<UserReference> mTemporaryContentSuggestionsServiceSet =
            new HashSet<>();
    private final Map<UserReference, Map<String, String>> mOriginalSecureSettings = new HashMap<>();
    private final Set<AccountReference> mCreatedAccounts = new HashSet<>();
    private final Map<String, AccountReference> mAccounts = new HashMap<>();
    private final Map<UserReference, RemoteAccountAuthenticator> mAccountAuthenticators =
            new HashMap<>();

    /**
     * Get the {@link UserReference} of the work profile for the initial user.
     *
     * <p>If the current user is a work profile, then the current user will be returned.
     *
     * <p>This should only be used to get work profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed work profile
     */
    public UserReference workProfile() {
        return workProfile(/* forUser= */ UserType.INITIAL_USER);
    }

    /**
     * Get the {@link UserReference} of the work profile.
     *
     * <p>This should only be used to get work profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed work profile for the given user
     */
    public UserReference workProfile(UserType forUser) {
        return workProfile(resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the work profile.
     *
     * <p>This should only be used to get work profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed work profile for the given user
     */
    public UserReference workProfile(UserReference forUser) {
        return profile(MANAGED_PROFILE_TYPE_NAME, forUser);
    }

    /**
     * Get the {@link UserReference} of the profile of the given type for the given user.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile for the given user
     */
    public UserReference profile(String profileType, UserType forUser) {
        return profile(profileType, resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the profile for the current user.
     *
     * <p>If the current user is a profile of the correct type, then the current user will be
     * returned.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile
     */
    public UserReference profile(String profileType) {
        return profile(profileType, /* forUser= */ UserType.INSTRUMENTED_USER);
    }

    /**
     * Get the {@link UserReference} of the profile of the given type for the given user.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile for the given user
     */
    public UserReference profile(String profileType, UserReference forUser) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                TestApis.users().supportedType(profileType);

        if (resolvedUserType == null) {
            throw new IllegalStateException("Can not have a profile of type " + profileType
                    + " as they are not supported on this device");
        }

        return profile(resolvedUserType, forUser);
    }

    /**
     * Get the {@link UserReference} of the profile of the given type for the given user.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile for the given user
     */
    public UserReference profile(
            com.android.bedstead.nene.users.UserType userType, UserReference forUser) {
        if (userType == null || forUser == null) {
            throw new NullPointerException();
        }

        if (!mProfiles.containsKey(userType) || !mProfiles.get(userType).containsKey(forUser)) {
            UserReference parentUser = TestApis.users().instrumented().parent();

            if (parentUser != null) {
                if (mProfiles.containsKey(userType)
                        && mProfiles.get(userType).containsKey(parentUser)) {
                    return mProfiles.get(userType).get(parentUser);
                }
            }

            throw new IllegalStateException(
                    "No harrier-managed profile of type " + userType
                            + ". This method should only"
                            + " be used when Harrier has been used to create the profile.");
        }

        return mProfiles.get(userType).get(forUser);
    }

    /**
     * Get the {@link UserReference} of the tv profile for the current user
     *
     * <p>This should only be used to get tv profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed tv profile
     */
    public UserReference tvProfile() {
        return tvProfile(/* forUser= */ UserType.INSTRUMENTED_USER);
    }

    /**
     * Get the {@link UserReference} of the tv profile.
     *
     * <p>This should only be used to get tv profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed tv profile
     */
    public UserReference tvProfile(UserType forUser) {
        return tvProfile(resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the tv profile.
     *
     * <p>This should only be used to get tv profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed tv profile
     */
    public UserReference tvProfile(UserReference forUser) {
        return profile(TV_PROFILE_TYPE_NAME, forUser);
    }

    /**
     * Get the {@link UserReference} of the clone profile for the current user
     *
     * <p>This should only be used to get clone profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed clone profile
     */
    public UserReference cloneProfile() {
        return cloneProfile(/* forUser= */ UserType.INITIAL_USER);
    }

    /**
     * Get the {@link UserReference} of the clone profile.
     *
     * <p>This should only be used to get clone profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed clone profile
     */
    public UserReference cloneProfile(UserType forUser) {
        return cloneProfile(resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the clone profile.
     *
     * <p>This should only be used to get clone profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed clone profile
     */
    public UserReference cloneProfile(UserReference forUser) {
        return profile(CLONE_PROFILE_TYPE_NAME, forUser);
    }

    /**
     * Get the {@link UserReference} of the private profile for the current user
     *
     * <p>This should only be used to get private profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed private profile
     */
    public UserReference privateProfile() {
        return privateProfile(/* forUser= */ UserType.INITIAL_USER);
    }

    /**
     * Get the {@link UserReference} of the private profile.
     *
     * <p>This should only be used to get private profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed private profile
     */
    public UserReference privateProfile(UserType forUser) {
        return privateProfile(resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the private profile.
     *
     * <p>This should only be used to get private profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed private profile
     */
    public UserReference privateProfile(UserReference forUser) {
        return profile(PRIVATE_PROFILE_TYPE_NAME, forUser);
    }

    /**
     * Gets the user ID of the initial user.
     */
    // TODO(b/249047658): cache the initial user at the start of the run.
    public UserReference initialUser() {
        return TestApis.users().initial();
    }

    /**
     * Gets the user ID of the first human user on the device.
     *
     * @deprecated Use {@link #initialUser()} to ensure compatibility with Headless System User
     * Mode devices.
     */
    @Deprecated
    public UserReference primaryUser() {
        return TestApis.users().all()
                .stream().filter(UserReference::isPrimary).findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    /**
     * Get a secondary user.
     *
     * <p>This should only be used to get secondary users managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed secondary user
     */
    public UserReference secondaryUser() {
        return mUsersComponent.user(SECONDARY_USER_TYPE_NAME);
    }

    /**
     * Gets the user marked as "other" by use of the {@code @OtherUser} annotation.
     *
     * @throws IllegalStateException if there is no "other" user
     */
    public UserReference otherUser() {
        return mUsersComponent.otherUser();
    }

    private UserReference ensureHasProfile(
            String profileType,
            OptionalBoolean installInstrumentedApp,
            UserType forUser,
            boolean hasProfileOwner,
            boolean profileOwnerIsPrimary,
            boolean useParentInstance,
            OptionalBoolean switchedToParentUser,
            OptionalBoolean isQuietModeEnabled,
            String dpcKey,
            TestAppQueryBuilder dpcQuery) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                mUsersComponent.requireUserSupported(profileType, FailureMode.SKIP);

        UserReference forUserReference = resolveUserTypeToUser(forUser);

        UserReference profile =
                TestApis.users().findProfileOfType(resolvedUserType, forUserReference);
        if (profile == null) {
            if (profileType.equals(MANAGED_PROFILE_TYPE_NAME)) {
                // TODO(b/239961027): either remove this check (once tests on UserManagerTest /
                // MultipleUsersOnMultipleDisplaysTest uses non-work profiles) or add a unit test
                // for it on DeviceStateTest
                requireFeature(FEATURE_MANAGED_USERS, FailureMode.SKIP);

                // DO + work profile isn't a valid state
                mDeviceOwnerComponent.ensureHasNoDeviceOwner();
                ensureDoesNotHaveUserRestriction(DISALLOW_ADD_MANAGED_PROFILE, forUserReference);
            }

            profile = createProfile(resolvedUserType, forUserReference);
        }

        profile.start();

        if (isQuietModeEnabled == OptionalBoolean.TRUE) {
            profile.setQuietMode(true);
        } else if (isQuietModeEnabled == OptionalBoolean.FALSE) {
            profile.setQuietMode(false);
        }

        if (installInstrumentedApp.equals(OptionalBoolean.TRUE)) {
            TestApis.packages().find(sContext.getPackageName()).installExisting(
                    profile);
        } else if (installInstrumentedApp.equals(OptionalBoolean.FALSE)) {
            TestApis.packages().find(sContext.getPackageName()).uninstall(profile);
        }

        if (!mProfiles.containsKey(resolvedUserType)) {
            mProfiles.put(resolvedUserType, new HashMap<>());
        }

        mProfiles.get(resolvedUserType).put(forUserReference, profile);

        if (hasProfileOwner) {
            mProfileOwnersComponent.ensureHasProfileOwner(
                    profile, profileOwnerIsPrimary,
                    useParentInstance,
                    /* affiliationIds= */ null,
                    dpcKey,
                    dpcQuery);
        }

        mUsersComponent.ensureSwitchedToUser(switchedToParentUser, forUserReference);

        return profile;
    }

    private void ensureHasNoProfile(String profileType, UserType forUser) {
        UserReference forUserReference = resolveUserTypeToUser(forUser);
        com.android.bedstead.nene.users.UserType resolvedProfileType =
                TestApis.users().supportedType(profileType);

        if (resolvedProfileType == null) {
            // These profile types don't exist so there can't be any
            return;
        }

        UserReference profile =
                TestApis.users().findProfileOfType(
                        resolvedProfileType,
                        forUserReference);
        if (profile != null) {
            // We can't remove an organization owned profile
            ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(profile);
            if (profileOwner != null && profileOwner.isOrganizationOwned()) {
                profileOwner.setIsOrganizationOwned(false);
            }
            mUsersComponent.removeAndRecordUser(profile);
        }
    }

    private void ensureCanAddProfile(
            UserReference parent, com.android.bedstead.nene.users.UserType userType, FailureMode failureMode) {
        checkFailOrSkip("the device cannot add more profiles of type " + userType,
                parent.canCreateProfile(userType),
                failureMode);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(String action) {
        return registerBroadcastReceiver(action, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(IntentFilter intentFilter) {
        return registerBroadcastReceiver(intentFilter, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(
            String action, Function<Intent, Boolean> checker) {
        BlockingBroadcastReceiver broadcastReceiver =
                new BlockingBroadcastReceiver(mContext, action, checker);
        broadcastReceiver.register();
        mRegisteredBroadcastReceivers.add(broadcastReceiver);

        return broadcastReceiver;
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(
            IntentFilter intentfilter, Function<Intent, Boolean> checker) {
        BlockingBroadcastReceiver broadcastReceiver =
                new BlockingBroadcastReceiver(mContext, intentfilter, checker);
        broadcastReceiver.register();
        mRegisteredBroadcastReceivers.add(broadcastReceiver);

        return broadcastReceiver;
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, String action) {
        return registerBroadcastReceiverForUser(user, action, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, IntentFilter intentFilter) {
        return registerBroadcastReceiverForUser(user, intentFilter, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, String action, Function<Intent, Boolean> checker) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(
                            TestApis.context().androidContextAsUser(user), action, checker);
            broadcastReceiver.register();
            mRegisteredBroadcastReceivers.add(broadcastReceiver);

            return broadcastReceiver;
        }
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, IntentFilter intentFilter, Function<Intent, Boolean> checker) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(
                            TestApis.context().androidContextAsUser(user), intentFilter, checker);
            broadcastReceiver.register();
            mRegisteredBroadcastReceivers.add(broadcastReceiver);

            return broadcastReceiver;
        }
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(String action) {
        return registerBroadcastReceiverForAllUsers(action, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(
            IntentFilter intentFilter) {
        return registerBroadcastReceiverForAllUsers(intentFilter, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(
            String action, Function<Intent, Boolean> checker) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(mContext, action, checker);
            broadcastReceiver.registerForAllUsers();

            mRegisteredBroadcastReceivers.add(broadcastReceiver);

            return broadcastReceiver;
        }
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(
            IntentFilter intentFilter, Function<Intent, Boolean> checker) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(mContext, intentFilter, checker);
            broadcastReceiver.registerForAllUsers();

            mRegisteredBroadcastReceivers.add(broadcastReceiver);

            return broadcastReceiver;
        }
    }

    /**
     * Creates appropriate {@link UserReference} for the {@link UserType}
     */
    public UserReference resolveUserTypeToUser(UserType userType) {
        switch (userType) {
            case SYSTEM_USER:
                return TestApis.users().system();
            case INSTRUMENTED_USER:
                return TestApis.users().instrumented();
            case CURRENT_USER:
                return TestApis.users().current();
            case PRIMARY_USER:
                return primaryUser();
            case SECONDARY_USER:
                return secondaryUser();
            case WORK_PROFILE:
                return workProfile();
            case TV_PROFILE:
                return tvProfile();
            case DPC_USER:
                return dpc().user();
            case INITIAL_USER:
                return TestApis.users().initial();
            case ADDITIONAL_USER:
                return mUsersComponent.additionalUser();
            case CLONE_PROFILE:
                return cloneProfile();
            case PRIVATE_PROFILE:
                return privateProfile();
            case ADMIN_USER:
                return TestApis.users().all().stream().sorted(
                                Comparator.comparing(UserReference::id))
                        .filter(UserReference::isAdmin)
                        .findFirst().orElseThrow(
                                () -> new IllegalStateException("No admin user on device"));
            case ANY:
                throw new IllegalStateException("ANY UserType can not be used here");
            default:
                throw new IllegalArgumentException("Unknown user type " + userType);
        }
    }

    /**
     * Returns the additional user specified by annotation
     */
    public UserReference additionalUser() {
        return mUsersComponent.additionalUser();
    }

    void teardownNonShareableState() {
        mAdditionalQueryParameters.clear();
        mProfiles.clear();

        // TODO(b/329570492): Support sharing of theme in bedstead across tests
        if (mOriginalDisplayTheme != null) {
            Display.INSTANCE.setDisplayTheme(mOriginalDisplayTheme);
            mOriginalDisplayTheme = null;
        }

        // TODO(b/329570492): Support sharing of orientation in bedstead across tests
        if (mOriginalScreenOrientation != null) {
            Display.INSTANCE.setScreenOrientation(mOriginalScreenOrientation);
            mOriginalScreenOrientation = null;
        }

        for (Map.Entry<UserReference, Set<String>> userRestrictions
                : mAddedUserRestrictions.entrySet()) {
            for (String restriction : userRestrictions.getValue()) {
                ensureDoesNotHaveUserRestriction(restriction, userRestrictions.getKey());
            }
        }

        for (Map.Entry<UserReference, Set<String>> userRestrictions
                : mRemovedUserRestrictions.entrySet()) {
            for (String restriction : userRestrictions.getValue()) {
                ensureHasUserRestriction(restriction, userRestrictions.getKey());
            }
        }

        mAddedUserRestrictions.clear();
        mRemovedUserRestrictions.clear();


        for (BlockingBroadcastReceiver broadcastReceiver : mRegisteredBroadcastReceivers) {
            broadcastReceiver.unregisterQuietly();
        }
        mRegisteredBroadcastReceivers.clear();
        mAccounts.clear();
        mAccountAuthenticators.clear();

        mLocator.teardownNonShareableState();

        if (mNextSafetyOperationSet) {
            ensurePolicyOperationUnsafe(
                    CommonDevicePolicy.DevicePolicyOperation.OPERATION_NONE,
                    CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_NONE);
            mNextSafetyOperationSet = false;

            for (OperationSafetyReason reason : OperationSafetyReason.values()) {
                if (reason == OperationSafetyReason.OPERATION_SAFETY_REASON_NONE) continue;

                Poll.forValue("Is Safe Operation", () ->
                                TestApis.devicePolicy().isSafeOperation(reason))
                        .toBeEqualTo(true)
                        .await();
            }
        }
    }

    void teardown() {
        teardownNonShareableState();
        teardownShareableState();
    }

    private void teardownShareableState() {
        if (!mCreatedAccounts.isEmpty()) {
            mCreatedAccounts.forEach(AccountReference::remove);

            TestApis.devicePolicy().calculateHasIncompatibleAccounts();
        }

        if (mOriginalBluetoothEnabled != null) {
            TestApis.bluetooth().setEnabled(mOriginalBluetoothEnabled);
            mOriginalBluetoothEnabled = null;
        }

        if (mOriginalWifiEnabled != null) {
            TestApis.wifi().setEnabled(mOriginalWifiEnabled);
            mOriginalWifiEnabled = null;
        }

        for (Map.Entry<UserReference, Boolean> s
                : mOriginalDefaultContentSuggestionsServiceEnabled.entrySet()) {
            TestApis.content().suggestions().setDefaultServiceEnabled(s.getKey(), s.getValue());
        }
        mOriginalDefaultContentSuggestionsServiceEnabled.clear();

        for (UserReference u : mTemporaryContentSuggestionsServiceSet) {
            TestApis.content().suggestions().clearTemporaryService(u);
        }
        mTemporaryContentSuggestionsServiceSet.clear();

        for (Map.Entry<String, String> s : mOriginalGlobalSettings.entrySet()) {
            TestApis.settings().global().putString(s.getKey(), s.getValue());
        }
        mOriginalGlobalSettings.clear();

        for (Map.Entry<String, String> s : mOriginalProperties.entrySet()) {
            TestApis.properties().set(s.getKey(), s.getValue());
        }
        mOriginalProperties.clear();

        for (Map.Entry<UserReference, Map<String, String>> s : mOriginalSecureSettings.entrySet()) {
            for (Map.Entry<String, String> s2 : s.getValue().entrySet()) {
                TestApis.settings().secure().putString(s.getKey(), s2.getKey(), s2.getValue());
            }
        }
        mOriginalSecureSettings.clear();

        TestApis.activities().clearAllActivities();
        mLocator.teardownShareableState();
    }

    private UserReference createProfile(
            com.android.bedstead.nene.users.UserType profileType,
            UserReference parent
    ) {
        mUsersComponent.ensureCanAddUser();
        ensureCanAddProfile(parent, profileType, FailureMode.SKIP);

        if (profileType.name().equals("android.os.usertype.profile.CLONE")) {
            // Special case - we can't create a clone profile if this is set
            ensureDoesNotHaveUserRestriction(DISALLOW_ADD_CLONE_PROFILE, parent);
        } else if (profileType.name().equals("android.os.usertype.profile.PRIVATE")) {
            // Special case - we can't create a private profile if this is set
            ensureDoesNotHaveUserRestriction(DISALLOW_ADD_PRIVATE_PROFILE, parent);
        }

        try {
            return mUsersComponent.createUser(profileType, parent);
        } catch (NeneException e) {
            throw new IllegalStateException("Error creating profile of type " + profileType, e);
        }
    }

    /**
     * Get the {@link RemoteDpc} for the device owner controlled by Harrier.
     *
     * <p>If no Harrier-managed device owner exists, an exception will be thrown.
     *
     * <p>If the device owner is not a RemoteDPC then an exception will be thrown
     */
    public RemoteDpc deviceOwner() {
        return mDeviceOwnerComponent.deviceOwner();
    }

    /**
     * Get the {@link RemoteDpc} for the profile owner on the current user controlled by Harrier.
     *
     * <p>If no Harrier-managed profile owner exists, an exception will be thrown.
     *
     * <p>If the profile owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemoteDpc profileOwner() {
        return profileOwner(UserType.INSTRUMENTED_USER);
    }

    /**
     * Get the {@link RemoteDpc} for the profile owner on the given user controlled by Harrier.
     *
     * <p>If no Harrier-managed profile owner exists, an exception will be thrown.
     *
     * <p>If the profile owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemoteDpc profileOwner(UserType onUser) {
        if (onUser == null) {
            throw new NullPointerException();
        }

        return profileOwner(resolveUserTypeToUser(onUser));
    }

    /**
     * Get the {@link RemoteDpc} for the profile owner on the given user controlled by Harrier.
     *
     * <p>If no Harrier-managed profile owner exists, an exception will be thrown.
     *
     * <p>If the profile owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemoteDpc profileOwner(UserReference onUser) {
        return mProfileOwnersComponent.profileOwner(onUser);
    }

    private void requirePackageInstalled(
            String packageName, UserType forUser, FailureMode failureMode) {
        Package pkg = TestApis.packages().find(packageName);

        if (forUser.equals(UserType.ANY)) {
            checkFailOrSkip(
                    packageName + " is required to be installed",
                    !pkg.installedOnUsers().isEmpty(),
                    failureMode);
        } else {
            checkFailOrSkip(
                    packageName + " is required to be installed for " + forUser,
                    pkg.installedOnUser(resolveUserTypeToUser(forUser)),
                    failureMode);
        }
    }

    private void requirePackageNotInstalled(
            String packageName, UserType forUser, FailureMode failureMode) {
        Package pkg = TestApis.packages().find(packageName);

        if (forUser.equals(UserType.ANY)) {
            checkFailOrSkip(
                    packageName + " is required to be not installed",
                    pkg.installedOnUsers().isEmpty(),
                    failureMode);
        } else {
            checkFailOrSkip(
                    packageName + " is required to be not installed for " + forUser,
                    !pkg.installedOnUser(resolveUserTypeToUser(forUser)),
                    failureMode);
        }
    }

    private void ensurePackageNotInstalled(
            String packageName, UserType forUser) {
        Package pkg = TestApis.packages().find(packageName);

        if (forUser.equals(UserType.ANY)) {
            pkg.uninstallFromAllUsers();
        } else {
            UserReference user = resolveUserTypeToUser(forUser);
            pkg.uninstall(user);
        }
    }

    /**
     * Behaves like {@link #dpc()} except that when running on a delegate, this will return
     * the delegating DPC not the delegate.
     */
    public RemotePolicyManager dpcOnly() {
        return mEnterpriseComponent.dpcOnly();
    }

    /**
     * Get the most appropriate {@link RemotePolicyManager} instance for the device state.
     *
     * <p>This method should only be used by tests which are annotated with any of:
     * {@link PolicyAppliesTest}
     * {@link PolicyDoesNotApplyTest}
     * {@link CanSetPolicyTest}
     * {@link CannotSetPolicyTest}
     *
     * <p>This may be a DPC, a delegate, or a normal app with or without given permissions.
     *
     * <p>If no policy manager is set as "primary" for the device state, then this method will first
     * check for a profile owner in the current user, or else check for a device owner.
     *
     * <p>If no Harrier-managed profile owner or device owner exists, an exception will be thrown.
     *
     * <p>If the profile owner or device owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemotePolicyManager dpc() {
        return mEnterpriseComponent.dpc();
    }


    /**
     * Get the Device Policy Management Role Holder.
     */
    public RemoteDevicePolicyManagerRoleHolder dpmRoleHolder() {
        return mEnterpriseComponent.dpmRoleHolder();
    }

    /**
     * Get a {@link TestAppProvider} which is cleared between tests.
     *
     * <p>Note that you must still manage the test apps manually. To have the infrastructure
     * automatically remove test apps use the {@link EnsureTestAppInstalled} annotation.
     */
    public TestAppProvider testApps() {
        return mTestAppsComponent.getTestAppProvider();
    }

    /**
     * Get a test app installed with @EnsureTestAppInstalled with no key.
     */
    public TestAppInstance testApp() {
        return testApp(DEFAULT_KEY);
    }

    /**
     * Get a test app installed with `@EnsureTestAppInstalled` with the given key.
     */
    public TestAppInstance testApp(String key) {
        return mTestAppsComponent.testApp(key);
    }

    private void ensureBluetoothEnabled() {
        // TODO(b/220306133): bluetooth from background
        Assume.assumeTrue("Can only configure bluetooth from foreground",
                TestApis.users().instrumented().isForeground());

        ensureDoesNotHaveUserRestriction(DISALLOW_BLUETOOTH, UserType.ANY);

        if (mOriginalBluetoothEnabled == null) {
            mOriginalBluetoothEnabled = TestApis.bluetooth().isEnabled();
        }
        TestApis.bluetooth().setEnabled(true);
    }

    private void ensureBluetoothDisabled() {
        Assume.assumeTrue("Can only configure bluetooth from foreground",
                TestApis.users().instrumented().isForeground());

        if (mOriginalBluetoothEnabled == null) {
            mOriginalBluetoothEnabled = TestApis.bluetooth().isEnabled();
        }
        TestApis.bluetooth().setEnabled(false);
    }

    private void ensureWifiEnabled() {
        if (mOriginalWifiEnabled == null) {
            mOriginalWifiEnabled = TestApis.wifi().isEnabled();
        }
        TestApis.wifi().setEnabled(true);
    }

    private void ensureWifiDisabled() {
        if (mOriginalWifiEnabled == null) {
            mOriginalWifiEnabled = TestApis.wifi().isEnabled();
        }
        TestApis.wifi().setEnabled(false);
    }

    private boolean isOrganizationOwned(Annotation annotation)
            throws InvocationTargetException, IllegalAccessException {
        Method isOrganizationOwnedMethod;

        try {
            isOrganizationOwnedMethod = annotation.annotationType().getMethod(
                    "isOrganizationOwned");
        } catch (NoSuchMethodException ignored) {
            return false;
        }

        return (boolean) isOrganizationOwnedMethod.invoke(annotation);
    }

    private void ensureGlobalSettingSet(String key, String value) {
        if (!mOriginalGlobalSettings.containsKey(key)) {
            mOriginalGlobalSettings.put(key, TestApis.settings().global().getString(key));
        }

        TestApis.settings().global().putString(key, value);
    }

    private void ensureSecureSettingSet(UserType user, String key, String value) {
        ensureSecureSettingSet(resolveUserTypeToUser(user), key, value);
    }

    private void ensureSecureSettingSet(UserReference user, String key, String value) {
        if (!mOriginalSecureSettings.containsKey(user)) {
            mOriginalSecureSettings.put(user, new HashMap<>());
        }
        if (!mOriginalSecureSettings.get(user).containsKey(key)) {
            mOriginalSecureSettings.get(user)
                    .put(key, TestApis.settings().secure().getString(user, key));
        }

        TestApis.settings().secure().putString(user, key, value);
    }

    private void ensureDefaultContentSuggestionsServiceEnabled(UserType user, boolean enabled) {
        ensureDefaultContentSuggestionsServiceEnabled(resolveUserTypeToUser(user), enabled);
    }

    private void ensureDefaultContentSuggestionsServiceEnabled(UserReference user,
                                                               boolean enabled) {
        boolean currentValue = TestApis.content().suggestions().defaultServiceEnabled(user);

        if (currentValue == enabled) {
            return;
        }

        if (!mOriginalDefaultContentSuggestionsServiceEnabled.containsKey(user)) {
            mOriginalDefaultContentSuggestionsServiceEnabled.put(user, currentValue);
        }

        TestApis.content().suggestions().setDefaultServiceEnabled(enabled);
    }

    private final TestApp mContentTestApp;
    private final ComponentReference mContentSuggestionsService =
            ComponentReference.unflattenFromString(
                    "com.android.ContentTestApp/.ContentSuggestionsService");

    private void ensureHasTestContentSuggestionsService(UserType user) {
        ensureHasTestContentSuggestionsService(resolveUserTypeToUser(user));
    }

    private void ensureHasTestContentSuggestionsService(UserReference user) {
        ensureDefaultContentSuggestionsServiceEnabled(user, /* enabled= */ false);
        mTestAppsComponent.ensureTestAppInstalled("content", mContentTestApp, user);

        mTemporaryContentSuggestionsServiceSet.add(user);

        TestApis.content().suggestions().setTemporaryService(user, mContentSuggestionsService);
    }

    private void requireInstantApp(String reason, FailureMode failureMode) {
        checkFailOrSkip("Test only runs as an instant-app: " + reason,
                TestApis.packages().instrumented().isInstantApp(), failureMode);
    }

    private void requireNotInstantApp(String reason, FailureMode failureMode) {
        checkFailOrSkip("Test does not run as an instant-app: " + reason,
                !TestApis.packages().instrumented().isInstantApp(), failureMode);
    }

    /**
     * Access harrier-managed accounts on the instrumented user.
     */
    public RemoteAccountAuthenticator accounts() {
        return accounts(TestApis.users().instrumented());
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    public RemoteAccountAuthenticator accounts(UserType user) {
        return accounts(resolveUserTypeToUser(user));
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    public RemoteAccountAuthenticator accounts(UserReference user) {
        if (!mAccountAuthenticators.containsKey(user)) {
            throw new IllegalStateException("No Harrier-Managed account authenticator on user "
                    + user + ". Did you use @EnsureHasAccountAuthenticator or @EnsureHasAccount?");
        }

        return mAccountAuthenticators.get(user);
    }

    private void ensureHasAccountAuthenticator(UserType onUser) {
        UserReference user = resolveUserTypeToUser(onUser);
        // We don't use .install() so we can rely on the default testapp sharing/uninstall logic
        mTestAppsComponent.ensureTestAppInstalled(REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP,
                user);

        mAccountAuthenticators.put(user, RemoteAccountAuthenticator.install(user));
    }

    private void ensureHasAccount(UserType onUser, String key, String[] features) {
        ensureHasAccount(onUser, key, features, new HashSet<>());
    }


    private AccountReference ensureHasAccount(UserType onUser, String key, String[] features,
                                              Set<AccountReference> ignoredAccounts) {
        ensureHasAccountAuthenticator(onUser);

        Optional<AccountReference> account =
                accounts(onUser).allAccounts().stream().filter(i -> !ignoredAccounts.contains(i))
                        .findFirst();

        if (account.isPresent()) {
            accounts(onUser).setFeatures(account.get(), new HashSet<>(Arrays.asList(features)));
            mAccounts.put(key, account.get());
            TestApis.devicePolicy().calculateHasIncompatibleAccounts();
            return account.get();
        }

        AccountReference createdAccount = accounts(onUser).addAccount()
                .features(new HashSet<>(Arrays.asList(features)))
                .add();
        mCreatedAccounts.add(createdAccount);
        mAccounts.put(key, createdAccount);
        TestApis.devicePolicy().calculateHasIncompatibleAccounts();
        return createdAccount;
    }

    private void ensureHasAccounts(EnsureHasAccount[] accounts) {
        Set<AccountReference> ignoredAccounts = new HashSet<>();

        for (EnsureHasAccount account : accounts) {
            ignoredAccounts.add(ensureHasAccount(
                    account.onUser(), account.key(), account.features(), ignoredAccounts));
        }
    }

    /**
     * See {@link DeviceState#ensureHasNoAccounts(UserReference, boolean, FailureMode)}
     * @deprecated do not use it in tests
     */
    //TODO(karzelek): move it outside of DeviceState
    @Deprecated
    public void ensureHasNoAccounts(UserType userType, boolean allowPreCreatedAccounts,
            FailureMode failureMode) {
        if (userType == UserType.ANY) {
            TestApis.users().all().forEach(user -> ensureHasNoAccounts(user,
                    allowPreCreatedAccounts, failureMode));
        } else {
            ensureHasNoAccounts(resolveUserTypeToUser(userType),
                    allowPreCreatedAccounts, failureMode);
        }
    }

    /**
     * Ensure that the given user has no accounts
     * <p>
     * Do not use this method inside tests, instead use the {@link EnsureHasNoAccounts} annotation.
     * @deprecated do not use it in tests
     */
    //TODO(karzelek): move it outside of DeviceState
    @Deprecated
    public void ensureHasNoAccounts(UserReference user, boolean allowPreCreatedAccounts,
            FailureMode failureMode) {
        if (REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP.pkg().installedOnUser(user)) {
            user.start(); // The user has to be started to remove accounts

            RemoteAccountAuthenticator.install(user).allAccounts()
                    .forEach(AccountReference::remove);
        }

        Set<AccountReference> accounts = TestApis.accounts().all(user);

        // If allowPreCreatedAccounts is enabled, that means it's okay to have
        // pre created accounts on the device.
        // Now to EnsureHasNoAccounts we will only check that there are no non-pre created accounts.
        // Non pre created accounts either have ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_DISALLOWED
        // or do not have ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED
        if (allowPreCreatedAccounts) {
            accounts = accounts.stream()
                    .filter(accountReference -> !accountReference.hasFeature(
                            DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED)
                            || accountReference.hasFeature(
                            DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_DISALLOWED))
                    .collect(Collectors.toSet());
        }

        if (!accounts.isEmpty()) {
            failOrSkip("Expected no user created accounts on user " + user
                    + " but there was some that could not be removed.", failureMode);
        }

        TestApis.devicePolicy().calculateHasIncompatibleAccounts();
    }

    /**
     * Get the default account defined with {@link EnsureHasAccount}.
     */
    public AccountReference account() {
        return account(DEFAULT_ACCOUNT_KEY);
    }

    /**
     * Get the account defined with {@link EnsureHasAccount} with a given key.
     */
    public AccountReference account(String key) {
        if (!mAccounts.containsKey(key)) {
            throw new IllegalStateException("No account for key " + key);
        }

        return mAccounts.get(key);
    }

    @Override
    boolean isHeadlessSystemUserMode() {
        return TestApis.users().isHeadlessSystemUserMode();
    }

    private AnnotationExecutor usesAnnotationExecutor(UsesAnnotationExecutor executorClassName) {
        return mLocator.get(getAnnotationExecutorClass(executorClassName));
    }

    private void ensureHasUserRestriction(String restriction, UserType onUser) {
        ensureHasUserRestriction(restriction, resolveUserTypeToUser(onUser));
    }

    private void ensureHasUserRestriction(String restriction, UserReference onUser) {
        if (TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            return;
        }

        // TODO use TestApis.root().testUsesAdbRoot when this is modularised
        boolean shouldRunAsRoot = Tags.hasTag("adb-root");
        if (shouldRunAsRoot) {
            Log.i(LOG_TAG, "Trying to set user restriction as root.");
            try {
                TestApis.devicePolicy().userRestrictions(onUser).set(restriction,
                        /* set= */ true);
            } catch (AdbException e) {
                Log.i(LOG_TAG,
                        "Unable to set user restriction as root, trying to set using heuristics.");
                trySetUserRestriction(onUser, restriction);
            }
        } else {
            trySetUserRestriction(onUser, restriction);
        }

        if (!TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            String message =
                    "Infra cannot set user restriction " + restriction + (shouldRunAsRoot ? ""
                            : ". Please add RequireAdbRoot to enable root capabilities.");
            throw new AssumptionViolatedException(message);
        }

        if (mRemovedUserRestrictions.containsKey(onUser)
                && mRemovedUserRestrictions.get(onUser).contains(restriction)) {
            mRemovedUserRestrictions.get(onUser).remove(restriction);
        } else {
            if (!mAddedUserRestrictions.containsKey(onUser)) {
                mAddedUserRestrictions.put(onUser, new HashSet<>());
            }

            mAddedUserRestrictions.get(onUser).add(restriction);
        }

        if (!TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            throw new NeneException("Error setting user restriction " + restriction);
        }
    }

    private void trySetUserRestriction(UserReference onUser, String restriction) {
        Log.i(LOG_TAG, "Trying to set user restriction using heuristics.");

        boolean hasSet = false;

        if (onUser.equals(TestApis.users().system())) {
            hasSet = trySetUserRestrictionWithDeviceOwner(restriction);
        }

        if (!hasSet) {
            hasSet = trySetUserRestrictionWithProfileOwner(onUser, restriction);
        }

        if (!hasSet && !onUser.equals(TestApis.users().system())) {
            trySetUserRestrictionWithDeviceOwner(restriction);
        }
    }

    private boolean trySetUserRestrictionWithDeviceOwner(String restriction) {
        mDeviceOwnerComponent.ensureHasDeviceOwner(FailureMode.FAIL,
                /* isPrimary= */ false, EnsureHasDeviceOwner.HeadlessDeviceOwnerType.NONE,
                /* affiliationIds= */ new HashSet<>(), /* type= */ DeviceOwnerType.DEFAULT,
                EnsureHasDeviceOwner.DEFAULT_KEY, new TestAppProvider().query());

        RemotePolicyManager dpc = deviceOwner();
        try {
            dpc.devicePolicyManager().addUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private boolean trySetUserRestrictionWithProfileOwner(UserReference onUser,
                                                          String restriction) {
        mProfileOwnersComponent.ensureHasProfileOwner(onUser,
                /* isPrimary= */ false, /* isParentInstance= */ false,
                /* affiliationIds= */ new HashSet<>(), EnsureHasProfileOwnerKt.DEFAULT_KEY,
                new TestAppProvider().query());

        RemotePolicyManager dpc = profileOwner(onUser);
        try {
            dpc.devicePolicyManager().addUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private boolean tryClearUserRestrictionWithDeviceOwner(String restriction) {
        mDeviceOwnerComponent.ensureHasDeviceOwner(FailureMode.FAIL,
                /* isPrimary= */ false, EnsureHasDeviceOwner.HeadlessDeviceOwnerType.NONE,
                /* affiliationIds= */ new HashSet<>(), /* type= */ DeviceOwnerType.DEFAULT,
                EnsureHasDeviceOwner.DEFAULT_KEY, new TestAppProvider().query());

        RemotePolicyManager dpc = deviceOwner();
        try {
            dpc.devicePolicyManager().clearUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private boolean tryClearUserRestrictionWithProfileOwner(UserReference onUser,
                                                            String restriction) {
        mProfileOwnersComponent.ensureHasProfileOwner(onUser,
                /* isPrimary= */ false, /* isParentInstance= */ false,
                /* affiliationIds= */ new HashSet<>(), EnsureHasProfileOwnerKt.DEFAULT_KEY,
                new TestAppProvider().query());

        RemotePolicyManager dpc = profileOwner(onUser);
        try {
            dpc.devicePolicyManager().clearUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    /**
     * Ensure that a user restriction isn't set on the given user.
     * <p>
     * @deprecated Do not use this method inside tests,
     * instead use the {@link EnsureDoesNotHaveUserRestriction} annotation.
     */
    //TODO(karzelek): move it outside of DeviceState
    @Deprecated
    public void ensureDoesNotHaveUserRestriction(String restriction, UserType onUser) {
        if (onUser == UserType.ANY) {
            for (UserReference userReference : TestApis.users().all()) {
                ensureDoesNotHaveUserRestriction(restriction, userReference);
            }
            return;
        }

        ensureDoesNotHaveUserRestriction(restriction, resolveUserTypeToUser(onUser));
    }

    private void tryClearUserRestriction(UserReference onUser, String restriction) {
        if (restriction.equals(DISALLOW_ADD_MANAGED_PROFILE)) {
            // Special case - set by the system whenever there is a Device Owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner();
        } else if (restriction.equals(DISALLOW_ADD_CLONE_PROFILE)) {
            // Special case - set by the system whenever there is a Device Owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner();
        } else if (restriction.equals(DISALLOW_ADD_PRIVATE_PROFILE)) {
            // Special case - set by the system whenever there is a Device Owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner();
        } else if (restriction.equals(DISALLOW_ADD_USER)) {
            // Special case - set by the system whenever there is a Device Owner or
            // organization-owned profile owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner();

            ProfileOwner orgOwnedProfileOwner =
                    TestApis.devicePolicy().getOrganizationOwnedProfileOwner();
            if (orgOwnedProfileOwner != null) {
                mProfileOwnersComponent.ensureHasNoProfileOwner(orgOwnedProfileOwner.user());
                return;
            }
        }

        boolean hasCleared = !TestApis.devicePolicy().userRestrictions(onUser).isSet(
                restriction);

        if (!hasCleared && onUser.equals(TestApis.users().system())) {
            hasCleared = tryClearUserRestrictionWithDeviceOwner(restriction);
        }

        if (!hasCleared) {
            hasCleared = tryClearUserRestrictionWithProfileOwner(onUser, restriction);
        }

        if (!hasCleared && !onUser.equals(TestApis.users().system())) {
            tryClearUserRestrictionWithDeviceOwner(restriction);
        }
    }

    /**
     * Ensure that a user restriction isn't set on the given user.
     * <p>
     * @deprecated Do not use this method inside tests,
     * instead use the {@link EnsureDoesNotHaveUserRestriction} annotation.
     */
    //TODO(karzelek): move it outside of DeviceState
    @Deprecated
    public void ensureDoesNotHaveUserRestriction(String restriction, UserReference onUser) {
        if (!TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            return;
        }

        // TODO use TestApis.root().testUsesAdbRoot when this is modularised
        boolean shouldRunAsRoot = Tags.hasTag("adb-root");
        if (shouldRunAsRoot) {
            Log.i(LOG_TAG, "Trying to clear user restriction as root.");
            try {
                TestApis.devicePolicy().userRestrictions(onUser).set(restriction,
                        /* set= */ false);
            } catch (AdbException e) {
                Log.i(
                        LOG_TAG,
                        "Unable to clear user restriction as root, trying to clear using"
                            + " heuristics.",
                        e);
                tryClearUserRestriction(onUser, restriction);
            }
        } else {
            tryClearUserRestriction(onUser, restriction);
        }

        if (TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            String message =
                    "Infra cannot remove user restriction " + restriction + (shouldRunAsRoot ? ""
                            : ". If this test requires capabilities only available on devices "
                            + "where adb has root, add @RequireAdbRoot to the test.");
            throw new AssumptionViolatedException(message);
        }

        if (mAddedUserRestrictions.containsKey(onUser)
                && mAddedUserRestrictions.get(onUser).contains(restriction)) {
            mAddedUserRestrictions.get(onUser).remove(restriction);
        } else {
            if (!mRemovedUserRestrictions.containsKey(onUser)) {
                mRemovedUserRestrictions.put(onUser, new HashSet<>());
            }

            mRemovedUserRestrictions.get(onUser).add(restriction);
        }
    }

    private void requireSystemServiceAvailable(Class<?> serviceClass, FailureMode failureMode) {
        checkFailOrSkip("Requires " + serviceClass + " to be available",
                TestApis.services().serviceIsAvailable(serviceClass), failureMode);
    }


    private void ensurePolicyOperationUnsafe(
            CommonDevicePolicy.DevicePolicyOperation operation,
            CommonDevicePolicy.OperationSafetyReason reason) {
        mNextSafetyOperationSet = true;
        TestApis.devicePolicy().setNextOperationSafety(operation, reason);
        mNextSafetyOperationSet = true;
        TestApis.devicePolicy().setNextOperationSafety(operation, reason);
    }

    private void ensurePropertySet(String key, String value) {
        if (!mOriginalProperties.containsKey(key)) {
            mOriginalProperties.put(key, TestApis.properties().get(key));
        }

        TestApis.properties().set(key, value);
    }

    private void ensureUsingDisplayTheme(DisplayProperties.Theme theme) {
        if (mOriginalDisplayTheme == null) {
            mOriginalDisplayTheme = Display.INSTANCE.getDisplayTheme();
        }
        Display.INSTANCE.setDisplayTheme(theme);
    }

    private void ensureUsingScreenOrientation(DisplayProperties.ScreenOrientation orientation) {
        if (mOriginalScreenOrientation == null) {
            mOriginalScreenOrientation = Display.INSTANCE.getScreenOrientation();
        }
        Display.INSTANCE.setScreenOrientation(orientation);
    }

    private void requirePackageRespondsToIntent(com.android.bedstead.harrier.annotations.Intent paramIntent, UserReference user, FailureMode failureMode) {
        Intent intent = new Intent(/* action= */ paramIntent.action());
        boolean packageResponded = TestApis.packages().queryIntentActivities(user, intent, /* flags= */0).size() > 0;

        if(packageResponded) {
            checkFailOrSkip(
                    "Requires at least one package to respond to this intent.",
                    /* value= */ true,
                    failureMode);
        }
        else {
            failOrSkip("Requires at least one package to respond to this intent.", failureMode);
        }
    }

    private void requireNoPackageRespondsToIntent(com.android.bedstead.harrier.annotations.Intent paramIntent, UserReference user, FailureMode failureMode) {
        Intent intent = new Intent(/* action= */ paramIntent.action());
        boolean noPackageResponded = TestApis.packages().queryIntentActivities(user, intent, /* flags= */0).isEmpty();

        if(noPackageResponded) {
            checkFailOrSkip(
                    "Requires no package to respond to this intent.",
                    /* value= */ true,
                    failureMode);
        }
        else {
            failOrSkip("Requires no package to respond to this intent.", failureMode);
        }
    }

    private void ensurePackageRespondsToIntent(com.android.bedstead.harrier.annotations.Intent paramIntent, UserReference user, FailureMode failureMode) {
        Intent intent = new Intent(/* action= */ paramIntent.action());
        boolean packageResponded = TestApis.packages().queryIntentActivities(user, intent, /* flags= */0).size() > 0;

        if(!packageResponded) {
            try {
                mTestAppsComponent.ensureTestAppInstalled(
                        /* testApp= */ testApps().query().whereActivities().contains(
                                        activity().where().intentFilters().contains(
                                                intentFilter().where().actions().contains(paramIntent.action())))
                                .get()
                        , user);
            } catch (NotFoundException notFoundException) {
                failOrSkip(
                        "Could not found the testApp which contains an activity matching the intent"
                            + " action '"
                                + paramIntent.action()
                                + "'.",
                        failureMode);
            }
        }
    }

    private void ensureNoPackageRespondsToIntent(com.android.bedstead.harrier.annotations.Intent paramIntent, UserType user) {
        Intent intent = new Intent(/* action= */ paramIntent.action());
        List<ResolveInfoWrapper> resolveInfoWrappers = TestApis.packages().queryIntentActivities(resolveUserTypeToUser(user), intent, /* flags= */0);

        for (ResolveInfoWrapper resolveInfoWrapper : resolveInfoWrappers) {
            String packageName = resolveInfoWrapper.activityInfo().packageName;
            ensurePackageNotInstalled(packageName, user);
        }
    }

    void onTestFailed(Throwable exception) {
        createMissingFailureDumpers();
        for (FailureDumper failureDumper : mLocator.getAllFailureDumpers()) {
            try {
                failureDumper.onTestFailed(exception);
            } catch (Throwable t) {
                // Ignore exceptions otherwise they'd hide the actual failure
                Log.e(LOG_TAG, "Error in onTestFailed for " + failureDumper, t);
            }
        }
    }

    private void createMissingFailureDumpers() {
        for (String className : FailureDumper.Companion.getFailureDumpers()) {
            try {
                mLocator.get(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
