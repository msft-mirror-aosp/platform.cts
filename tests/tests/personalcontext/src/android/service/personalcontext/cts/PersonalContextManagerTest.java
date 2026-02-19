/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.cts;

import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.Instrumentation;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.util.Size;
import android.widget.inline.InlinePresentationSpec;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

/** Build/Install/Run: atest CtsPersonalContextTestCases:PersonalContextManagerTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(BedsteadJUnit4.class)
public class PersonalContextManagerTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final InlinePresentationSpec INLINE_PRESENTATION_SPEC =
            new InlinePresentationSpec.Builder(new Size(100, 100), new Size(100, 100)).build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private AutoCloseable mMockCloseable;

    private PersonalContextManager mPersonalContextManager;

    @Before
    public void setUp() throws Exception {
        mMockCloseable = MockitoAnnotations.openMocks(this);

        mPersonalContextManager =
                mInstrumentation.getTargetContext().getSystemService(PersonalContextManager.class);
    }

    @After
    public void tearDown() throws Exception {
        mMockCloseable.close();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
            })
    @Test
    public void testPublishTriggeringHintWithRenderToken() {
        final List<ContextHint> hints =
                List.of(new BundleHint.Builder().build(), new BundleHint.Builder().build());

        final List<RenderToken> renderTokens = List.of(new RenderToken(UUID.randomUUID(), null));

        mPersonalContextManager.publishTriggeringHint(hints, renderTokens);
        // TODO: Check that hints are received by service.
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
            })
    @Test
    public void testPublishTriggeringHintWithAttributionHints() {
        final List<ContextHint> mainHints =
                List.of(new BundleHint.Builder().build(), new BundleHint.Builder().build());
        final List<ContextHint> attributionHints =
                List.of(new BundleHint.Builder().build(), new BundleHint.Builder().build());

        final List<RenderToken> renderTokens = List.of(new RenderToken(UUID.randomUUID(), null));

        mPersonalContextManager.publishTriggeringHint(mainHints, renderTokens, attributionHints);
        // TODO: Check that hints are received by service.
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#signHint",
            })
    @Test
    public void testSignHintWithAttributionHints() {
        final ContextHint mainHint = new BundleHint.Builder().build();
        final ContextHint attributionHint1 = new BundleHint.Builder().build();
        final ContextHint attributionHint2 = new BundleHint.Builder().build();

        final ContextHintWithSignature signedHint =
                mPersonalContextManager.signHint(
                        mainHint, List.of(attributionHint1, attributionHint2));

        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(mainHint.getHintId());
        assertThat(
                        signedHint.getAttributionHints().stream()
                                .map(chws -> chws.getContextHint().getHintId()))
                .containsExactly(attributionHint1.getHintId(), attributionHint2.getHintId());
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#isEnabled",
                "android.service.personalcontext.PersonalContextManager#setEnabled",
            })
    @EnsureHasPermission(
            value = {
                Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS,
                Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS,
            })
    @Test
    public void testIsEnabled() {
        try (PermissionContext ignored =
                TestApis.permissions()
                        .withPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)) {
            mPersonalContextManager.setEnabled(true);
            assertThat(mPersonalContextManager.isEnabled()).isTrue();
        }
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#isEnabled",
                "android.service.personalcontext.PersonalContextManager#setEnabled",
            })
    @EnsureHasPermission(
            value = {
                Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS,
                Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS,
            })
    @Test
    public void testIsDisabled() {
        try (PermissionContext ignored =
                TestApis.permissions()
                        .withPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)) {
            mPersonalContextManager.setEnabled(false);
            assertThat(mPersonalContextManager.isEnabled()).isFalse();
        }
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#setEnabled",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @EnsureDoesNotHavePermission(Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS)
    @Test
    public void testSetEnabledNoPermissions() {
        assertThrows(SecurityException.class, () -> mPersonalContextManager.setEnabled(true));
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager"
                        + "#isPersonalContextModeEnabled",
            })
    @EnsureTestAppInstalled
    @Test
    public void testPersonalContextMode_defaultValue() {
        try (TestAppInstance testApp = testApp(sDeviceState)) {
            // Default value is enabled.
            assertThat(mPersonalContextManager.isPersonalContextModeEnabled(testApp.packageName()))
                    .isEqualTo(true);
        }
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager"
                        + "#isPersonalContextModeEnabled",
                "android.service.personalcontext.PersonalContextManager"
                        + "#setPersonalContextModeEnabled",
            })
    @EnsureTestAppInstalled
    @Test
    public void testPersonalContextMode_noPermissions_fails() {
        try (TestAppInstance testApp = testApp(sDeviceState)) {
            assertThrows(
                    SecurityException.class,
                    () ->
                            mPersonalContextManager.setPersonalContextModeEnabled(
                                    testApp.packageName(), false));

            // Value has not changed since the call failed.
            assertThat(mPersonalContextManager.isPersonalContextModeEnabled(testApp.packageName()))
                    .isEqualTo(true);
        }
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager"
                        + "#isPersonalContextModeEnabled",
                "android.service.personalcontext.PersonalContextManager"
                        + "#setPersonalContextModeEnabled",
            })
    @EnsureTestAppInstalled
    @EnsureHasPermission(android.Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE)
    @Test
    public void testPersonalContextMode_setPersonalContextModeEnabled_succeeds() {
        try (TestAppInstance testApp = testApp(sDeviceState)) {
            boolean updatedValue = false;
            mPersonalContextManager.setPersonalContextModeEnabled(
                    testApp.packageName(), updatedValue);

            // Value that is set is read back.
            assertThat(mPersonalContextManager.isPersonalContextModeEnabled(testApp.packageName()))
                    .isEqualTo(updatedValue);
        }
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager"
                        + "#isPersonalContextModeEnabled",
                "android.service.personalcontext.PersonalContextManager"
                        + "#setPersonalContextModeEnabled",
            })
    @EnsureTestAppInstalled
    @EnsureHasPermission(android.Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE)
    @Test
    public void testPersonalContextMode_setPersonalContextModeEnabled_toggle() {
        try (TestAppInstance testApp = testApp(sDeviceState)) {
            mPersonalContextManager.setPersonalContextModeEnabled(testApp.packageName(), false);
            mPersonalContextManager.setPersonalContextModeEnabled(testApp.packageName(), true);

            // Value is set back to true.
            assertThat(mPersonalContextManager.isPersonalContextModeEnabled(testApp.packageName()))
                    .isEqualTo(true);
        }
    }
}
