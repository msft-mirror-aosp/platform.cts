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

package android.view.inputmethod.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.TestUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Instant apps cannot query the installed IMEs")
public final class InputMethodRegistrationTest {

    private static final String LARGE_RESOURCE_IME_APK_PATH =
            "/data/local/tmp/cts/inputmethod/CtsMockLargeResourceInputMethod.apk";
    private static final String LARGE_RESOURCE_IME_PACKAGE = "com.android.cts.mocklargeresourceime";

    private static final ComponentName INITIALLY_DISABLED_IME_1 =
            ComponentName.createRelative(LARGE_RESOURCE_IME_PACKAGE,
                    ".services.a_initially_disabled_ime1");
    private static final ComponentName INITIALLY_DISABLED_IME_2 =
            ComponentName.createRelative(LARGE_RESOURCE_IME_PACKAGE,
                    ".services.a_initially_disabled_ime2");

    // IME with a subtype having long attributes. This is installable.
    private static final ComponentName IME_WITH_LONG_SUBTYPE_ATTR =
            ComponentName.createRelative(
                    LARGE_RESOURCE_IME_PACKAGE, ".services.service_with_a_subtype_of_long_attrs");

    // IME with 100 subtypes having long attributes. This is NOT installable.
    private static final ComponentName IME_WITH_MANY_LONG_ATTR_SUBTYPES =
            ComponentName.createRelative(
                    LARGE_RESOURCE_IME_PACKAGE,
                    ".services.service_with_100_subtypes_of_long_attrs");
    // IME with 700 subtypes having small attributes, This is installable.
    private static final ComponentName IME_WITH_MANY_SMALL_ATTR_SUBTYPES =
            ComponentName.createRelative(
                    LARGE_RESOURCE_IME_PACKAGE,
                    ".services.service_with_700_subtypes_of_small_attrs");

    // TODO(b/453929129): Reconsider this long timeout.
    private static final long REGISTRATION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @BeforeClass
    public static void setUpClass() {
        Log.v("InputMethodRegistrationTest", "Installing apk: " + LARGE_RESOURCE_IME_APK_PATH);
        TestUtils.installApk(LARGE_RESOURCE_IME_APK_PATH);
        runShellCommand("am wait-for-broadcast-barrier");
    }

    @AfterClass
    public static void tearDownClass() {
        Log.v("InputMethodRegistrationTest", "Uninstalling package: " + LARGE_RESOURCE_IME_PACKAGE);
        TestUtils.uninstallPackage(LARGE_RESOURCE_IME_PACKAGE);
        runShellCommandOrThrow("ime reset");
    }

    /**
     * There are two disabled IMEs in AndroidManifest.xml of com.android.cts.mocklargeresourceime
     * which have to be disabled
     */
    @Before
    public void verifyTestSetup_initiallyDisabledImes_AreFound() {
        final int flags =
                PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_DISABLED_COMPONENTS;
        final List<ResolveInfo> allPackageImes = mContext.getPackageManager().queryIntentServices(
                new Intent(InputMethod.SERVICE_INTERFACE).setPackage(LARGE_RESOURCE_IME_PACKAGE),
                PackageManager.ResolveInfoFlags.of(flags));

        // two of the manifests IME are initially disabled
        final var disabled_ime1 = allPackageImes.stream().filter(
                ri -> ri.serviceInfo.applicationInfo.className != null
                        && INITIALLY_DISABLED_IME_1.equals(
                        ComponentName.createRelative(ri.serviceInfo.applicationInfo.packageName,
                                ri.serviceInfo.applicationInfo.className))
                        && !ri.serviceInfo.enabled).findAny();
        final var disabled_ime2 = allPackageImes.stream().filter(
                ri -> ri.serviceInfo.applicationInfo.className != null
                        && INITIALLY_DISABLED_IME_2.equals(
                        ComponentName.createRelative(ri.serviceInfo.applicationInfo.packageName,
                                ri.serviceInfo.applicationInfo.className))
                        && !ri.serviceInfo.enabled).findAny();
        assertWithMessage("Test setup failed: couldn't find " + INITIALLY_DISABLED_IME_1).that(
                disabled_ime1).isNotNull();
        assertWithMessage("Test setup failed: couldn't find " + INITIALLY_DISABLED_IME_2).that(
                disabled_ime2).isNotNull();

        // IME package was successfully installed, but we need to wait until all IMEs are loaded
        final var imm = mContext.getSystemService(InputMethodManager.class);
        final ComponentName firstMockImeComponentName = ComponentName.createRelative(
                LARGE_RESOURCE_IME_PACKAGE, ".services.imeservice1");
        PollingCheck.waitFor(2000L,
                () -> imm.getInputMethodList().stream().filter(
                        imi -> imi.getComponent().equals(firstMockImeComponentName)).count() == 1,
                "IMEs were installed, but getInputMethodList() did not contain "
                        + firstMockImeComponentName);
    }

    /**
     * We only allow names of a maximum size of
     * {@link InputMethodInfo#COMPONENT_NAME_MAX_LENGTH} characters. If the name of an IME or
     * settingsActivityComponent is greater than that, it will be ignored
     */
    @Test
    public void testIgnoreLargeSettingsActivityComponent() {
        final var imm = mContext.getSystemService(InputMethodManager.class);

        runShellCommand("am wait-for-broadcast-barrier");

        List<InputMethodInfo> imis = imm.getInputMethodList();

        List<InputMethodInfo> filteredImis = imis.stream().filter(
                imi -> imi.getServiceInfo().packageName.equals(LARGE_RESOURCE_IME_PACKAGE) && (
                        imi.getServiceInfo().name.length()
                                > InputMethodInfo.COMPONENT_NAME_MAX_LENGTH
                                || imi.getSettingsActivity().length()
                                > InputMethodInfo.COMPONENT_NAME_MAX_LENGTH)).toList();
        assertWithMessage("Number of IMEs that exceed threshold").that(
                filteredImis.size()).isEqualTo(0);
    }

    /**
     * If a package contains more than {@link InputMethodInfo#MAX_IMES_PER_PACKAGE} IMEs, all IMEs
     * up to that  threshold should be loaded (if none is enabled)
     */
    @Test
    public void testLoadIMEsUpToThreshold() {
        final var imm = mContext.getSystemService(InputMethodManager.class);

        runShellCommand("am wait-for-broadcast-barrier");

        List<InputMethodInfo> imis = imm.getInputMethodList();

        List<InputMethodInfo> filteredImis = imis.stream().filter(
                imi -> imi.getServiceInfo().packageName.equals(
                        LARGE_RESOURCE_IME_PACKAGE)).toList();

        assertWithMessage("Number of loaded IMEs (overall %s, filtered %s)",
                imis.stream().map(InputMethodInfo::getId).toList(),
                filteredImis.stream().map(InputMethodInfo::getId).toList()).that(
                filteredImis.size()).isAtLeast(InputMethodInfo.MAX_IMES_PER_PACKAGE);
    }

    /**
     * Ensure that all enabled + {@link InputMethodInfo#MAX_IMES_PER_PACKAGE} are loaded:
     * 1. load all enabled IMEs
     * 2. enable some of them,
     * 3. set enabled status = true of IMEs in the manifest (located before the currently enabled
     * ones),
     * 4. load IMEs again, check that total amount is greater
     */
    @Test
    public void testLoadEnabledIMEsAndMore() throws InterruptedException {
        final var imm = mContext.getSystemService(InputMethodManager.class);
        final List<ComponentName> componentNamesToEnable = List.of(
                INITIALLY_DISABLED_IME_1, INITIALLY_DISABLED_IME_2);
        try {
            // Wait until ime 19 and 20 are loaded
            final ComponentName enable_ime19 = ComponentName.createRelative(
                    LARGE_RESOURCE_IME_PACKAGE, ".services.imeservice19");
            final ComponentName enable_ime20 = ComponentName.createRelative(
                    LARGE_RESOURCE_IME_PACKAGE, ".services.imeservice20");

            // Wait until 2 initially disabled IMEs are loaded
            PollingCheck.waitFor(
                    REGISTRATION_TIMEOUT_MS,
                    () -> {
                        final List<ComponentName> imiIds =
                                imm.getInputMethodList().stream()
                                        .map(InputMethodInfo::getComponent)
                                        .toList();
                        return imiIds.containsAll(List.of(enable_ime19, enable_ime20));
                    },
                    "Enabled IMIs were not loaded before timeout");

            enableImes(enable_ime19.flattenToShortString(), enable_ime20.flattenToShortString());
            setComponentEnabledSync(componentNamesToEnable,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

            runShellCommand("am wait-for-broadcast-barrier");
            // enable last two IMEs and make two IMEs at the beginning available
            PollingCheck.waitFor(
                    REGISTRATION_TIMEOUT_MS,
                    () -> {
                        final List<ComponentName> imiIds =
                                imm.getInputMethodList().stream()
                                        .map(InputMethodInfo::getComponent)
                                        .toList();
                        return imiIds.containsAll(
                                List.of(INITIALLY_DISABLED_IME_1, INITIALLY_DISABLED_IME_2));
                    },
                    "Initially disabled IMIs were not loaded before timeout");

            // load all IMEs: the number of enabled IMEs should be more than MAX_IMES_PER_PACKAGE.
            List<InputMethodInfo> imis = imm.getInputMethodList();
            List<InputMethodInfo> filteredImis = imis.stream().filter(
                    imi -> imi.getServiceInfo().packageName.equals(
                            LARGE_RESOURCE_IME_PACKAGE)).toList();
            assertWithMessage("Expected enabled IMEs to still be available, but they weren't.")
                    .that(filteredImis.stream().map(InputMethodInfo::getComponent).toList())
                    .containsAtLeast(enable_ime19, enable_ime20);
            assertWithMessage("Number of loaded IMEs (overall %s, filtered %s)",
                    imis.stream().map(InputMethodInfo::getId).toList(),
                    filteredImis.stream().map(InputMethodInfo::getId).toList()).that(
                    filteredImis.size()).isAtLeast(2 + InputMethodInfo.MAX_IMES_PER_PACKAGE);

        } finally {
            // disable again, for other tests
            setComponentEnabledSync(componentNamesToEnable,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

            runShellCommand("am wait-for-broadcast-barrier");
        }
    }

    /** We ignore IMEs that have too long string attribute or too many subtypes. */
    @Test
    public void testIgnoreInvalidSubtypeImes() {
        // These large services are disabled by default.
        // Enabling components are done one-by-one. The valid services are put the last so that the
        // polling check finishes after enabling all.
        final List<ComponentName> componentNamesToEnable =
                List.of(
                        IME_WITH_MANY_LONG_ATTR_SUBTYPES,
                        IME_WITH_MANY_SMALL_ATTR_SUBTYPES, // Valid IME
                        IME_WITH_LONG_SUBTYPE_ATTR // Valid IME
                        );
        final var imm = mContext.getSystemService(InputMethodManager.class);
        try {
            setComponentEnabledSync(
                    componentNamesToEnable, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

            runShellCommand("am wait-for-broadcast-barrier");

            // Wait until the valid IMEs are loaded.
            final Set<ComponentName> componentNames = new HashSet<>();
            PollingCheck.waitFor(
                    REGISTRATION_TIMEOUT_MS,
                    () -> {
                        componentNames.clear();
                        componentNames.addAll(
                                imm.getInputMethodList().stream()
                                        .map(InputMethodInfo::getComponent)
                                        .toList());
                        return componentNames.contains(IME_WITH_MANY_SMALL_ATTR_SUBTYPES)
                                && componentNames.contains(IME_WITH_LONG_SUBTYPE_ATTR);
                    },
                    "Valid IMEs (IME_WITH_LONG_SUBTYPE_ATTR and "
                            + "IME_WITH_MANY_SMALL_ATTR_SUBTYPES) must be loaded");

            assertWithMessage("Invalid IMEs must be filtered out")
                    .that(componentNames)
                    .doesNotContain(IME_WITH_MANY_LONG_ATTR_SUBTYPES);
        } finally {
            // disable again, for other tests
            setComponentEnabledSync(
                    componentNamesToEnable, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

            runShellCommand("am wait-for-broadcast-barrier");
        }
    }

    private void setComponentEnabledSync(List<ComponentName> componentNames, int enabledState) {
        for (ComponentName componentName : componentNames) {
            runWithShellPermissionIdentity(
                    () -> mContext.getPackageManager().setComponentEnabledSetting(componentName,
                            enabledState, 0));
        }
    }

    private void enableImes(String... ids) {
        for (String id : ids) {
            runShellCommandOrThrow("ime enable --user " + UserHandle.myUserId() + " " + id);
        }
    }
}
