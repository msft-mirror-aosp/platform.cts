/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.server.wm.app

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for test app components. */
object Components : ComponentsProvider() {
    @JvmField val ALT_LAUNCHING_ACTIVITY = component("AltLaunchingActivity")
    @JvmField val TRANSLUCENT_PIP_ACTIVITY = component("TranslucentPipActivity")
    @JvmField val ANIMATION_TEST_ACTIVITY = component("AnimationTestActivity")
    @JvmField val ASSISTANT_ACTIVITY = component("AssistantActivity")
    @JvmField val BOTTOM_ACTIVITY = component("BottomActivity")
    @JvmField val BOTTOM_NON_RESIZABLE_ACTIVITY = component("BottomNonResizableActivity")
    @JvmField val BOTTOM_LEFT_LAYOUT_ACTIVITY = component("BottomLeftLayoutActivity")
    @JvmField val BOTTOM_RIGHT_LAYOUT_ACTIVITY = component("BottomRightLayoutActivity")
    @JvmField val BROADCAST_RECEIVER_ACTIVITY = component("BroadcastReceiverActivity")
    @JvmField val DIALOG_WHEN_LARGE_ACTIVITY = component("DialogWhenLargeActivity")
    @JvmField val DISMISS_KEYGUARD_ACTIVITY = component("DismissKeyguardActivity")
    @JvmField val DISMISS_KEYGUARD_METHOD_ACTIVITY = component("DismissKeyguardMethodActivity")
    @JvmField val DOCKED_ACTIVITY = component("DockedActivity")

    /** This activity is an alias activity pointing [TrampolineActivity] in AndroidManifest.xml */
    @JvmField val ENTRY_POINT_ALIAS_ACTIVITY = component("EntryPointAliasActivity")
    @JvmField val FONT_SCALE_ACTIVITY = component("FontScaleActivity")
    @JvmField val FONT_SCALE_NO_RELAUNCH_ACTIVITY = component("FontScaleNoRelaunchActivity")
    @JvmField val FREEFORM_ACTIVITY = component("FreeformActivity")
    @JvmField val HOST_ACTIVITY = component("HostActivity")
    @JvmField val HIDE_OVERLAY_WINDOWS_ACTIVITY = component("HideOverlayWindowsActivity")
    @JvmField val KEYGUARD_LOCK_ACTIVITY = component("KeyguardLockActivity")
    @JvmField val LANDSCAPE_ORIENTATION_ACTIVITY = component("LandscapeOrientationActivity")
    @JvmField
    val LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION = component("LaunchAssistantActivityFromSession")
    @JvmField
    val LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK =
        component("LaunchAssistantActivityIntoAssistantStack")
    @JvmField val LAUNCH_ENTER_PIP_ACTIVITY = component("LaunchEnterPipActivity")
    @JvmField val LAUNCH_PIP_ON_PIP_ACTIVITY = component("LaunchPipOnPipActivity")
    @JvmField val LAUNCHING_ACTIVITY = component("LaunchingActivity")
    @JvmField val LOG_CONFIGURATION_ACTIVITY = component("LogConfigurationActivity")
    @JvmField val MOVE_TASK_TO_BACK_ACTIVITY = component("MoveTaskToBackActivity")
    @JvmField val MULTI_WINDOW_FULLSCREEN_ACTIVITY = component("MultiWindowFullscreenActivity")
    @JvmField val NIGHT_MODE_ACTIVITY = component("NightModeActivity")
    @JvmField val NO_DISPLAY_ACTIVITY = component("NoDisplayActivity")
    @JvmField val NO_HISTORY_ACTIVITY = component("NoHistoryActivity")
    @JvmField val NO_HISTORY_ACTIVITY2 = component("NoHistoryActivity2")
    @JvmField val NO_RELAUNCH_ACTIVITY = component("NoRelaunchActivity")
    @JvmField val NON_RESIZEABLE_ACTIVITY = component("NonResizeableActivity")
    @JvmField val PRESENTATION_ACTIVITY = component("PresentationActivity")
    @JvmField val PIP_ACTIVITY = component("PipActivity")
    @JvmField val PIP_ACTIVITY2 = component("PipActivity2")
    @JvmField val PIP_ACTIVITY_WITH_MINIMAL_SIZE = component("PipActivityWithMinimalSize")
    @JvmField val PIP_ACTIVITY_WITH_TINY_MINIMAL_SIZE = component("PipActivityWithTinyMinimalSize")
    @JvmField val PIP_ACTIVITY_WITH_SAME_AFFINITY = component("PipActivityWithSameAffinity")
    @JvmField val PIP_ON_STOP_ACTIVITY = component("PipOnStopActivity")
    @JvmField val LAUNCH_INTO_PIP_HOST_ACTIVITY = component("LaunchIntoPipHostActivity")
    @JvmField val LAUNCH_INTO_PIP_CONTAINER_ACTIVITY = component("LaunchIntoPipContainerActivity")
    @JvmField val PORTRAIT_ORIENTATION_ACTIVITY = component("PortraitOrientationActivity")
    @JvmField val RECURSIVE_ACTIVITY = component("RecursiveActivity")
    @JvmField val REPORT_FULLY_DRAWN_ACTIVITY = component("ReportFullyDrawnActivity")
    @JvmField val RESIZEABLE_ACTIVITY = component("ResizeableActivity")
    @JvmField val RESUME_WHILE_PAUSING_ACTIVITY = component("ResumeWhilePausingActivity")
    @JvmField val SHOW_WHEN_LOCKED_ACTIVITY = component("ShowWhenLockedActivity")
    @JvmField val SHOW_WHEN_LOCKED_ATTR_IME_ACTIVITY = component("ShowWhenLockedAttrImeActivity")
    @JvmField val SHOW_WHEN_LOCKED_ATTR_ACTIVITY = component("ShowWhenLockedAttrActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY = component("ShowWhenLockedAttrRotationActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_ACTIVITY =
        component("ShowWhenLockedAttrRemoveAttrActivity")
    @JvmField
    val INHERIT_SHOW_WHEN_LOCKED_ADD_ACTIVITY = component("InheritShowWhenLockedAddActivity")
    @JvmField
    val INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY = component("InheritShowWhenLockedAttrActivity")
    @JvmField
    val INHERIT_SHOW_WHEN_LOCKED_REMOVE_ACTIVITY = component("InheritShowWhenLockedRemoveActivity")
    @JvmField
    val NO_INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY = component("NoInheritShowWhenLockedAttrActivity")
    @JvmField val SHOW_WHEN_LOCKED_DIALOG_ACTIVITY = component("ShowWhenLockedDialogActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY = component("ShowWhenLockedTranslucentActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY = component("ShowWhenLockedWithDialogActivity")
    @JvmField val SINGLE_INSTANCE_ACTIVITY = component("SingleInstanceActivity")
    @JvmField val HOME_ACTIVITY = component("HomeActivity")
    @JvmField val SECONDARY_HOME_ACTIVITY = component("SecondaryHomeActivity")
    @JvmField val UI_SCALING_TEST_ACTIVITY = component("UiScalingTestActivity")
    @JvmField val SINGLE_HOME_ACTIVITY = component("SingleHomeActivity")
    @JvmField val SINGLE_SECONDARY_HOME_ACTIVITY = component("SingleSecondaryHomeActivity")
    @JvmField val SINGLE_TASK_ACTIVITY = component("SingleTaskActivity")
    @JvmField val SINGLE_TOP_ACTIVITY = component("SingleTopActivity")
    @JvmField val SPLASHSCREEN_ACTIVITY = component("SplashscreenActivity")
    @JvmField val DISABLE_PREVIEW_ACTIVITY = component("DisablePreviewActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY = component("ShowWhenLockedNoPreviewActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY = component("ShowWhenLockedAttrNoPreviewActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY =
        component("ShowWhenLockedAttrRemoveAttrNoPreviewActivity")
    @JvmField
    val SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY =
        component("ShowWhenLockedWithDialogNoPreviewActivity")

    @JvmField val TEST_ACTIVITY = component("TestActivity")
    @JvmField val TOP_ACTIVITY = component("TopActivity")
    @JvmField val TOP_NON_RESIZABLE_ACTIVITY = component("TopNonResizableActivity")
    @JvmField
    val TOP_NON_RESIZABLE_WALLPAPER_ACTIVITY = component("TopNonResizableWallpaperActivity")
    @JvmField val TOP_WALLPAPER_ACTIVITY = component("TopWallpaperActivity")
    @JvmField val TEST_ACTIVITY_WITH_SAME_AFFINITY = component("TestActivityWithSameAffinity")
    @JvmField val TEST_LIVE_WALLPAPER_SERVICE = component("LiveWallpaper")
    @JvmField val TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE = component("InteractiveLiveWallpaper")
    @JvmField val TOP_LEFT_LAYOUT_ACTIVITY = component("TopLeftLayoutActivity")
    @JvmField val TOP_RIGHT_LAYOUT_ACTIVITY = component("TopRightLayoutActivity")
    @JvmField val TRANSLUCENT_ACTIVITY = component("TranslucentActivity")
    @JvmField val TRANSLUCENT_ASSISTANT_ACTIVITY = component("TranslucentAssistantActivity")
    @JvmField val TRANSLUCENT_TOP_ACTIVITY = component("TranslucentTopActivity")
    @JvmField val TRANSLUCENT_TOP_WALLPAPER_ACTIVITY = component("TranslucentTopWallpaperActivity")
    @JvmField
    val TRANSLUCENT_TOP_NON_RESIZABLE_ACTIVITY = component("TranslucentTopNonResizableActivity")
    @JvmField val TRANSLUCENT_TEST_ACTIVITY = component("TranslucentTestActivity")
    @JvmField val TRANSLUCENT_LANDSCAPE_ACTIVITY = component("TranslucentLandscapeActivity")
    @JvmField val TURN_SCREEN_ON_ACTIVITY = component("TurnScreenOnActivity")
    @JvmField val TURN_SCREEN_ON_ATTR_ACTIVITY = component("TurnScreenOnAttrActivity")
    @JvmField
    val TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY =
        component("TurnScreenOnAttrDismissKeyguardActivity")
    @JvmField
    val TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY = component("TurnScreenOnAttrRemoveAttrActivity")
    @JvmField
    val TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY = component("TurnScreenOnDismissKeyguardActivity")
    @JvmField val TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY = component("TurnScreenOnShowOnLockActivity")
    @JvmField val TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY = component("TurnScreenOnSingleTaskActivity")
    @JvmField
    val TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY = component("TurnScreenOnWithRelayoutActivity")
    @JvmField val UNRESPONSIVE_ACTIVITY = component("UnresponsiveActivity")
    @JvmField val VIRTUAL_DISPLAY_ACTIVITY = component("VirtualDisplayActivity")
    @JvmField val VR_TEST_ACTIVITY = component("VrTestActivity")
    @JvmField val WALLPAPAER_ACTIVITY = component("WallpaperActivity")
    @JvmField val LAUNCH_TEST_ON_DESTROY_ACTIVITY = component("LaunchTestOnDestroyActivity")
    @JvmField val ALIAS_TEST_ACTIVITY = component("AliasTestActivity")
    @JvmField
    val DISPLAY_ACCESS_CHECK_EMBEDDING_ACTIVITY = component("DisplayAccessCheckEmbeddingActivity")
    @JvmField val MAX_ASPECT_RATIO_ACTIVITY = component("MaxAspectRatioActivity")
    @JvmField val MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY = component("MaxAspectRatioResizableActivity")
    @JvmField val MAX_ASPECT_RATIO_UNSET_ACTIVITY = component("MaxAspectRatioUnsetActivity")
    @JvmField val META_DATA_MAX_ASPECT_RATIO_ACTIVITY = component("MetaDataMaxAspectRatioActivity")
    @JvmField val MIN_ASPECT_RATIO_ACTIVITY = component("MinAspectRatioActivity")
    @JvmField val MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY = component("MinAspectRatioLandscapeActivity")
    @JvmField val MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY = component("MinAspectRatioPortraitActivity")
    @JvmField val MIN_ASPECT_RATIO_UNSET_ACTIVITY = component("MinAspectRatioUnsetActivity")

    @JvmField
    val ASSISTANT_VOICE_INTERACTION_SERVICE = component("AssistantVoiceInteractionService")

    @JvmField val LAUNCH_BROADCAST_RECEIVER = component("LaunchBroadcastReceiver")

    @JvmField val CLICKABLE_TOAST_ACTIVITY = component("ClickableToastActivity")

    @JvmField val WALLPAPER_TARGET_ACTIVITY = component("WallpaperTargetActivity")

    object LaunchBroadcastReceiver {
        @JvmField val LAUNCH_BROADCAST_ACTION = "$packageName.LAUNCH_BROADCAST_ACTION"
        @JvmField val ACTION_TEST_ACTIVITY_START = "$packageName.ACTION_TEST_ACTIVITY_START"
        const val EXTRA_COMPONENT_NAME = "component_name"
        const val EXTRA_TARGET_DISPLAY = "target_display"
    }

    @JvmField
    val SINGLE_TASK_INSTANCE_DISPLAY_ACTIVITY = component("SingleTaskInstanceDisplayActivity")
    @JvmField
    val SINGLE_TASK_INSTANCE_DISPLAY_ACTIVITY2 = component("SingleTaskInstanceDisplayActivity2")
    @JvmField
    val SINGLE_TASK_INSTANCE_DISPLAY_ACTIVITY3 = component("SingleTaskInstanceDisplayActivity3")

    @JvmField val MPP_ACTIVITY = component("MinimalPostProcessingActivity")

    @JvmField val MPP_ACTIVITY2 = component("MinimalPostProcessingActivity2")

    @JvmField val MPP_ACTIVITY3 = component("MinimalPostProcessingManifestActivity")

    @JvmField val POPUP_MPP_ACTIVITY = component("PopupMinimalPostProcessingActivity")

    @JvmField val CRASHING_ACTIVITY = component("CrashingActivity")

    @JvmField val HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY = component("HandleSplashScreenExitActivity")
    @JvmField val SPLASH_SCREEN_REPLACE_ICON_ACTIVITY = component("SplashScreenReplaceIconActivity")
    @JvmField
    val SPLASH_SCREEN_REPLACE_THEME_ACTIVITY = component("SplashScreenReplaceThemeActivity")
    @JvmField val SPLASH_SCREEN_STYLE_THEME_ACTIVITY = component("SplashScreenStyleThemeActivity")

    @JvmField val TEST_DREAM_SERVICE = component("TestDream")

    @JvmField val TEST_STUBBORN_DREAM_SERVICE = component("TestStubbornDream")

    @JvmField val OVERLAY_TEST_SERVICE = component("OverlayTestService")

    @JvmField val KEEP_CLEAR_RECTS_ACTIVITY = component("KeepClearRectsActivity")

    @JvmField val KEEP_CLEAR_RECTS_ACTIVITY2 = component("KeepClearRectsActivity2")

    /** The keys are used for [TestJournalProvider] when testing starting window. */
    object TestStartingWindowKeys {
        const val HANDLE_SPLASH_SCREEN_EXIT = "HandleSplashScreenExitActivity"
        const val REPLACE_ICON_EXIT = "SplashScreenReplaceIconActivity"
        const val RECEIVE_SPLASH_SCREEN_EXIT = "receive_splash_screen_exit"
        const val CONTAINS_CENTER_VIEW = "contains_center_view"
        const val CONTAINS_BRANDING_VIEW = "contains_branding_view"
        const val ICON_BACKGROUND_COLOR = "icon_background_color"
        const val ICON_ANIMATION_DURATION = "icon_animation_duration"
        const val ICON_ANIMATION_START = "icon_animation_start"
        const val CENTER_VIEW_IS_SURFACE_VIEW = "center_view_is_surface_view"

        const val REQUEST_HANDLE_EXIT_ON_CREATE = "handle_exit_onCreate"
        const val REQUEST_HANDLE_EXIT_ON_RESUME = "handle_exit_onResume"
        const val CANCEL_HANDLE_EXIT = "cancel_handle_exit"

        const val REQUEST_SET_NIGHT_MODE_ON_CREATE = "night_mode_onCreate"
        const val GET_NIGHT_MODE_ACTIVITY_CHANGED = "get_night_mode_activity"
        const val DELAY_RESUME = "delay_resume"
        const val OVERRIDE_THEME_ENABLED = "override_theme_enabled"
        const val OVERRIDE_THEME_COLOR = "override_theme_color"
        const val OVERRIDE_THEME_COMPONENT = "override_theme_component"
        const val STYLE_THEME_COMPONENT = "style_theme_component"
    }

    /** The keys are used for [TestJournalProvider] when testing wallpaper component. */
    object TestLiveWallpaperKeys {
        const val COMPONENT = "LiveWallpaper"
        const val ENGINE_CREATED = "engine_created"
        const val ENGINE_DISPLAY_ID = "engine_display_Id"
    }

    /** The keys are used for [TestJournalProvider] when testing interactive wallpaper component. */
    object TestInteractiveLiveWallpaperKeys {
        const val COMPONENT = "InteractiveLiveWallpaper"
        const val LAST_RECEIVED_MOTION_EVENT = "LastReceivedMotionEvent"
    }

    /**
     * Action and extra key constants for [TEST_ACTIVITY].
     *
     * TODO(b/73346885): These constants should be in [android.server.wm.app.TestActivity] once the
     *   activity is moved to test APK.
     */
    object TestActivity {
        // Finishes the activity
        @JvmField val TEST_ACTIVITY_ACTION_FINISH_SELF = "$packageName.TestActivity.finish_self"

        // Sets the fixed orientation (can be one of [ActivityInfo.ScreenOrientation]
        const val EXTRA_FIXED_ORIENTATION = "fixed_orientation"
        const val EXTRA_CONFIGURATION = "configuration"
        const val EXTRA_CONFIG_ASSETS_SEQ = "config_assets_seq"
        const val EXTRA_INTENT = "intent"
        const val EXTRA_INTENTS = "intents"
        const val EXTRA_NO_IDLE = "no_idle"
        const val COMMAND_NAVIGATE_UP_TO = "navigate_up_to"
        const val COMMAND_START_ACTIVITY = "start_activity"
        const val COMMAND_START_ACTIVITIES = "start_activities"
        const val EXTRA_OPTION = "option"
    }

    /**
     * Extra key constants for [LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK] and
     * [LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION].
     *
     * TODO(b/73346885): These constants should be in [android.server.wm.app.AssistantActivity] once
     *   the activity is moved to test APK.
     */
    object AssistantActivity {
        // Launches the given activity in onResume
        const val EXTRA_ASSISTANT_LAUNCH_NEW_TASK = "launch_new_task"

        // Finishes this activity in onResume, this happens after EXTRA_ASSISTANT_LAUNCH_NEW_TASK
        const val EXTRA_ASSISTANT_FINISH_SELF = "finish_self"

        // Attempts to enter picture-in-picture in onResume
        const val EXTRA_ASSISTANT_ENTER_PIP = "enter_pip"

        // Display on which Assistant runs
        const val EXTRA_ASSISTANT_DISPLAY_ID = "assistant_display_id"
    }

    /**
     * Extra key constants for [android.server.wm.app.BottomActivity].
     *
     * TODO(b/73346885): These constants should be in [android.server.wm.app.BottomActivity] once
     *   the activity is moved to test APK.
     */
    object BottomActivity {
        const val EXTRA_BOTTOM_WALLPAPER = "USE_WALLPAPER"
        const val EXTRA_STOP_DELAY = "STOP_DELAY"
    }

    /**
     * Extra key constants for [android.server.wm.app.BroadcastReceiverActivity].
     *
     * TODO(b/73346885): These constants should be in
     *   [android.server.wm.app.BroadcastReceiverActivity] once the activity is moved to test APK.
     */
    object BroadcastReceiverActivity {
        const val ACTION_TRIGGER_BROADCAST = "trigger_broadcast"
        const val EXTRA_DISMISS_KEYGUARD = "dismissKeyguard"
        const val EXTRA_DISMISS_KEYGUARD_METHOD = "dismissKeyguardMethod"
        const val EXTRA_FINISH_BROADCAST = "finish"
        const val EXTRA_MOVE_BROADCAST_TO_BACK = "moveToBack"
        const val EXTRA_BROADCAST_ORIENTATION = "orientation"
        const val EXTRA_CUTOUT_EXISTS = "cutoutExists"
    }

    /** Extra key constants for [android.server.wm.app.LandscapeOrientationActivity]. */
    object LandscapeOrientationActivity {
        const val EXTRA_APP_CONFIG_INFO = "app_config_info"
        const val EXTRA_CONFIG_INFO_IN_ON_CREATE = "config_info_in_on_create"
        const val EXTRA_DISPLAY_REAL_SIZE = "display_real_size"
        const val EXTRA_SYSTEM_RESOURCES_CONFIG_INFO = "sys_config_info"
    }

    /** Extra key constants for [android.server.wm.app.FontScaleActivity]. */
    object FontScaleActivity {
        const val EXTRA_FONT_PIXEL_SIZE = "fontPixelSize"
        const val EXTRA_FONT_ACTIVITY_DPI = "fontActivityDpi"
    }

    /** Extra key constants for [android.server.wm.app.NoHistoryActivity]. */
    object NoHistoryActivity {
        const val EXTRA_SHOW_WHEN_LOCKED = "showWhenLocked"
    }

    /** Extra key constants for [android.server.wm.app.TurnScreenOnActivity]. */
    object TurnScreenOnActivity {
        // Turn on screen by window flags or APIs.
        const val EXTRA_USE_WINDOW_FLAGS = "useWindowFlags"
        const val EXTRA_SHOW_WHEN_LOCKED = "useShowWhenLocked"
        const val EXTRA_SLEEP_MS_IN_ON_CREATE = "sleepMsInOnCreate"
    }

    /** Extra key constants for [android.server.wm.app.MinimalPostProcessingActivity]. */
    object MinimalPostProcessingActivity {
        // Turn on minimal post processing (if available).
        const val EXTRA_PREFER_MPP = "preferMinimalPostProcessing"
    }

    /**
     * Logging constants for [android.server.wm.app.KeyguardDismissLoggerCallback].
     *
     * TODO(b/73346885): These constants should be in
     *   [android.server.wm.app.KeyguardDismissLoggerCallback] once the class is moved to test APK.
     */
    object KeyguardDismissLoggerCallback {
        const val KEYGUARD_DISMISS_LOG_TAG = "KeyguardDismissLoggerCallback"
        const val ENTRY_ON_DISMISS_CANCELLED = "onDismissCancelled"
        const val ENTRY_ON_DISMISS_ERROR = "onDismissError"
        const val ENTRY_ON_DISMISS_SUCCEEDED = "onDismissSucceeded"
    }

    /**
     * Extra key constants for [LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK].
     *
     * TODO(b/73346885): These constants should be in
     *   [android.server.wm.app.LaunchAssistantActivityIntoAssistantStack] once the activity is
     *   moved to test APK.
     */
    object LaunchAssistantActivityIntoAssistantStack {
        // Launches the translucent assist activity
        const val EXTRA_ASSISTANT_IS_TRANSLUCENT = "is_translucent"
    }

    /**
     * Extra constants for [android.server.wm.app.MoveTaskToBackActivity].
     *
     * TODO(b/73346885): These constants should be in [android.server.wm.app.MoveTaskToBackActivity]
     *   once the activity is moved to test APK.
     */
    object MoveTaskToBackActivity {
        const val EXTRA_FINISH_POINT = "finish_point"
        const val FINISH_POINT_ON_PAUSE = "on_pause"
        const val FINISH_POINT_ON_STOP = "on_stop"
    }

    /**
     * Action and extra key constants for [android.server.wm.app.PipActivity].
     *
     * TODO(b/73346885): These constants should be in [android.server.wm.app.PipActivity] once the
     *   activity is moved to test APK.
     */
    object PipActivity {
        // Intent action that this activity dynamically registers to enter picture-in-picture
        const val ACTION_ENTER_PIP = "android.server.wm.app.PipActivity.enter_pip"

        // Intent action that this activity dynamically registers to move itself to the back
        const val ACTION_MOVE_TO_BACK = "android.server.wm.app.PipActivity.move_to_back"

        // Intent action that this activity dynamically registers to expand itself.
        // If EXTRA_SET_ASPECT_RATIO_WITH_DELAY is set, it will also attempt to apply the aspect
        // ratio after a short delay.
        const val ACTION_EXPAND_PIP = "android.server.wm.app.PipActivity.expand_pip"

        // Intent action that this activity dynamically registers to receive update callback.
        // If EXTRA_SET_PIP_STASH is set, then onPictureInPictureStateChanged will be called
        // with the value.
        const val ACTION_UPDATE_PIP_STATE = "android.server.wm.app.PipActivity.update_pip_state"

        // Intent action that this activity dynamically registers to enter picture-in-picture
        // then wait for the onPictureInPictureUiStateChanged callback.
        const val ACTION_ENTER_PIP_AND_WAIT_FOR_UI_STATE =
            "android.server.wm.app.PipActivity.enter_pip_and_wait_for_ui_state"

        // Intent action that this activity dynamically registers to set requested orientation.
        // Will apply the orientation to the value set in the EXTRA_FIXED_ORIENTATION extra.
        const val ACTION_SET_REQUESTED_ORIENTATION =
            "android.server.wm.app.PipActivity.set_requested_orientation"

        // Intent action that will finish this activity
        const val ACTION_FINISH = "android.server.wm.app.PipActivity.finish"

        // Intent action that will request that the activity enters picture-in-picture.
        const val ACTION_ON_PIP_REQUESTED = "android.server.wm.app.PipActivity.on_pip_requested"

        // Intent action that sets a RemoteCallback in PipActivity that will receive the
        // isInPictureInPictureMode result
        const val ACTION_SET_ON_PAUSE_REMOTE_CALLBACK =
            "android.server.wm.app.PipActivity.set_on_pause_remote_callback"

        // Intent action that will request that the activity initiates launch-into-pip
        const val ACTION_START_LAUNCH_INTO_PIP_CONTAINER =
            "android.server.wm.app.LaunchIntoPip.start_container_activity"

        // Intent action that will request the host activity of launch-into-pip to finish itself
        const val ACTION_FINISH_LAUNCH_INTO_PIP_HOST =
            "android.server.wm.app.LaunchIntoPip.finish_host_activity"

        // Intent action that will request the activity to change the PiP aspect ratio
        const val ACTION_CHANGE_ASPECT_RATIO =
            "android.server.wm.app.LaunchIntoPip.change_aspect_ratio"

        // Intent action that will request the activity to start a new translucent activity
        const val ACTION_LAUNCH_TRANSLUCENT_ACTIVITY =
            "android.server.wm.app.LaunchIntoPip.launch_translucent_activity"

        // Adds an assertion that we do not ever get onStop() before we enter picture in picture
        const val EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP = "assert_no_on_stop_before_pip"

        // Calls enterPictureInPicture() on creation
        const val EXTRA_ENTER_PIP = "enter_pip"

        // Used with EXTRA_AUTO_ENTER_PIP, value specifies the aspect ratio to enter PIP with
        const val EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR = "enter_pip_aspect_ratio_numerator"

        // Used with EXTRA_AUTO_ENTER_PIP, value specifies the aspect ratio to enter PIP with
        const val EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR = "enter_pip_aspect_ratio_denominator"

        // Calls requestAutoEnterPictureInPicture() with the value provided
        const val EXTRA_ENTER_PIP_ON_PAUSE = "enter_pip_on_pause"

        // Calls requestAutoEnterPictureInPicture() with the value provided
        const val EXTRA_ENTER_PIP_ON_USER_LEAVE_HINT = "enter_pip_on_user_leave_hint"

        // Calls requestAutoEnterPictureInPicture() with the value provided
        const val EXTRA_ENTER_PIP_ON_PIP_REQUESTED = "enter_pip_on_pip_requested"

        // Calls enterPictureInPictureMode when activity receives onBackPressed
        const val EXTRA_ENTER_PIP_ON_BACK_PRESSED = "enter_pip_on_back_pressed"
        const val EXTRA_EXPANDED_PIP_ASPECT_RATIO_NUMERATOR = "expanded_pip_numerator"
        const val EXTRA_EXPANDED_PIP_ASPECT_RATIO_DENOMINATOR = "expanded_pip_denomenator"

        // Sets auto PIP allowed on the activity picture-in-picture params.
        const val EXTRA_ALLOW_AUTO_PIP = "enter_pip_auto_pip_allowed"

        // Sets seamless resize enabled on the activity picture-in-picture params.
        const val EXTRA_IS_SEAMLESS_RESIZE_ENABLED = "enter_pip_is_seamless_resize_enabled"

        // Sets the given title on the activity picture-in-picture params.
        const val EXTRA_TITLE = "set_pip_title"

        // Sets the given subtitle on the activity picture-in-picture params.
        const val EXTRA_SUBTITLE = "set_pip_subtitle"

        // Finishes the activity at the end of onResume (after EXTRA_START_ACTIVITY is handled)
        const val EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume"

        // Similar to EXTRA_FINISH_SELF_ON_RESUME but only be used to finish the trampoline
        // Activity when it receives onResume (after the target Activity is launched).
        const val EXTRA_FINISH_TRAMPOLINE_ON_RESUME = "finish_trampoline_on_resume"

        // Sets the fixed orientation (can be one of [ActivityInfo.ScreenOrientation]
        const val EXTRA_PIP_ORIENTATION = "fixed_orientation"

        // The amount to delay to artificially introduce in onPause()
        // (before EXTRA_ENTER_PIP_ON_PAUSE is processed)
        const val EXTRA_ON_PAUSE_DELAY = "on_pause_delay"

        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value
        const val EXTRA_SET_ASPECT_RATIO_DENOMINATOR = "set_aspect_ratio_denominator"

        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value
        const val EXTRA_SET_ASPECT_RATIO_NUMERATOR = "set_aspect_ratio_numerator"

        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value with a
        // fixed delay
        const val EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR =
            "set_aspect_ratio_with_delay_numerator"

        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value with a
        // fixed delay
        const val EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR =
            "set_aspect_ratio_with_delay_denominator"

        // Calls onPictureInPictureStateChange with the state specified in the value
        const val EXTRA_SET_PIP_STASHED = "set_pip_stashed"

        // Shows this activity over the keyguard
        const val EXTRA_SHOW_OVER_KEYGUARD = "show_over_keyguard"

        // Starts the activity (component name) provided by the value at the end of onCreate
        const val EXTRA_START_ACTIVITY = "start_activity"

        // Adds a click listener to finish this activity when it is clicked
        const val EXTRA_TAP_TO_FINISH = "tap_to_finish"

        // Dismiss keyguard when activity show.
        const val EXTRA_DISMISS_KEYGUARD = "dismiss_keyguard"

        // Number of custom actions should be set onto PictureInPictureParams
        const val EXTRA_NUMBER_OF_CUSTOM_ACTIONS = "number_of_custom_actions"

        // Whether a custom close action should be set in the PictureInPictureParams.
        const val EXTRA_CLOSE_ACTION = "set_pip_close_action"

        // Supplied when a callback is expected for pip
        const val EXTRA_SET_PIP_CALLBACK = "set_pip_callback"

        // Supplied when a callback is expected for pip when activity receives onPause
        const val EXTRA_PIP_ON_PAUSE_CALLBACK = "pip_on_pause_callback"

        // Result key for obtaining the PictureInPictureUiState#isStashed result
        const val UI_STATE_STASHED_RESULT = "ui_state_stashed_result"

        // Result key for obtaining the PictureInPictureUiState#isEnteringPip result
        const val UI_STATE_ENTERING_PIP_RESULT = "ui_state_entering_result"

        // Result key for obtaining the Activity#isInPictureInPictureMode result
        const val IS_IN_PIP_MODE_RESULT = "is_in_pip_mode_result"
    }

    /**
     * Extra key constants for [android.server.wm.app.TopActivity] and [TranslucentTopActivity].
     *
     * TODO(b/73346885): These constants should be in [android.server.wm.app.TopActivity] once the
     *   activity is moved to test APK.
     */
    object TopActivity {
        const val EXTRA_FINISH_DELAY = "FINISH_DELAY"
        const val EXTRA_FINISH_IN_ON_CREATE = "FINISH_IN_ON_CREATE"
        const val ACTION_CONVERT_TO_TRANSLUCENT = "convert_to_translucent"
        const val ACTION_CONVERT_FROM_TRANSLUCENT = "convert_from_translucent"
    }

    object UnresponsiveActivity {
        const val EXTRA_ON_CREATE_DELAY_MS = "ON_CREATE_DELAY_MS"
        const val EXTRA_ON_KEYDOWN_DELAY_MS = "ON_KEYDOWN_DELAY_MS"
        const val EXTRA_ON_MOTIONEVENT_DELAY_MS = "ON_MOTIONEVENT_DELAY_MS"
        const val PROCESS_NAME = ".unresponsive_activity_process"
    }

    object RenderService {
        const val PROCESS_NAME = ".render_process"
        const val EXTRAS_BUNDLE = "EXTRAS_BUNDLE"
        const val EXTRAS_DISPLAY_ID = "EXTRAS_DISPLAY_ID"
        const val EXTRAS_HOST_TOKEN = "EXTRAS_HOST_TOKEN"
        @JvmField val BROADCAST_EMBED_CONTENT = "$packageName.RenderService.EMBED_CONTENT"
        const val EXTRAS_SURFACE_PACKAGE = "surfacePackage"
    }

    object MultiWindowFullscreenActivity {
        const val ACTION_REQUEST_FULLSCREEN =
            "android.server.wm.app.MultiWindowFullscreenActivity.action_request_fullscreen"
        const val ACTION_RESTORE_FREEFORM =
            "android.server.wm.app.MultiWindowFullscreenActivity.action_restore_freeform"
    }

    /**
     * Extra key constants for [android.server.wm.app.VirtualDisplayActivity].
     *
     * TODO(b/73346885): These constants should be in [android.server.wm.app.VirtualDisplayActivity]
     *   once the activity is moved to test APK.
     */
    object VirtualDisplayActivity {
        const val VIRTUAL_DISPLAY_PREFIX = "HostedVirtualDisplay"
        const val KEY_CAN_SHOW_WITH_INSECURE_KEYGUARD = "can_show_with_insecure_keyguard"
        const val KEY_COMMAND = "command"
        const val KEY_COUNT = "count"
        const val KEY_DENSITY_DPI = "density_dpi"
        const val KEY_PUBLIC_DISPLAY = "public_display"
        const val KEY_RESIZE_DISPLAY = "resize_display"
        const val KEY_SHOW_SYSTEM_DECORATIONS = "show_system_decorations"
        const val KEY_PRESENTATION_DISPLAY = "presentation_display"
        const val KEY_SUPPORTS_TOUCH = "supports_touch"

        // Value constants of [KEY_COMMAND].
        const val COMMAND_CREATE_DISPLAY = "create_display"
        const val COMMAND_DESTROY_DISPLAY = "destroy_display"
        const val COMMAND_RESIZE_DISPLAY = "resize_display"
    }

    object ClickableToastActivity {
        const val ACTION_TOAST_DISPLAYED = "toast_displayed"
        const val ACTION_TOAST_TAP_DETECTED = "toast_tap_detected"
    }

    object PresentationActivity {
        const val KEY_DISPLAY_ID = "display_id"
        const val HIDE_PRESENTATION = "hide_presentation"
    }

    object LaunchingActivity {
        const val KEY_FINISH_BEFORE_LAUNCH = "finish_before_launch"
    }

    object OverlayTestService {
        const val EXTRA_LAYOUT_PARAMS = "layout_params"
        const val EXTRA_DISPLAY_ID_PARAM = "display_id"
    }

    object Notifications {
        const val CHANNEL_MAIN = "main"
        const val ID_OVERLAY_TEST_SERVICE = 1
    }

    object HideOverlayWindowsActivity {
        const val ACTION = "hide_action"
        const val PONG = "pong_action"
        const val SHOULD_HIDE = "should_hide"
        const val REPORT_TOUCH = "report_touch"
        const val MOTION_EVENT_EXTRA = "motion_event_extra"
    }

    object BackgroundActivityTransition {
        const val TRANSITION_REQUESTED = "transition_requested"
    }

    /** Extra constants for [android.server.wm.app.KeepClearRectsActivity]. */
    object KeepClearRectsActivity {
        const val EXTRA_KEEP_CLEAR_RECTS = "keep_clear_rects"
    }

    /** Extra constants for [android.server.wm.app.UiScalingTestActivity]. */
    object UiScalingTestActivity {
        const val SUBVIEW_ID1 = "compat_scale_subview1"
        const val SUBVIEW_ID2 = "compat_scale_subview2"

        const val KEY_COMMAND_SUCCESS = "success"
        const val KEY_RESOURCES_CONFIG = "resources_config"
        const val KEY_SUBVIEW_ID = "subview_id"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_VIEW_SIZE = "view_size"

        const val COMMAND_ADD_SUBVIEW = "add_subview"
        const val COMMAND_CLEAR_DEFAULT_VIEW = "clear_default_view"
        const val COMMAND_GET_SUBVIEW_SIZE = "get_subview_size"

        const val COMMAND_GET_RESOURCES_CONFIG = "get_resources_config"
        const val COMMAND_UPDATE_RESOURCES_CONFIG = "update_resources_config"
    }

    object WallpaperTargetActivity {
        const val EXTRA_ENABLE_WALLPAPER_TOUCH = "enable_wallpaper_touch"
    }

    @JvmStatic fun getPackageName() = packageName

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
