/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.appenumeration.cts;

import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallback;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.hamcrest.Matchers;
import org.hamcrest.core.IsNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class AppEnumerationTests {

    private static final String PKG_BASE = "android.appenumeration.";

    /** A package with no published API and so isn't queryable by anything but package name */
    private static final String TARGET_NO_API = PKG_BASE + "noapi";
    /** A package that declares itself force queryable, making it visible to all other packages */
    private static final String TARGET_FORCEQUERYABLE = PKG_BASE + "forcequeryable";
    /** A package that exposes itself via various intent filters (activities, services, etc.) */
    private static final String TARGET_FILTERS = PKG_BASE + "filters";
    /** A package that exposes nothing, but is part of a shared user */
    private static final String TARGET_SHARED_USER = PKG_BASE + "noapi.shareduid";

    /** A package that queries nothing, but is part of a shared user */
    private static final String QUERIES_NOTHING_SHARED_USER =
            PKG_BASE + "queries.nothing.shareduid";
    /** A package that has no queries tag or permission to query any specific packages */
    private static final String QUERIES_NOTHING = PKG_BASE + "queries.nothing";
    /** A package that has no queries tag or permissions but targets Q */
    private static final String QUERIES_NOTHING_Q = PKG_BASE + "queries.nothing.q";
    /** A package that has no queries but gets the QUERY_ALL_PACKAGES permission */
    private static final String QUERIES_NOTHING_PERM = PKG_BASE + "queries.nothing.haspermission";
    /** A package that queries for the action in {@link #TARGET_FILTERS} activity filter */
    private static final String QUERIES_ACTIVITY_ACTION = PKG_BASE + "queries.activity.action";
    /** A package that queries for the action in {@link #TARGET_FILTERS} service filter */
    private static final String QUERIES_SERVICE_ACTION = PKG_BASE + "queries.service.action";
    /** A package that queries for the authority in {@link #TARGET_FILTERS} provider */
    private static final String QUERIES_PROVIDER_AUTH = PKG_BASE + "queries.provider.authority";
    /** Queries for the unexported action in {@link #TARGET_FILTERS} activity filter */
    private static final String QUERIES_UNEXPORTED_ACTIVITY_ACTION =
            PKG_BASE + "queries.activity.action.unexported";
    /** Queries for the unexported action in {@link #TARGET_FILTERS} service filter */
    private static final String QUERIES_UNEXPORTED_SERVICE_ACTION =
            PKG_BASE + "queries.service.action.unexported";
    /** Queries for the unexported authority in {@link #TARGET_FILTERS} provider */
    private static final String QUERIES_UNEXPORTED_PROVIDER_AUTH =
            PKG_BASE + "queries.provider.authority.unexported";
    /** A package that queries for {@link #TARGET_NO_API} package */
    private static final String QUERIES_PACKAGE = PKG_BASE + "queries.pkg";

    private static final String[] ALL_QUERIES_TARGETING_Q_PACKAGES = {
            QUERIES_NOTHING,
            QUERIES_NOTHING_PERM,
            QUERIES_ACTIVITY_ACTION,
            QUERIES_SERVICE_ACTION,
            QUERIES_PROVIDER_AUTH,
            QUERIES_UNEXPORTED_ACTIVITY_ACTION,
            QUERIES_UNEXPORTED_SERVICE_ACTION,
            QUERIES_UNEXPORTED_PROVIDER_AUTH,
            QUERIES_PACKAGE,
            QUERIES_NOTHING_SHARED_USER
    };

    private static Handler sResponseHandler;
    private static HandlerThread sResponseThread;

    private static boolean sGlobalFeatureEnabled;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setup() {
        final String deviceConfigResponse =
                SystemUtil.runShellCommand(
                        "device_config get package_manager_service "
                                + "package_query_filtering_enabled")
                        .trim();
        if ("null".equalsIgnoreCase(deviceConfigResponse) || deviceConfigResponse.isEmpty()) {
            sGlobalFeatureEnabled = true;
        } else {
            sGlobalFeatureEnabled = Boolean.parseBoolean(deviceConfigResponse);
        }
        System.out.println("Feature enabled: " + sGlobalFeatureEnabled);
        if (!sGlobalFeatureEnabled) return;

        sResponseThread = new HandlerThread("response");
        sResponseThread.start();
        sResponseHandler = new Handler(sResponseThread.getLooper());
    }

    @Before
    public void setupTest() {
        if (!sGlobalFeatureEnabled) return;
        setFeatureEnabledForAll(true);
    }

    @AfterClass
    public static void tearDown() {
        if (!sGlobalFeatureEnabled) return;
        sResponseThread.quit();
    }

    @Test
    public void systemPackagesQueryable_notEnabled() throws Exception {
        final Resources resources = Resources.getSystem();
        assertFalse(
                "config_forceSystemPackagesQueryable must not be true.",
                resources.getBoolean(resources.getIdentifier(
                        "config_forceSystemPackagesQueryable", "bool", "android")));

        // now let's assert that that the actual set of system apps is limited
        assertThat("Not all system apps should be visible.",
                getInstalledPackages(QUERIES_NOTHING_PERM, MATCH_SYSTEM_ONLY).length,
                greaterThan(getInstalledPackages(QUERIES_NOTHING, MATCH_SYSTEM_ONLY).length));
    }

    @Test
    public void all_canSeeForceQueryable() throws Exception {
        assertVisible(QUERIES_NOTHING, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PACKAGE, TARGET_FORCEQUERYABLE);
    }

    @Test
    public void queriesNothing_cannotSeeNonForceQueryable() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_NO_API);
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesNothing_featureOff_canSeeAll() throws Exception {
        setFeatureEnabledForAll(QUERIES_NOTHING, false);
        assertVisible(QUERIES_NOTHING, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingTargetsQ_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_Q, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_Q, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_Q, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingHasPermission_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FILTERS);
    }

    @Test
    public void queriesNothing_cannotSeeFilters() throws Exception {
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                "android.appenumeration.action.ACTIVITY", this::queryIntentActivities);
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                "android.appenumeration.action.SERVICE", this::queryIntentServices);
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                "android.appenumeration.action.PROVIDER", this::queryIntentProviders);
    }

    @Test
    public void queriesActivityAction_canSeeFilters() throws Exception {
        assertQueryable(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS,
                "android.appenumeration.action.ACTIVITY", this::queryIntentActivities);
        assertQueryable(QUERIES_SERVICE_ACTION, TARGET_FILTERS,
                "android.appenumeration.action.SERVICE", this::queryIntentServices);
        assertQueryable(QUERIES_PROVIDER_AUTH, TARGET_FILTERS,
                "android.appenumeration.action.PROVIDER", this::queryIntentProviders);
    }

    @Test
    public void queriesNothingHasPermission_canSeeFilters() throws Exception {
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                "android.appenumeration.action.ACTIVITY", this::queryIntentActivities);
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                "android.appenumeration.action.SERVICE", this::queryIntentServices);
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                "android.appenumeration.action.PROVIDER", this::queryIntentProviders);
    }

    @Test
    public void queriesSomething_cannotSeeNoApi() throws Exception {
        assertNotVisible(QUERIES_ACTIVITY_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_SERVICE_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_PROVIDER_AUTH, TARGET_NO_API);
    }

    @Test
    public void queriesActivityAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesServiceAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAuthority_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FILTERS);
    }

    @Test
    public void queriesActivityAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesServiceAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_SERVICE_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAuthority_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_PROVIDER_AUTH, TARGET_FILTERS);
    }

    @Test
    public void queriesPackage_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_NO_API);
    }

    @Test
    public void whenStarted_canSeeCaller() throws Exception {
        // let's first make sure that the target cannot see the caller.
        assertNotVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
        // now let's start the target and make sure that it can see the caller as part of that call
        PackageInfo packageInfo = startForResult(QUERIES_NOTHING_PERM, QUERIES_NOTHING);
        assertThat(packageInfo, IsNull.notNullValue());
        assertThat(packageInfo.packageName, is(QUERIES_NOTHING_PERM));
        // and finally let's re-run the last check to make sure that the target can still see the
        // caller
        assertVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
    }

    @Test
    public void sharedUserMember_canSeeOtherMember() throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SHARED_USER);
    }

    @Test
    public void queriesPackage_canSeeAllSharedUserMembers() throws Exception {
        // explicitly queries target via manifest
        assertVisible(QUERIES_PACKAGE, TARGET_SHARED_USER);
        // implicitly granted visibility to other member of shared user
        assertVisible(QUERIES_PACKAGE, QUERIES_NOTHING_SHARED_USER);
    }

    private void assertVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        Assert.assertNotNull(sourcePackageName + " should be able to see " + targetPackageName,
                getPackageInfo(sourcePackageName, targetPackageName));
    }


    private void setFeatureEnabledForAll(Boolean enabled) {
        for (String pkgName : ALL_QUERIES_TARGETING_Q_PACKAGES) {
            setFeatureEnabledForAll(pkgName, enabled);
        }
        setFeatureEnabledForAll(QUERIES_NOTHING_Q, enabled == null ? null : false);
    }

    private void setFeatureEnabledForAll(String packageName, Boolean enabled) {
        SystemUtil.runShellCommand(
                "am compat " + (enabled == null ? "reset" : enabled ? "enable" : "disable")
                        + " 135549675 " + packageName);
    }

    private void assertNotVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        try {
            getPackageInfo(sourcePackageName, targetPackageName);
            fail(sourcePackageName + " should not be able to see " + targetPackageName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    interface ThrowingBiFunction<T,U,R> {
        R apply(T arg1, U arg2) throws Exception;
    }

    private void assertNotQueryable(String sourcePackageName, String targetPackageName,
            String intentAction, ThrowingBiFunction<String, Intent, String[]> commandMethod)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        Intent intent = new Intent(intentAction);
        String[] queryablePackageNames = commandMethod.apply(sourcePackageName, intent);
        for (String packageName : queryablePackageNames) {
            if (packageName.contentEquals(targetPackageName)) {
                fail(sourcePackageName + " should not be able to query " + targetPackageName +
                        " via " + intentAction);
            }
        }
    }
    private void assertQueryable(String sourcePackageName, String targetPackageName,
            String intentAction, ThrowingBiFunction<String, Intent, String[]> commandMethod)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        Intent intent = new Intent(intentAction);
        String[] queryablePackageNames = commandMethod.apply(sourcePackageName, intent);
        for (String packageName : queryablePackageNames) {
            if (packageName.contentEquals(targetPackageName)) {
                return;
            }
        }
        fail(sourcePackageName + " should be able to query " + targetPackageName + " via "
                + intentAction);
    }

    private PackageInfo getPackageInfo(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, targetPackageName,
                null /*queryIntent*/, PKG_BASE + "cts.action.GET_PACKAGE_INFO");
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }

    private PackageInfo startForResult(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, targetPackageName,
                null /*queryIntent*/, PKG_BASE + "cts.action.START_FOR_RESULT");
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] queryIntentActivities(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, null, queryIntent, PKG_BASE +
                        "cts.action.QUERY_INTENT_ACTIVITIES");
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] queryIntentServices(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, null, queryIntent, PKG_BASE +
                "cts.action.QUERY_INTENT_SERVICES");
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] queryIntentProviders(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, null, queryIntent, PKG_BASE +
                "cts.action.QUERY_INTENT_PROVIDERS");
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getInstalledPackages(String sourcePackageNames, int flags) throws Exception {
        Bundle response = sendCommand(sourcePackageNames, null, new Intent().putExtra("flags", flags),
                "android.appenumeration.cts.action.GET_INSTALLED_PACKAGES");
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private Bundle sendCommand(String sourcePackageName, @Nullable String targetPackageName,
            @Nullable Intent queryIntent, String action)
            throws Exception {
        final Intent intent = new Intent(action)
                .setComponent(new ComponentName(
                        sourcePackageName, PKG_BASE + "cts.query.TestActivity"))
                // data uri unique to each activity start to ensure actual launch and not just
                // redisplay
                .setData(Uri.parse("test://" + name.getMethodName()
                      + (targetPackageName != null
                      ? targetPackageName : UUID.randomUUID().toString())))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        if (targetPackageName != null) {
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPackageName);
        }
        if (queryIntent != null) {
            intent.putExtra(Intent.EXTRA_INTENT, queryIntent);
        }

        final ConditionVariable latch = new ConditionVariable();
        final AtomicReference<Bundle> resultReference = new AtomicReference<>();
        final RemoteCallback callback = new RemoteCallback(
                bundle -> {
                    resultReference.set(bundle);
                    latch.open();
                },
                sResponseHandler);
        intent.putExtra("remoteCallback", callback);
        InstrumentationRegistry.getInstrumentation().getContext().startActivity(intent);
        if (!latch.block(TimeUnit.SECONDS.toMillis(10))) {
            throw new TimeoutException(
                    "Latch timed out while awiating a response from " + sourcePackageName);
        }
        final Bundle bundle = resultReference.get();
        if (bundle != null && bundle.containsKey("error")) {
            throw (Exception) Objects.requireNonNull(bundle.getSerializable("error"));
        }
        return bundle;
    }

}
