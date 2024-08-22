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
import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip;
import static com.android.bedstead.harrier.annotations.EnsureTestAppInstalled.DEFAULT_KEY;
import static com.android.bedstead.harrier.annotations.UsesAnnotationExecutorKt.getAnnotationExecutorClass;
import static com.android.bedstead.harrier.annotations.UsesTestRuleExecutorKt.getTestRuleExecutorClass;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.bedstead.nene.utils.StringLinesDiff.DEVICE_POLICY_STANDARD_LINES_DIFFERENCE;
import static com.android.bedstead.nene.utils.Versions.meetsSdkVersionRequirements;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.bedstead.enterprise.DeviceAdminComponent;
import com.android.bedstead.enterprise.DeviceOwnerComponent;
import com.android.bedstead.enterprise.EnterpriseComponent;
import com.android.bedstead.enterprise.ProfileOwnersComponent;
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor;
import com.android.bedstead.harrier.annotations.UsesTestRuleExecutor;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4;
import com.android.bedstead.harrier.components.AccountsComponent;
import com.android.bedstead.harrier.components.TestAppsComponent;
import com.android.bedstead.multiuser.UsersComponent;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountReference;
import com.android.bedstead.nene.devicepolicy.DevicePolicy;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.logcat.SystemServerException;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver;
import com.android.bedstead.nene.utils.FailureDumper;
import com.android.bedstead.nene.utils.StringLinesDiff;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator;
import com.android.bedstead.remotedpc.RemoteDeviceAdmin;
import com.android.bedstead.remotedpc.RemoteDevicePolicyManagerRoleHolder;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.remotedpc.RemotePolicyManager;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.eventlib.EventLogs;

import junit.framework.AssertionFailedError;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

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

    // We timeout 10 seconds before the infra would timeout
    private static final Duration MAX_TEST_DURATION =
            Duration.ofMillis(
                    Long.parseLong(TestApis.instrumentation().arguments().getString(
                            "timeout_msec", "600000")) - 2000);

    // We allow overriding the limit on a class-by-class basis
    private final Duration mMaxTestDuration;
    private static final boolean TRACK_DUMPSYS_DEVICE_POLICY_LEAKS = TestApis
            .instrumentation()
            .arguments()
            .getBoolean("track-dumpsys-device-policy-leaks", false);
    private static final boolean THROW_ON_DEVICE_POLICY_LEAKS = TestApis
            .instrumentation()
            .arguments()
            .getBoolean("throw-on-device-policy-leaks", false);

    private ExecutorService mTestExecutor;
    private Thread mTestThread;

    private final BedsteadServiceLocator mLocator = new BedsteadServiceLocator();

    public DeviceState(Duration maxTestDuration) {
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

    private UsersComponent usersComponent() {
        return getDependency(UsersComponent.class);
    }

    private EnterpriseComponent enterpriseComponent() {
        return getDependency(EnterpriseComponent.class);
    }

    private AccountsComponent accountsComponent() {
        return getDependency(AccountsComponent.class);
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
        mLocator.clearDependencies();
        mRegisteredBroadcastReceivers.clear();

        Log.i(LOG_TAG, "Shutting down test thread executor");
        mTestExecutor.shutdown();
    }

    private Statement applyTest(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Future<Throwable> future = mTestExecutor.submit(() -> {
                    FailureDumper.Companion.registerCurrentTest(
                            description.getClassName() + "#" + description.getMethodName()
                    );
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
                                    + " after " + mMaxTestDuration.getSeconds() + " seconds", e);
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
        String devicePolicyDumpBeforeTests = null;
        if (THROW_ON_DEVICE_POLICY_LEAKS) {
            devicePolicyDumpBeforeTests = DevicePolicy.INSTANCE.dump();
        }

        try {
            prepareTestState(description);
            base.evaluate();
        } finally {
            Log.d(LOG_TAG, "Tearing down state for test " + testName);

            // Teardown any external rule this test depends on.
            for (TestRuleExecutor externalRuleExecutor : mLocator.getAllTestRuleExecutors()) {
                externalRuleExecutor.teardown(/* deviceState= */ this);
            }

            teardownNonShareableState();
            if (!mSkipTestTeardown || THROW_ON_DEVICE_POLICY_LEAKS) {
                teardownShareableState();
            }
            Log.d(LOG_TAG, "Finished tearing down state for test " + testName);
            if (devicePolicyDumpBeforeTests != null) {
                printDevicePolicyDumpDifference(devicePolicyDumpBeforeTests);
            }
        }
    }

    void prepareTestState(Description description) {
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
        applyAnnotations(annotations);

        List<Annotation> testRulesExecutorAnnotations = annotations.stream()
                .filter(a -> a.annotationType().getAnnotation(UsesTestRuleExecutor.class) != null)
                .collect(Collectors.toList());
        prepareExternalRule(description, testRulesExecutorAnnotations);

        Log.d(LOG_TAG, "Finished preparing state for test " + testName);
    }

    private void applyAnnotations(List<Annotation> annotations) {
        Log.d(LOG_TAG, "Applying annotations: " + annotations);
        for (final Annotation annotation : annotations) {
            Log.v(LOG_TAG, "Applying annotation " + annotation);

            if (annotation instanceof RequireSdkVersion) {
                RequireSdkVersion requireSdkVersionAnnotation = (RequireSdkVersion) annotation;
                requireSdkVersion(requireSdkVersionAnnotation);
            } else {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                UsesAnnotationExecutor usesAnnotationExecutorAnnotation =
                        annotationType.getAnnotation(UsesAnnotationExecutor.class);
                if (usesAnnotationExecutorAnnotation != null) {
                    var executor = usesAnnotationExecutor(usesAnnotationExecutorAnnotation);
                    executor.applyAnnotation(annotation);
                }
            }
        }

        requireSdkVersion(/* min= */ mMinSdkVersionCurrentTest,
                /* max= */ Integer.MAX_VALUE, FailureMode.SKIP);
    }

    private void requireSdkVersion(RequireSdkVersion requireSdkVersion) {
        if (requireSdkVersion.reason().isEmpty()) {
            requireSdkVersion(
                    requireSdkVersion.min(),
                    requireSdkVersion.max(),
                    requireSdkVersion.failureMode()
            );
        } else {
            requireSdkVersion(
                    requireSdkVersion.min(),
                    requireSdkVersion.max(),
                    requireSdkVersion.failureMode(),
                    requireSdkVersion.reason()
            );
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

                String devicePolicyDumpBeforeTests = null;
                if (TRACK_DUMPSYS_DEVICE_POLICY_LEAKS) {
                    devicePolicyDumpBeforeTests = DevicePolicy.INSTANCE.dump();
                }

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
                        applyAnnotations(annotations);
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

                    if (!mSkipClassTeardown || TRACK_DUMPSYS_DEVICE_POLICY_LEAKS) {
                        teardownShareableState();
                    }

                    if (!isInstantApp) {
                        TestApis.device().setKeyguardEnabled(true);
                    }
                    // TODO(b/249710985): Reset to the default for the device or the previous value
                    // TestApis.device().keepScreenOn(false);
                    TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.ANY);

                    releaseResources();

                    if (devicePolicyDumpBeforeTests != null) {
                        printDevicePolicyDumpDifference(devicePolicyDumpBeforeTests);
                    }
                }
            }
        };
    }

    private void printDevicePolicyDumpDifference(@Nonnull String devicePolicyDumpBeforeTests) {
        String currentDump = DevicePolicy.INSTANCE.dump();
        // TODO (b/348162683) replace StringLinesDiff with a proper diff
        StringLinesDiff diff = new StringLinesDiff(devicePolicyDumpBeforeTests, currentDump);
        if (diff.countLinesDifference() > DEVICE_POLICY_STANDARD_LINES_DIFFERENCE) {
            Log.w(LOG_TAG, "device_policy dump:\n" + currentDump);
            String message = "device_policy state has changed, probably a " +
                    "state leak, these are the new lines:\n" + diff.extraLinesString();
            if (THROW_ON_DEVICE_POLICY_LEAKS) {
                throw new NeneException(message);
            } else {
                Log.w(LOG_TAG, message);
            }
        }
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
    private final List<BlockingBroadcastReceiver> mRegisteredBroadcastReceivers = new ArrayList<>();

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
        return enterpriseComponent().workProfile();
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
        return enterpriseComponent().workProfile(forUser);
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
        return enterpriseComponent().workProfile(forUser);
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
        return usersComponent().profile(profileType, forUser);
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
     * Get the {@link UserReference} of the tv profile for the current user
     *
     * <p>This should only be used to get tv profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed tv profile
     */
    public UserReference tvProfile() {
        return usersComponent().tvProfile();
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
        return usersComponent().tvProfile(forUser);
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
        return usersComponent().tvProfile(forUser);
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
        return usersComponent().cloneProfile();
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
        return usersComponent().cloneProfile(forUser);
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
        return usersComponent().cloneProfile(forUser);
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
        return usersComponent().privateProfile();
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
        return usersComponent().privateProfile(forUser);
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
        return usersComponent().privateProfile(forUser);
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
    @SuppressWarnings("deprecation")
    @Deprecated
    public UserReference primaryUser() {
        return TestApis.users().primary();
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
        return usersComponent().user(SECONDARY_USER_TYPE_NAME);
    }

    /**
     * Gets the user marked as "other" by use of the {@code @OtherUser} annotation.
     *
     * @throws IllegalStateException if there is no "other" user
     */
    public UserReference otherUser() {
        return usersComponent().otherUser();
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
     * Returns the additional user specified by annotation
     */
    public UserReference additionalUser() {
        return usersComponent().additionalUser();
    }

    void teardownNonShareableState() {
        for (BlockingBroadcastReceiver broadcastReceiver : mRegisteredBroadcastReceivers) {
            broadcastReceiver.unregisterQuietly();
        }
        mRegisteredBroadcastReceivers.clear();
        mLocator.teardownNonShareableState();
    }

    void teardown() {
        teardownNonShareableState();
        teardownShareableState();
    }

    private void teardownShareableState() {
        TestApis.activities().clearAllActivities();
        mLocator.teardownShareableState();
    }

    /**
     * Get the {@link RemoteDpc} for the device owner controlled by Harrier.
     *
     * <p>If no Harrier-managed device owner exists, an exception will be thrown.
     *
     * <p>If the device owner is not a RemoteDPC then an exception will be thrown
     */
    public RemoteDpc deviceOwner() {
        return getDependency(DeviceOwnerComponent.class).deviceOwner();
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
        return getDependency(ProfileOwnersComponent.class).profileOwner(onUser);
    }

    /**
     * Get the {@link RemoteDpc} for the profile owner on the given user controlled by Harrier.
     *
     * <p>If no Harrier-managed profile owner exists, an exception will be thrown.
     *
     * <p>If the profile owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemoteDpc profileOwner(UserReference onUser) {
        return getDependency(ProfileOwnersComponent.class).profileOwner(onUser);
    }

    /**
     * Get the [RemoteDeviceAdmin] for the device admin set using
     * `EnsureHasDeviceAdmin` without specifying a custom key.
     *
     * If no Harrier-managed device admin exists, an exception will be thrown.
     */
    public RemoteDeviceAdmin deviceAdmin() {
        return getDependency(DeviceAdminComponent.class).deviceAdmin();
    }

    /**
     * Get the [RemoteDeviceAdmin] for the device admin with the specified key set on the
     * user and controlled by Harrier.
     *
     * If no Harrier-managed device admin exists for the given key, an exception will be thrown.
     */
    public RemoteDeviceAdmin deviceAdmin(String key) {
        return getDependency(DeviceAdminComponent.class).deviceAdmin(key);
    }

    /**
     * Behaves like {@link #dpc()} except that when running on a delegate, this will return
     * the delegating DPC not the delegate.
     */
    public RemotePolicyManager dpcOnly() {
        return enterpriseComponent().dpcOnly();
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
     * <p>This may be a DPC, a delegate, a device admin, or a normal app with or without given
     * permissions.
     *
     * <p>If no policy manager is set as "primary" for the device state, then this method will first
     * check for a profile owner in the current user, or else check for a device owner.
     *
     * <p>If no Harrier-managed profile owner or device owner exists, an exception will be thrown.
     *
     * <p>If the profile owner or device owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemotePolicyManager dpc() {
        return enterpriseComponent().dpc();
    }


    /**
     * Get the Device Policy Management Role Holder.
     */
    public RemoteDevicePolicyManagerRoleHolder dpmRoleHolder() {
        return enterpriseComponent().dpmRoleHolder();
    }

    /**
     * Get a {@link TestAppProvider} which is cleared between tests.
     *
     * <p>Note that you must still manage the test apps manually. To have the infrastructure
     * automatically remove test apps use the {@link EnsureTestAppInstalled} annotation.
     */
    public TestAppProvider testApps() {
        return getDependency(TestAppsComponent.class).getTestAppProvider();
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
        return getDependency(TestAppsComponent.class).testApp(key);
    }

    /**
     * Access harrier-managed accounts on the instrumented user.
     */
    public RemoteAccountAuthenticator accounts() {
        return accountsComponent().accounts();
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    public RemoteAccountAuthenticator accounts(UserType user) {
        return accountsComponent().accounts(user);
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    public RemoteAccountAuthenticator accounts(UserReference user) {
        return accountsComponent().accounts(user);
    }

    /**
     * Get the default account defined with {@link EnsureHasAccount}.
     */
    public AccountReference account() {
        return accountsComponent().account();
    }

    /**
     * Get the account defined with {@link EnsureHasAccount} with a given key.
     */
    public AccountReference account(String key) {
        return accountsComponent().account(key);
    }

    @Override
    boolean isHeadlessSystemUserMode() {
        return TestApis.users().isHeadlessSystemUserMode();
    }

    private AnnotationExecutor usesAnnotationExecutor(UsesAnnotationExecutor executorClassName) {
        return mLocator.get(getAnnotationExecutorClass(executorClassName));
    }

    private TestRuleExecutor usesTestRuleExecutor(UsesTestRuleExecutor executorClassName) {
        return mLocator.get(getTestRuleExecutorClass(executorClassName));
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
                var unused = mLocator.get(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void prepareExternalRule(Description description,
            List<Annotation> testRulesExecutorAnnotations) {
        for (Annotation annotation : testRulesExecutorAnnotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            UsesTestRuleExecutor usesTestRuleExecutorAnnotation =
                    annotationType.getAnnotation(UsesTestRuleExecutor.class);
            Log.d(LOG_TAG, "Preparing " + usesTestRuleExecutorAnnotation +
                    " for test " + description.getMethodName());
            TestRuleExecutor externalRuleExecutor =
                    usesTestRuleExecutor(usesTestRuleExecutorAnnotation);
            externalRuleExecutor.applyTestRule(/* deviceState= */ this, annotation, description);
        }
    }
}
