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

package com.android.bedstead.nene.services;

import static java.util.Map.entry;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
//import android.app.ActivityTaskManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.DownloadManager;
// import android.app.DreamManager;
import android.app.GameManager;
import android.app.GrammaticalInflectionManager;
import android.app.KeyguardManager;
import android.app.LocaleManager;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.UiModeManager;
//import android.app.UriGrantsManager;
import android.app.VrManager;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.app.ambientcontext.AmbientContextManager;
import android.app.contentsuggestions.ContentSuggestionsManager;
import android.app.people.PeopleManager;
import android.app.prediction.AppPredictionManager;
import android.app.search.SearchUiManager;
//import android.app.slice.SliceManager;
import android.app.smartspace.SmartspaceManager;
import android.app.time.TimeManager;
//import android.app.timedetector.TimeDetector;
//import android.app.timezonedetector.TimeZoneDetector;
//import android.app.trust.TrustManager;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStatsManager;
import android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager;
import android.app.wearable.WearableSensingManager;
import android.apphibernation.AppHibernationManager;
import android.appwidget.AppWidgetManager;
import android.companion.CompanionDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.RestrictionsManager;
import android.content.integrity.AppIntegrityManager;
import android.content.om.OverlayManager;
import android.content.pm.CrossProfileApps;
//import android.content.pm.DataLoaderManager;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutManager;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.credentials.CredentialManager;
//import android.debug.AdbManager;
import android.graphics.fonts.FontManager;
import android.hardware.ConsumerIrManager;
import android.hardware.SensorManager;
//import android.hardware.SensorPrivacyManager;
//import android.hardware.SerialManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.camera2.CameraManager;
//import android.hardware.devicestate.DeviceStateManager;
//import android.hardware.display.ColorDisplayManager;
import android.hardware.display.DisplayManager;
//import android.hardware.face.FaceManager;
//import android.hardware.fingerprint.FingerprintManager;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.input.InputManager;
//import android.hardware.iris.IrisManager;
//import android.hardware.lights.LightsManager;
import android.hardware.location.ContextHubManager;
//import android.hardware.radio.RadioManager;
import android.hardware.usb.UsbManager;
//import android.location.CountryDetector;
import android.location.LocationManager;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.media.metrics.MediaMetricsManager;
import android.media.midi.MidiManager;
import android.media.musicrecognition.MusicRecognitionManager;
import android.media.projection.MediaProjectionManager;
//import android.media.soundtrigger.SoundTriggerManager;
import android.media.tv.TvInputManager;
import android.media.tv.interactive.TvInteractiveAppManager;
//import android.media.tv.tunerresourcemanager.TunerResourceManager;
//import android.net.NetworkPolicyManager;
import android.net.NetworkScoreManager;
//import android.net.NetworkWatchlistManager;
//import android.net.PacProxyManager;
import android.net.TetheringManager;
import android.net.VpnManager;
//import android.net.vcn.VcnManager;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.os.BatteryManager;
import android.os.BatteryStatsManager;
import android.os.BugreportManager;
import android.os.DropBoxManager;
import android.os.HardwarePropertiesManager;
import android.os.IBinder;
//import android.os.IncidentManager;
import android.os.PerformanceHintManager;
//import android.os.PermissionEnforcer;
import android.os.PowerManager;
//import android.os.RecoverySystem;
import android.os.SystemConfigManager;
import android.os.SystemUpdateManager;
import android.os.UserManager;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.health.SystemHealthManager;
//import android.os.image.DynamicSystemManager;
//import android.os.incremental.IncrementalManager;
import android.os.storage.StorageManager;
//import android.permission.LegacyPermissionManager;
//import android.permission.PermissionCheckerManager;
import android.permission.PermissionControllerManager;
import android.permission.PermissionManager;
import android.print.PrintManager;
import android.security.FileIntegrityManager;
//import android.security.attestationverification.AttestationVerificationManager;
import android.service.oemlock.OemLockManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.telecom.TelecomManager;
//import android.telephony.MmsManager;
//import android.telephony.TelephonyRegistryManager;
//import android.transparency.BinaryTransparencyManager;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.CaptioningManager;
//import android.view.autofill.AutofillManager;
//import android.view.contentcapture.ContentCaptureManager;
import android.view.displayhash.DisplayHashManager;
import android.view.inputmethod.InputMethodManager;
//import android.view.selectiontoolbar.SelectionToolbarManager;
import android.view.textclassifier.TextClassificationManager;
import android.view.textservice.TextServicesManager;
import android.view.translation.TranslationManager;
import android.view.translation.UiTranslationManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TestApis related to system services.
 */
@Experimental
public final class Services {

    public static final Services sInstance = new Services();

    // Mapping from SystemServiceRegistry.java
    @SuppressLint("NewApi")
    private static final Map<String, Class<?>> sServiceMapping = 
            Map.ofEntries(
                    entry(Context.ACCESSIBILITY_SERVICE, AccessibilityManager.class),
                    entry(Context.CAPTIONING_SERVICE, CaptioningManager.class),
                    entry(Context.ACCOUNT_SERVICE, AccountManager.class),
                    entry(Context.ACTIVITY_SERVICE, ActivityManager.class),
//        entry(Context.ACTIVITY_TASK_SERVICE, ActivityTaskManager.class),
//        entry(Context.URI_GRANTS_SERVICE, UriGrantsManager.class),
                    entry(Context.ALARM_SERVICE, AlarmManager.class),
                    entry(Context.AUDIO_SERVICE, AudioManager.class),
                    entry(Context.AUDIO_DEVICE_VOLUME_SERVICE, AudioDeviceVolumeManager.class),
                    entry(Context.MEDIA_ROUTER_SERVICE, MediaRouter.class),
                    entry(Context.HDMI_CONTROL_SERVICE, HdmiControlManager.class),
                    entry(Context.TEXT_CLASSIFICATION_SERVICE, TextClassificationManager.class),
//        entry(Context.SELECTION_TOOLBAR_SERVICE, SelectionToolbarManager.class),
                    entry(Context.FONT_SERVICE, FontManager.class),
                    entry(Context.CLIPBOARD_SERVICE, ClipboardManager.class),
//        entry(Context.PAC_PROXY_SERVICE, PacProxyManager.class),
                    entry(Context.NETD_SERVICE, IBinder.class),
                    entry(Context.TETHERING_SERVICE, TetheringManager.class),
                    entry(Context.VPN_MANAGEMENT_SERVICE, VpnManager.class),
//        entry(Context.VCN_MANAGEMENT_SERVICE, VcnManager.class),
//        entry(Context.COUNTRY_DETECTOR, CountryDetector.class),
                    entry(Context.DEVICE_POLICY_SERVICE, DevicePolicyManager.class),
                    entry(Context.DOWNLOAD_SERVICE, DownloadManager.class),
                    entry(Context.BATTERY_SERVICE, BatteryManager.class),
                    entry(Context.DROPBOX_SERVICE, DropBoxManager.class),
//        entry(Context.BINARY_TRANSPARENCY_SERVICE, BinaryTransparencyManager.class),
                    entry(Context.INPUT_SERVICE, InputManager.class),
                    entry(Context.DISPLAY_SERVICE, DisplayManager.class),
//        entry(Context.COLOR_DISPLAY_SERVICE, ColorDisplayManager.class),
                    entry(Context.INPUT_METHOD_SERVICE, InputMethodManager.class),
                    entry(Context.TEXT_SERVICES_MANAGER_SERVICE, TextServicesManager.class),
                    entry(Context.KEYGUARD_SERVICE, KeyguardManager.class),
                    entry(Context.LAYOUT_INFLATER_SERVICE, LayoutInflater.class),
                    entry(Context.LOCATION_SERVICE, LocationManager.class),
//        entry(Context.NETWORK_POLICY_SERVICE, NetworkPolicyManager.class),
                    entry(Context.NOTIFICATION_SERVICE, NotificationManager.class),
                    entry(Context.PEOPLE_SERVICE, PeopleManager.class),
                    entry(Context.POWER_SERVICE, PowerManager.class),
                    entry(Context.PERFORMANCE_HINT_SERVICE, PerformanceHintManager.class),
//        entry(Context.RECOVERY_SERVICE, RecoverySystem.class),
                    entry(Context.SEARCH_SERVICE, SearchManager.class),
                    entry(Context.SENSOR_SERVICE, SensorManager.class),
//        entry(Context.SENSOR_PRIVACY_SERVICE, SensorPrivacyManager.class),
                    entry(Context.STATUS_BAR_SERVICE, StatusBarManager.class),
                    entry(Context.STORAGE_SERVICE, StorageManager.class),
                    entry(Context.STORAGE_STATS_SERVICE, StorageStatsManager.class),
                    entry(Context.SYSTEM_UPDATE_SERVICE, SystemUpdateManager.class),
                    entry(Context.SYSTEM_CONFIG_SERVICE, SystemConfigManager.class),
//        entry(Context.TELEPHONY_REGISTRY_SERVICE, TelephonyRegistryManager.class),
                    entry(Context.TELECOM_SERVICE, TelecomManager.class),
//        entry(Context.MMS_SERVICE, MmsManager.class),
                    entry(Context.UI_MODE_SERVICE, UiModeManager.class),
                    entry(Context.USB_SERVICE, UsbManager.class),
//        entry(Context.ADB_SERVICE, AdbManager.class),
//        entry(Context.SERIAL_SERVICE, SerialManager.class),
                    entry(Context.VIBRATOR_MANAGER_SERVICE, VibratorManager.class),
                    entry(Context.VIBRATOR_SERVICE, Vibrator.class),
                    entry(Context.WALLPAPER_SERVICE, WallpaperManager.class),
                    entry(Context.WIFI_NL80211_SERVICE, WifiNl80211Manager.class),
                    entry(Context.WINDOW_SERVICE, WindowManager.class),
                    entry(Context.USER_SERVICE, UserManager.class),
                    entry(Context.APP_OPS_SERVICE, AppOpsManager.class),
                    entry(Context.CAMERA_SERVICE, CameraManager.class),
                    entry(Context.LAUNCHER_APPS_SERVICE, LauncherApps.class),
                    entry(Context.RESTRICTIONS_SERVICE, RestrictionsManager.class),
                    entry(Context.PRINT_SERVICE, PrintManager.class),
                    entry(Context.COMPANION_DEVICE_SERVICE, CompanionDeviceManager.class),
                    entry(Context.VIRTUAL_DEVICE_SERVICE, VirtualDeviceManager.class),
                    entry(Context.CONSUMER_IR_SERVICE, ConsumerIrManager.class),
//        entry(Context.TRUST_SERVICE, TrustManager.class),
//                    entry(Context.FINGERPRINT_SERVICE, FingerprintManager.class),
//        entry(Context.FACE_SERVICE, FaceManager.class),
//        entry(Context.IRIS_SERVICE, IrisManager.class),
                    entry(Context.BIOMETRIC_SERVICE, BiometricManager.class),
                    entry(Context.TV_INTERACTIVE_APP_SERVICE, TvInteractiveAppManager.class),
                    entry(Context.TV_INPUT_SERVICE, TvInputManager.class),
//        entry(Context.TV_TUNER_RESOURCE_MGR_SERVICE, TunerResourceManager.class),
                    entry(Context.NETWORK_SCORE_SERVICE, NetworkScoreManager.class),
                    entry(Context.USAGE_STATS_SERVICE, UsageStatsManager.class),
                    entry(Context.PERSISTENT_DATA_BLOCK_SERVICE, PersistentDataBlockManager.class),
                    entry(Context.OEM_LOCK_SERVICE, OemLockManager.class),
                    entry(Context.MEDIA_PROJECTION_SERVICE, MediaProjectionManager.class),
                    entry(Context.APPWIDGET_SERVICE, AppWidgetManager.class),
                    entry(Context.MIDI_SERVICE, MidiManager.class),
//        entry(Context.RADIO_SERVICE, RadioManager.class),
                    entry(Context.HARDWARE_PROPERTIES_SERVICE, HardwarePropertiesManager.class),
//        entry(Context.SOUND_TRIGGER_SERVICE, SoundTriggerManager.class),
                    entry(Context.SHORTCUT_SERVICE, ShortcutManager.class),
                    entry(Context.OVERLAY_SERVICE, OverlayManager.class),
//        entry(Context.NETWORK_WATCHLIST_SERVICE, NetworkWatchlistManager.class),
                    entry(Context.SYSTEM_HEALTH_SERVICE, SystemHealthManager.class),
                    entry(Context.CONTEXTHUB_SERVICE, ContextHubManager.class),
//        entry(Context.INCIDENT_SERVICE, IncidentManager.class),
                    entry(Context.BUGREPORT_SERVICE, BugreportManager.class),
//        entry(Context.AUTOFILL_MANAGER_SERVICE, AutofillManager.class),
                    entry(Context.CREDENTIAL_SERVICE, CredentialManager.class),
                    entry(Context.MUSIC_RECOGNITION_SERVICE, MusicRecognitionManager.class),
//                    entry(Context.CONTENT_CAPTURE_MANAGER_SERVICE, ContentCaptureManager.class),
                    entry(Context.TRANSLATION_MANAGER_SERVICE, TranslationManager.class),
                    entry(Context.UI_TRANSLATION_SERVICE, UiTranslationManager.class),
                    entry(Context.SEARCH_UI_SERVICE, SearchUiManager.class),
                    entry(Context.SMARTSPACE_SERVICE, SmartspaceManager.class),
                    entry(Context.APP_PREDICTION_SERVICE, AppPredictionManager.class),
                    entry(Context.VR_SERVICE, VrManager.class),
                    entry(Context.CROSS_PROFILE_APPS_SERVICE, CrossProfileApps.class),
//        entry(Context.SLICE_SERVICE, SliceManager.class),
//        entry(Context.TIME_DETECTOR_SERVICE, TimeDetector.class),
//        entry(Context.TIME_ZONE_DETECTOR_SERVICE, TimeZoneDetector.class),
                    entry(Context.TIME_MANAGER_SERVICE, TimeManager.class),
                    entry(Context.PERMISSION_SERVICE, PermissionManager.class),
//        entry(Context.LEGACY_PERMISSION_SERVICE, LegacyPermissionManager.class),
                    entry(Context.PERMISSION_CONTROLLER_SERVICE, PermissionControllerManager.class),
//        entry(Context.PERMISSION_CHECKER_SERVICE, PermissionCheckerManager.class),
//        entry(Context.PERMISSION_ENFORCER_SERVICE, PermissionEnforcer.class),
//        entry(Context.DYNAMIC_SYSTEM_SERVICE, DynamicSystemManager.class),
                    entry(Context.BATTERY_STATS_SERVICE, BatteryStatsManager.class),
//        entry(Context.DATA_LOADER_MANAGER_SERVICE, DataLoaderManager.class),
//        entry(Context.LIGHTS_SERVICE, LightsManager.class),
                    entry(Context.LOCALE_SERVICE, LocaleManager.class),
//        entry(Context.INCREMENTAL_SERVICE, IncrementalManager.class),
                    entry(Context.FILE_INTEGRITY_SERVICE, FileIntegrityManager.class),
                    entry(Context.APP_INTEGRITY_SERVICE, AppIntegrityManager.class),
                    entry(Context.APP_HIBERNATION_SERVICE, AppHibernationManager.class),
//                    entry(Context.DREAM_SERVICE, DreamManager.class),
//        entry(Context.DEVICE_STATE_SERVICE, DeviceStateManager.class),
                    entry(Context.MEDIA_METRICS_SERVICE, MediaMetricsManager.class),
                    entry(Context.GAME_SERVICE, GameManager.class),
                    entry(Context.DOMAIN_VERIFICATION_SERVICE, DomainVerificationManager.class),
                    entry(Context.DISPLAY_HASH_SERVICE, DisplayHashManager.class),
                    entry(Context.AMBIENT_CONTEXT_SERVICE, AmbientContextManager.class),
                    entry(Context.WEARABLE_SENSING_SERVICE, WearableSensingManager.class),
                    entry(Context.GRAMMATICAL_INFLECTION_SERVICE, GrammaticalInflectionManager.class),
                    entry(Context.SHARED_CONNECTIVITY_SERVICE, SharedConnectivityManager.class),
                    entry(Context.CONTENT_SUGGESTIONS_SERVICE, ContentSuggestionsManager.class),
                    entry(Context.WALLPAPER_EFFECTS_GENERATION_SERVICE,
                            WallpaperEffectsGenerationManager.class)
//        entry(Context.ATTESTATION_VERIFICATION_SERVICE,
//                AttestationVerificationManager.class),
            );
    private static final Map<Class<?>, String> sServiceNameMapping =
            sServiceMapping.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));


    private Services() {

    }

    public boolean serviceIsAvailable(String service) {
        if (!sServiceMapping.containsKey(service)) {
            throw new NeneException("Unknown service " + service + ". Check Nene Services map");
        }
        return serviceIsAvailable(service, sServiceMapping.get(service));
    }

    public boolean serviceIsAvailable(Class<?> serviceClass) {
        if (!sServiceNameMapping.containsKey(serviceClass)) {
            throw new NeneException("Unknown service " + serviceClass + ". Check Nene Services map");
        }
        return serviceIsAvailable(sServiceNameMapping.get(serviceClass), serviceClass);
    }

    private boolean serviceIsAvailable(String service, Class<?> serviceClass) {
        if (TestApis.context().instrumentedContext().getSystemService(serviceClass) == null) {
            return false;
        }

        return ShellCommand.builder("cmd -l")
                .executeOrThrowNeneException("Error getting service list")
                .contains(service);

    }

}
