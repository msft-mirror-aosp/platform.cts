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

package android.server.wm;

import java.util.Arrays;
import java.util.List;

/**
 * Allowlist for the legacy task cleanup behavior for ActivityManagerTestBase tests.
 *
 * <p>This class defines a list of fully qualified class names that should retain the original
 * behavior of removing all tasks except the home launcher during the {@code setUp} and
 * {@code tearDown} phases in {@code ActivityManagerTestBase}. This is a transitional measure to
 * ensure no CTS tests break during the cleanup process.
 *
 * <p>Tests listed in this allowlist will still invoke
 * {@code removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME)} in the {@code setUp}
 * and {@code tearDown} methods.
 */
// TODO(b/355452977): Remove this allowlist once all tests have been migrated to the new
//     task cleanup behavior.
public class KeepLegacyTaskCleanupAllowlist {

    // ***********************************************************************************
    // IMPORTANT: No new tests should be added to this allowlist.
    //
    // The @Deprecated annotation is intentionally used to discourage further additions,
    // facilitating the convergence of our code cleanup efforts.
    // ***********************************************************************************
    @Deprecated
    private static final List<String> ALLOWLIST = Arrays.asList(
            "android.car.cts.builtin.app.ActivityManagerHelperTest",
            "android.car.cts.builtin.app.KeyguardManagerHelperTest",
            "android.car.cts.builtin.app.TaskInfoHelperTest",
            "android.car.cts.builtin.content.ContextHelperTest",
            "android.grammaticalinflection.cts.GrammaticalInflectionManagerTest",
            "android.hardware.devicestate.cts.DeviceStateManagerTests",
            "android.localemanager.cts.LocaleManagerOverrideLocaleConfigTest",
            "android.localemanager.cts.LocaleManagerSystemLocaleTest",
            "android.localemanager.cts.LocaleManagerTests",
            "android.security.identity.cts.UserAuthTest",
            "android.server.wm.ActivitySecurityModelEmbeddingTest",
            "android.server.wm.ActivitySecurityModelTest",
            "android.server.wm.BackgroundActivityLaunchTest",
            "android.server.wm.activity.ActivityCaptureCallbackTests",
            "android.server.wm.activity.ActivityMetricsLoggerTests",
            "android.server.wm.activity.ActivityRecordInputSinkTests",
            "android.server.wm.activity.ActivityTaskAffinityTests",
            "android.server.wm.activity.ActivityVisibilityTests",
            "android.server.wm.activity.AmProfileTests",
            "android.server.wm.activity.AssistantStackTests",
            "android.server.wm.activity.ConfigChangeTests",
            "android.server.wm.activity.ConfigurationCallbacksTest",
            "android.server.wm.activity.DockConfigChangeTests",
            "android.server.wm.activity.OverrideConfigTests",
            "android.server.wm.activity.StartActivityTests",
            "android.server.wm.activity.UnsupportedErrorDialogTests",
            "android.server.wm.activity.lifecycle.ActivityLifecycleFreeformTests",
            "android.server.wm.activity.lifecycle.ActivityLifecycleKeyguardTests",
            "android.server.wm.activity.lifecycle.ActivityLifecycleLegacySplitScreenTests",
            "android.server.wm.activity.lifecycle.ActivityLifecyclePipTests",
            "android.server.wm.activity.lifecycle.ActivityLifecycleTests",
            "android.server.wm.activity.lifecycle.ActivityLifecycleTopResumedStateTests",
            "android.server.wm.activity.lifecycle.ActivityStarterTests",
            "android.server.wm.activity.lifecycle.ActivityTests",
            "android.server.wm.am.AmStartOptionsTests",
            "android.server.wm.animations.ActivityTransitionTests",
            "android.server.wm.animations.BlurTests",
            "android.server.wm.animations.DialogFrameTests",
            "android.server.wm.animations.DisplayShapeTests",
            "android.server.wm.animations.DisplaySizeTest",
            "android.server.wm.animations.KeepScreenOnTests",
            "android.server.wm.animations.LayoutTests",
            "android.server.wm.animations.ManifestLayoutTests",
            "android.server.wm.animations.MinimalPostProcessingTests",
            "android.server.wm.animations.MoveAnimationTests",
            "android.server.wm.animations.SplashscreenTests",
            "android.server.wm.animations.TransitionSelectionTests",
            "android.server.wm.backnavigation.BackGestureInvokedTest",
            "android.server.wm.backnavigation.BackNavigationLegacyGestureTest",
            "android.server.wm.backnavigation.BackNavigationLegacyTest",
            "android.server.wm.backnavigation.OnBackInvokedCallbackGestureTest",
            "android.server.wm.display.AppConfigurationTests",
            "android.server.wm.display.CompatChangeTests",
            "android.server.wm.display.DisplayAreaPolicyTests",
            "android.server.wm.display.DisplayTests",
            "android.server.wm.display.FreeformWindowingModeTests",
            "android.server.wm.display.PresentationTest",
            "android.server.wm.display.VrDisplayTests",
            "android.server.wm.display.WindowContextPolicyTests",
            "android.server.wm.display.WindowContextTests",
            "android.server.wm.ime.MultiDisplayImeTests",
            "android.server.wm.ime.MultiDisplaySecurityImeTests",
            "android.server.wm.ime.WindowInsetsAnimationImeTests",
            "android.server.wm.input.WallpaperWindowInputTests.kt",
            "android.server.wm.input.KeyguardInputTests",
            "android.server.wm.input.WindowFocusTests",
            "android.server.wm.insets.RoundedCornerTests",
            "android.server.wm.insets.WindowInsetsAnimationControllerTests",
            "android.server.wm.insets.WindowInsetsAnimationTests",
            "android.server.wm.insets.WindowInsetsControllerTests",
            "android.server.wm.insets.WindowInsetsLayoutTests",
            "android.server.wm.insets.WindowInsetsPolicyTest",
            "android.server.wm.intent.IntentGenerationTests",
            "android.server.wm.intent.IntentTests",
            "android.server.wm.jetpack.SdkAvailabilityTest",
            "android.server.wm.jetpack.SidecarTest",
            "android.server.wm.jetpack.area.ExtensionRearDisplayPresentationKeyguardTest",
            "android.server.wm.jetpack.area.ExtensionRearDisplayPresentationTest",
            "android.server.wm.jetpack.area.ExtensionRearDisplayTest",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingBoundsTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingCrossUidTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingFinishTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingFocusTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingIntegrationTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingLaunchTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingLifecycleTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingPlaceholderTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingPolicyTests",
            "android.server.wm.jetpack.embedding.ActivityEmbeddingPropertyTests",
            "android.server.wm.jetpack.embedding.ActivityStackApisTests",
            "android.server.wm.jetpack.embedding.EmbeddedActivityWindowInfoTests",
            "android.server.wm.jetpack.embedding.PinActivityStackTests",
            "android.server.wm.jetpack.embedding.SplitAttributesCalculatorTest",
            "android.server.wm.jetpack.embedding.SplitAttributesRuntimeApisTests",
            "android.server.wm.jetpack.layout.ExtensionWindowLayoutComponentTest",
            "android.server.wm.jetpack.layout.WindowLayoutComponentLetterboxTest",
            "android.server.wm.keyguard.KeyguardLockedTests",
            "android.server.wm.keyguard.KeyguardTests",
            "android.server.wm.keyguard.KeyguardTransitionTests",
            "android.server.wm.multidisplay.MultiDisplayActivityLaunchTests",
            "android.server.wm.multidisplay.MultiDisplayClientTests",
            "android.server.wm.taskfragment.SplitActivityLifecycleTest",
            "android.server.wm.taskfragment.TaskFragmentOrganizerPolicyTest",
            "android.server.wm.taskfragment.TaskFragmentOrganizerTest",
            "android.server.wm.taskfragment.TaskFragmentTrustedModeTest",
            "android.server.wm.window.HideOverlayWindowsTest",
            "android.server.wm.window.MultiWindowTests",
            "android.server.wm.window.ScreenRecordingCallbackTests",
            "android.server.wm.window.SnapshotTaskTests",
            "android.server.wm.window.ToastWindowTest",
            "android.server.wm.window.WindowMetricsActivityTests",
            "android.server.wm.window.WindowMetricsWindowContextTests",
            "android.server.wm.window.WindowPolicyTests",
            "android.service.dreams.cts.DreamOverlayTest",
            "android.service.dreams.cts.DreamServiceTest",
            "android.service.dreams.cts.SystemDreamTest",
            "android.view.inputmethod.cts.InputMethodManagerMultiDisplayTest",
            "android.view.inputmethod.cts.InputMethodPickerTest",
            "android.view.surfacecontrol.cts.SurfaceControlViewHostTests",
            "com.android.cts.localeconfig.LocaleConfigAppUpdateTest",
            "com.android.cts.localeconfig.LocaleConfigTest"
    );

    /**
     * Checks if a class should keep the legacy task cleanup behavior.
     *
     * @param clazz The class to check.
     * @return {@code true} if the class is in the allowlist, {@code false} otherwise.
     */
    public static boolean shouldKeepLegacyTaskCleanup(Class<?> clazz) {
        return ALLOWLIST.contains(clazz.getName());
    }

    private KeepLegacyTaskCleanupAllowlist() {
    }
}
