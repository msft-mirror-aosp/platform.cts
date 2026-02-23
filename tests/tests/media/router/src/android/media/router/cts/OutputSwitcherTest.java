/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.media.router.cts;

import static android.Manifest.permission.NEARBY_WIFI_DEVICES;
import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.RouteListingPreference.ACTION_RESOLVE_MISSING_PERMISSIONS;
import static android.media.RouteListingPreference.ACTION_TRANSFER_MEDIA;
import static android.media.router.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.router.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID4_TO_SELECT_AND_DESELECT;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID5_TO_TRANSFER_TO;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID9_REMOTE;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID_REQ_INTERNET_PERM;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID_REQ_NEARBY_PERM;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID_REQ_UNDECLARED_PERM;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID_SPECIAL_FEATURE;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME2;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME4;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME5;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME9;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME_SPECIAL_FEATURE;
import static android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.RoutingController;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.media.session.MediaSession;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.BidiFormatter;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.UiAutomatorUtils2;
import com.android.media.flags.Flags;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "The system should be able to bind to StubMediaRoute2ProviderService")
@LargeTest
@FrameworkSpecificTest
@RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE) // TODO(b/397522231)
@RequireDoesNotHaveFeature(PackageManager.FEATURE_LEANBACK) // TODO(b/397521067)
@RequireDoesNotHaveFeature(PackageManager.FEATURE_WATCH) // TODO(b/397520196)
public class OutputSwitcherTest {
    private static final String TAG = "OutputSwitcherTest";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final int TIMEOUT_MS = 15_000;

    // This comes from the value of com.android.systemui.R.string.media_output_item_connected_state
    // (frameworks/base/packages/SystemUI/res/values/strings.xml)
    private static final String CONNECTED_STATE = "Connected";

    // This comes from the value of com.android.settingslib.R.string.media_transfer_this_device_name
    // (frameworks/base/packages/SettingsLib/res/values/strings.xml)
    public static final String THIS_DEVICE_PREFIX = "This ";

    // This comes from the value of
    // com.android.systemui.R.string.cast_to_other_device_stop_dialog_button
    // (frameworks/base/packages/SystemUI/res/values/strings.xml)
    private static final String STOP_CASTING_BUTTON_TITLE = "Stop casting";

    // This comes from R.id.warning_text and R.id.warning_fix_button
    // (frameworks/base/packages/SystemUI/res/layout/media_output_dialog.xml)
    private static final String WARNING_TEXT_ID = "warning_text";
    private static final String WARNING_FIX_BTN_ID = "warning_fix_button";

    public static final String SESSION_TEST_TITLE_1 = "session_title_1";
    public static final String SESSION_TEST_TITLE_2 = "session_title_2";
    public static final String SESSION_TEST_ARTIST_1 = "session_artist_1";
    public static final String SESSION_TEST_ARTIST_2 = "session_artist_2";

    // Required by Bedstead.
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public StubMediaRoute2ProviderService.Setup mProviderSetup =
            new StubMediaRoute2ProviderService.Setup();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private StubMediaRoute2ProviderService.Proxy mProviderProxy;

    @Mock private MediaRouter2.TransferCallback mTransferCallback;
    private MediaRouter2.ControllerCallback mControllerCallback;

    private Context mContext;
    private Executor mExecutor;
    private MediaRouter2 mRouter2;
    private StubMediaRoute2ProviderService mService;
    private MediaRouter2.RouteCallback mEmptyRouteCallback = new MediaRouter2.RouteCallback() {};

    @Before
    public void setUp() throws Exception {
        // According to CTS setup rules, the device locale should be set to english:
        //   https://source.android.com/docs/compatibility/cts/setup#device-config
        // Some tests rely on finding particular accessibility text in the output switcher dialog,
        // so we double-check the locale here.
        assertThat(Locale.getDefault().getLanguage()).isEqualTo(Locale.ENGLISH.getLanguage());

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mRouter2 = MediaRouter2.getInstance(mContext);
        MediaRouter2TestActivity.startActivity(mContext);
        mService = mProviderSetup.setupAndGetService(mContext);
        mService.setProxy(mProviderProxy);
        // We use a spy to make new overloads work as intended (call the overload with fewer args).
        mControllerCallback = spy(MediaRouter2.ControllerCallback.class);
    }

    @After
    public void tearDown() {
        if (mRouter2 != null) {
            mRouter2.unregisterRouteCallback(mEmptyRouteCallback);
        }
        MediaRouter2TestActivity.finishActivity();
        dismissSystemDialogs();
    }

    @Test
    public void showSystemOutputSwitcher_preferredRoutesAppear() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME2).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
    }

    @Test
    public void showSystemOutputSwitcher_clickOnRoute_sessionIsCreated() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        mRouter2.registerTransferCallback(mExecutor, mTransferCallback);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        clickRouteInDialog(ROUTE_NAME1);

        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onCreateSession(anyLong(), any(), eq(ROUTE_ID1), any());
        ArgumentCaptor<RoutingController> newController =
                ArgumentCaptor.forClass(RoutingController.class);
        verify(mTransferCallback, timeout(TIMEOUT_MS)).onTransfer(any(), newController.capture());
        assertThat(
                        newController.getValue().getSelectedRoutes().stream()
                                .map(MediaRoute2Info::getName))
                .containsExactly(ROUTE_NAME1);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    public void showSystemOutputSwitcherWithMediaSession_oneSession_showsSessionInfo()
            throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        MediaSession session = new MediaSession(mContext, "test_session");
        session.setMetadata(
                new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, SESSION_TEST_TITLE_1)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, SESSION_TEST_ARTIST_1)
                        .build());
        session.setActive(true);

        assertThat(mRouter2.showSystemOutputSwitcher(session.getSessionToken())).isTrue();

        UiAutomatorUtils2.waitFindObject(
                By.text(SESSION_TEST_TITLE_1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(
                By.text(SESSION_TEST_ARTIST_1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    public void showSystemOutputSwitcherWithMediaSession_twoSessions_showsSession2Info()
            throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        MediaSession session1 = new MediaSession(mContext, "test_session_1");
        session1.setMetadata(
                new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, SESSION_TEST_TITLE_1)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, SESSION_TEST_ARTIST_1)
                        .build());
        session1.setActive(true);

        MediaSession session2 = new MediaSession(mContext, "test_session_2");
        session2.setMetadata(
                new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, SESSION_TEST_TITLE_2)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, SESSION_TEST_ARTIST_2)
                        .build());
        session2.setActive(true);

        assertThat(mRouter2.showSystemOutputSwitcher(session2.getSessionToken())).isTrue();

        UiAutomatorUtils2.waitFindObject(
                By.text(SESSION_TEST_TITLE_2).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(
                By.text(SESSION_TEST_ARTIST_2).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
    }

    @Test
    public void selectOneRoute_closeAndOpenDialog_routeStillSelected() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        clickRouteInDialog(ROUTE_NAME1);
        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onCreateSession(anyLong(), any(), eq(ROUTE_ID1), any());
        assertDialogShowsConnectionTo(ROUTE_NAME1);

        dismissSystemDialogs();
        UiAutomatorUtils2.waitUntilObjectGone(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE));

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        assertDialogShowsConnectionTo(ROUTE_NAME1);
    }

    @Test
    public void selectOneRoute_thenTransferToAnother_sessionTransferred() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID5_TO_TRANSFER_TO));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        clickRouteInDialog(ROUTE_NAME1);
        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onCreateSession(anyLong(), any(), eq(ROUTE_ID1), any());
        assertDialogShowsConnectionTo(ROUTE_NAME1);

        clickRouteInDialog(ROUTE_NAME5);
        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onTransferToRoute(anyLong(), any(), eq(ROUTE_ID5_TO_TRANSFER_TO));
        assertDialogShowsConnectionTo(ROUTE_NAME5);
    }

    @Test
    public void selectOneRoute_thenTransferToThisDevice_sessionTransferred() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE, FEATURE_LIVE_AUDIO));
        mRouter2.registerTransferCallback(mExecutor, mTransferCallback);

        RoutingController systemController = mRouter2.getSystemController();
        List<MediaRoute2Info> systemRoutes = systemController.getSelectedRoutes();
        assertThat(systemRoutes).hasSize(1);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        assertDialogShowsConnectionToThisDevice();

        clickRouteInDialog(ROUTE_NAME1);
        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onCreateSession(anyLong(), any(), eq(ROUTE_ID1), any());
        assertDialogShowsConnectionTo(ROUTE_NAME1);

        InOrder onTransferCalls = inOrder(mTransferCallback);
        ArgumentCaptor<RoutingController> controllerCaptor =
                ArgumentCaptor.forClass(RoutingController.class);

        // On the first call to onTransfer, the old controller should be the system controller and
        // the new controller should be the one for newly selected route1.
        onTransferCalls
                .verify(mTransferCallback, timeout(TIMEOUT_MS))
                .onTransfer(eq(systemController), controllerCaptor.capture());
        RoutingController route1Controller = controllerCaptor.getValue();

        clickThisDeviceRoute();
        assertDialogShowsConnectionToThisDevice();

        // Now that the user clicked back to "this device", we should get a second call to
        // onTransfer, and the order of the old and new controller arguments should be reversed (as
        // compared to the first call).
        onTransferCalls
                .verify(mTransferCallback, timeout(TIMEOUT_MS))
                .onTransfer(
                        argThat(
                                oldController ->
                                        route1Controller.getId().equals(oldController.getId())),
                        eq(systemController));

        route1Controller.release();
        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onReleaseSession(anyLong(), eq(route1Controller.getOriginalId()));
    }

    @Test
    public void changePublishedRoutesWhileDialogOpen_listOfRoutesUpdates() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME2).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);

        // Change the list of advertised routes to 1 and 5 (instead of 1 and 2).
        mService.initializeRoutes();
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID5_TO_TRANSFER_TO));
        mService.publishRoutes();

        UiAutomatorUtils2.waitUntilObjectGone(By.text(ROUTE_NAME2).pkg(SYSTEM_UI_PACKAGE));
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME5).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
    }

    @Test
    public void changeDiscoveryPreferenceWhileDialogOpen_listOfRoutesUpdates() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2, ROUTE_ID_SPECIAL_FEATURE));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME2).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);

        mRouter2.unregisterRouteCallback(mEmptyRouteCallback);
        registerRouteCallback(List.of(FEATURE_SPECIAL));

        UiAutomatorUtils2.waitUntilObjectGone(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE));
        UiAutomatorUtils2.waitUntilObjectGone(By.text(ROUTE_NAME2).pkg(SYSTEM_UI_PACKAGE));
        UiAutomatorUtils2.waitFindObject(
                By.text(ROUTE_NAME_SPECIAL_FEATURE).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
    }

    @Test
    public void providerReleasesSessionWhileDialogOpen_dialogUpdates() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));
        mRouter2.registerTransferCallback(mExecutor, mTransferCallback);
        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        clickRouteInDialog(ROUTE_NAME1);
        assertDialogShowsConnectionTo(ROUTE_NAME1);
        ArgumentCaptor<RoutingController> controllerCaptor =
                ArgumentCaptor.forClass(RoutingController.class);
        verify(mTransferCallback, timeout(TIMEOUT_MS))
                .onTransfer(any(), controllerCaptor.capture());
        String originalSessionId = controllerCaptor.getValue().getOriginalId();
        mService.onReleaseSession(-1 /* requestId */, originalSessionId);

        assertDialogShowsConnectionToThisDevice();
    }

    @Test
    public void appCallsTransferToWhileDialogOpen_dialogUpdates() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID5_TO_TRANSFER_TO));
        registerRouteCallback(List.of(FEATURE_SAMPLE));
        mRouter2.registerTransferCallback(mExecutor, mTransferCallback);
        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        clickRouteInDialog(ROUTE_NAME1);
        assertDialogShowsConnectionTo(ROUTE_NAME1);
        // Transfer the route by calling transferTo from the client app instead of by clicking on
        // the route in the UI.
        Optional<MediaRoute2Info> route5 =
                mRouter2.getRoutes().stream()
                        .filter(r -> r.getName().toString().equals(ROUTE_NAME5))
                        .findFirst();
        assertThat(route5).isPresent();
        mRouter2.transferTo(route5.get());

        assertDialogShowsConnectionTo(ROUTE_NAME5);
    }

    @Test
    public void selectRemoteRoute_thenTapStopCastingButton_correctEventsFire() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID9_REMOTE));
        registerRouteCallback(List.of(FEATURE_SAMPLE, MediaRoute2Info.FEATURE_REMOTE_PLAYBACK));
        mRouter2.registerTransferCallback(mExecutor, mTransferCallback);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        clickRouteInDialog(ROUTE_NAME9);

        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onCreateSession(anyLong(), any(), eq(ROUTE_ID9_REMOTE), any());

        ArgumentCaptor<RoutingController> controllerCaptor =
                ArgumentCaptor.forClass(RoutingController.class);

        verify(mTransferCallback, timeout(TIMEOUT_MS))
                .onTransfer(any(), controllerCaptor.capture());
        String sessionId = controllerCaptor.getValue().getId();
        String originalSessionId = controllerCaptor.getValue().getOriginalId();

        clickButtonWithLabel(STOP_CASTING_BUTTON_TITLE);

        verify(mTransferCallback, timeout(TIMEOUT_MS))
                .onStop(argThat(stoppedController -> sessionId.equals(stoppedController.getId())));
        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onReleaseSession(anyLong(), eq(originalSessionId));
    }

    @Test
    public void streamExpansion_addSecondRoute_eventsFire() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID4_TO_SELECT_AND_DESELECT));
        registerRouteCallback(List.of(FEATURE_SAMPLE));
        mRouter2.registerTransferCallback(mExecutor, mTransferCallback);
        mRouter2.registerControllerCallback(mExecutor, mControllerCallback);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        clickRouteInDialog(ROUTE_NAME1);

        ArgumentCaptor<RoutingController> controllerCaptor =
                ArgumentCaptor.forClass(RoutingController.class);
        verify(mTransferCallback, timeout(TIMEOUT_MS))
                .onTransfer(any(), controllerCaptor.capture());

        String sessionId = controllerCaptor.getValue().getOriginalId();

        clickAddDeviceToGroup(ROUTE_NAME4);

        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onSelectRoute(anyLong(), eq(sessionId), eq(ROUTE_ID4_TO_SELECT_AND_DESELECT));

        verify(mControllerCallback, timeout(TIMEOUT_MS).atLeastOnce())
                .onControllerUpdated(
                        argThat(
                                controller -> {
                                    Set<String> selectedRouteIds =
                                            controller.getSelectedRoutes().stream()
                                                    .map(MediaRoute2Info::getOriginalId)
                                                    .collect(Collectors.toSet());
                                    return selectedRouteIds.equals(
                                            Set.of(ROUTE_ID1, ROUTE_ID4_TO_SELECT_AND_DESELECT));
                                }));
    }

    @Test
    public void streamExpansion_addAndRemoveSecondRoute_eventsFire() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID4_TO_SELECT_AND_DESELECT));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        clickRouteInDialog(ROUTE_NAME1);

        clickAddDeviceToGroup(ROUTE_NAME4);

        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onSelectRoute(anyLong(), any(), eq(ROUTE_ID4_TO_SELECT_AND_DESELECT));

        clickRemoveDeviceFromGroup(ROUTE_NAME4);

        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onDeselectRoute(anyLong(), any(), eq(ROUTE_ID4_TO_SELECT_AND_DESELECT));
    }

    @Test
    public void streamExpansion_selectSecondRouteUsingController_dialogUpdates() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID4_TO_SELECT_AND_DESELECT));
        registerRouteCallback(List.of(FEATURE_SAMPLE));
        SettableFuture<RoutingController> controllerFuture = SettableFuture.create();
        mRouter2.registerControllerCallback(
                mExecutor,
                new MediaRouter2.ControllerCallback() {
                    @Override
                    public void onControllerUpdated(@NonNull RoutingController controller) {
                        if (selectedRoutes(controller).containsKey(ROUTE_ID1)
                                && selectableRoutes(controller)
                                        .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)) {
                            controllerFuture.set(controller);
                        }
                    }
                });
        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        clickRouteInDialog(ROUTE_NAME1);
        RoutingController controller = controllerFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        controller.selectRoute(selectableRoutes(controller).get(ROUTE_ID4_TO_SELECT_AND_DESELECT));

        assertDialogShowsConnectionTo(ROUTE_NAME4);
    }

    @Test
    public void streamExpansion_deselectRouteUsingController_dialogUpdates() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID4_TO_SELECT_AND_DESELECT));
        registerRouteCallback(List.of(FEATURE_SAMPLE));
        SettableFuture<RoutingController> controllerFuture = SettableFuture.create();
        mRouter2.registerControllerCallback(
                mExecutor,
                new MediaRouter2.ControllerCallback() {
                    @Override
                    public void onControllerUpdated(@NonNull RoutingController controller) {
                        if (deselectableRoutes(controller)
                                .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)) {
                            controllerFuture.set(controller);
                        }
                    }
                });
        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        assertDialogShowsConnectionToThisDevice();

        clickRouteInDialog(ROUTE_NAME1);
        clickAddDeviceToGroup(ROUTE_NAME4);
        UiAutomatorUtils2.waitFindObject(removeDeviceFromGroupSelector(ROUTE_NAME4));
        RoutingController controller = controllerFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        controller.deselectRoute(
                deselectableRoutes(controller).get(ROUTE_ID4_TO_SELECT_AND_DESELECT));

        UiAutomatorUtils2.waitFindObject(addDeviceToGroupSelector(ROUTE_NAME4));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    public void showSystemOutputSwitcher_missingPermissions_showsWarning() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID_REQ_NEARBY_PERM));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        ComponentName resolveComponent =
                new ComponentName(mContext, MediaRouter2TestActivity.class);
        RouteListingPreference preference =
                new RouteListingPreference.Builder()
                        .setMissingPermissionsComponentName(resolveComponent)
                        .setItems(
                                List.of(
                                        new RouteListingPreference.Item.Builder(
                                                        getRouteUniqueId(ROUTE_ID1))
                                                .build()))
                        .build();
        mRouter2.setRouteListingPreference(preference);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        UiAutomatorUtils2.waitFindObject(By.res(SYSTEM_UI_PACKAGE, WARNING_TEXT_ID), TIMEOUT_MS);
        UiObject2 fixButton =
                UiAutomatorUtils2.waitFindObject(
                        By.res(SYSTEM_UI_PACKAGE, WARNING_FIX_BTN_ID), TIMEOUT_MS);

        Instrumentation.ActivityMonitor activityMonitor =
                InstrumentationRegistry.getInstrumentation()
                        .addMonitor(MediaRouter2TestActivity.class.getName(), null, false);

        fixButton.click();

        Activity activity = activityMonitor.waitForActivityWithTimeout(TIMEOUT_MS);
        assertThat(activity).isNotNull();
        assertThat(activity).isInstanceOf(MediaRouter2TestActivity.class);

        // Verify the intent was sent to the resolve component.
        Intent startedIntent = activity.getIntent();
        assertThat(startedIntent).isNotNull();
        assertThat(startedIntent.getAction()).isEqualTo(ACTION_RESOLVE_MISSING_PERMISSIONS);
        assertThat(startedIntent.getComponent()).isEqualTo(resolveComponent);
        ArrayList<String> missingPermissions =
                startedIntent.getStringArrayListExtra(
                        RouteListingPreference.EXTRA_MISSING_PERMISSIONS);
        assertThat(missingPermissions).containsExactly(NEARBY_WIFI_DEVICES);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    public void showSystemOutputSwitcher_withRequiredPermissions_noWarning() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID_REQ_INTERNET_PERM));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        ComponentName component = new ComponentName(mContext, MediaRouter2TestActivity.class);
        RouteListingPreference preference =
                new RouteListingPreference.Builder()
                        .setMissingPermissionsComponentName(component)
                        .setItems(
                                List.of(
                                        new RouteListingPreference.Item.Builder(
                                                        getRouteUniqueId(ROUTE_ID1))
                                                .build()))
                        .build();
        mRouter2.setRouteListingPreference(preference);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitUntilObjectGone(By.res(SYSTEM_UI_PACKAGE, "warning_text"));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    public void showSystemOutputSwitcher_missingUndeclaredPermissions_noWarning() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID_REQ_UNDECLARED_PERM));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        ComponentName component = new ComponentName(mContext, MediaRouter2TestActivity.class);
        RouteListingPreference preference =
                new RouteListingPreference.Builder()
                        .setMissingPermissionsComponentName(component)
                        .setItems(
                                List.of(
                                        new RouteListingPreference.Item.Builder(
                                                        getRouteUniqueId(ROUTE_ID1))
                                                .build()))
                        .build();
        mRouter2.setRouteListingPreference(preference);

        // The required permission is not declared in the manifest, so no warning should be shown.
        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitUntilObjectGone(By.res(SYSTEM_UI_PACKAGE, "warning_text"));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    public void showSystemOutputSwitcher_noResolveComponent_noWarning() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID_REQ_NEARBY_PERM));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        // No resolve component set in the preference.
        RouteListingPreference preference =
                new RouteListingPreference.Builder()
                        .setItems(
                                List.of(
                                        new RouteListingPreference.Item.Builder(
                                                        getRouteUniqueId(ROUTE_ID1))
                                                .build()))
                        .build();
        mRouter2.setRouteListingPreference(preference);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitUntilObjectGone(By.res(SYSTEM_UI_PACKAGE, "warning_text"));
    }

    @Test
    public void showSystemOutputSwitcher_showsCustomSubtextForRoute() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        RouteListingPreference.Item customSubtextItem1 =
                new RouteListingPreference.Item.Builder(getRouteUniqueId(ROUTE_ID1))
                        .setSubText(RouteListingPreference.Item.SUBTEXT_CUSTOM)
                        .setCustomSubtextMessage("message1")
                        .build();
        RouteListingPreference.Item customSubtextItem2 =
                new RouteListingPreference.Item.Builder(getRouteUniqueId(ROUTE_ID2))
                        .setSubText(RouteListingPreference.Item.SUBTEXT_CUSTOM)
                        .setCustomSubtextMessage("message2")
                        .build();
        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder()
                        .setItems(List.of(customSubtextItem1, customSubtextItem2))
                        .build();
        mRouter2.setRouteListingPreference(routeListingPreference);

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();
        BySelector subtextSelector1 = By.text("message1").pkg(SYSTEM_UI_PACKAGE);
        BySelector subtextSelector2 = By.text("message2").pkg(SYSTEM_UI_PACKAGE);
        UiObject2 route1NameObject =
                UiAutomatorUtils2.waitFindObject(
                        By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiObject2 route1TextContainer = route1NameObject.getParent();
        UiObject2 message1ForRoute1 = route1TextContainer.findObject(subtextSelector1);
        assertThat(message1ForRoute1).isNotNull();
        UiObject2 message2ForRoute1 = route1TextContainer.findObject(subtextSelector2);
        assertThat(message2ForRoute1).isNull();
        UiObject2 route2NameObject =
                UiAutomatorUtils2.waitFindObject(
                        By.text(ROUTE_NAME2).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiObject2 route2TextContainer = route2NameObject.getParent();
        UiObject2 message1ForRoute2 = route2TextContainer.findObject(subtextSelector1);
        assertThat(message1ForRoute2).isNull();
        UiObject2 message2ForRoute2 = route2TextContainer.findObject(subtextSelector2);
        assertThat(message2ForRoute2).isNotNull();
    }

    @Test
    public void selectionBehavior_noneBehavior_itemUnclickable() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        RouteListingPreference.Item noneSelectionItem =
                new RouteListingPreference.Item.Builder(getRouteUniqueId(ROUTE_ID1))
                        .setSelectionBehavior(RouteListingPreference.Item.SELECTION_BEHAVIOR_NONE)
                        .build();
        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder().setItems(List.of(noneSelectionItem)).build();
        mRouter2.setRouteListingPreference(routeListingPreference);
        mRouter2.showSystemOutputSwitcher();

        UiObject2 route =
                UiAutomatorUtils2.waitFindObject(
                        By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        assertThat(route.isClickable()).isFalse();
    }

    @Test
    public void selectionBehavior_transferBehavior_transferMediaOnClick() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1));
        registerRouteCallback(List.of(FEATURE_SAMPLE));
        mRouter2.registerTransferCallback(mExecutor, mTransferCallback);
        RouteListingPreference.Item transferSelectionItem =
                new RouteListingPreference.Item.Builder(getRouteUniqueId(ROUTE_ID1))
                        .setSelectionBehavior(
                                RouteListingPreference.Item.SELECTION_BEHAVIOR_TRANSFER)
                        .build();
        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder()
                        .setItems(List.of(transferSelectionItem))
                        .build();
        mRouter2.setRouteListingPreference(routeListingPreference);

        mRouter2.showSystemOutputSwitcher();
        clickRouteInDialog(ROUTE_NAME1);

        verify(mProviderProxy, timeout(TIMEOUT_MS))
                .onCreateSession(anyLong(), any(), eq(ROUTE_ID1), any());
        ArgumentCaptor<RoutingController> newController =
                ArgumentCaptor.forClass(RoutingController.class);
        verify(mTransferCallback, timeout(TIMEOUT_MS)).onTransfer(any(), newController.capture());
        assertThat(
                        newController.getValue().getSelectedRoutes().stream()
                                .map(MediaRoute2Info::getName))
                .containsExactly(ROUTE_NAME1);
    }

    @Test
    public void selectionBehavior_goToAppBehavior_launchAppIntentOnClick() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1));
        registerRouteCallback(List.of(FEATURE_SAMPLE));
        Instrumentation.ActivityMonitor activityMonitor =
                InstrumentationRegistry.getInstrumentation()
                        .addMonitor(MediaRouter2TestActivity.class.getName(), null, false);

        String uniqueId = getRouteUniqueId(ROUTE_ID1);
        RouteListingPreference.Item goToAppSelectionItem =
                new RouteListingPreference.Item.Builder(uniqueId)
                        .setSelectionBehavior(
                                RouteListingPreference.Item.SELECTION_BEHAVIOR_GO_TO_APP)
                        .build();
        ComponentName resolveComponent =
                new ComponentName(mContext, MediaRouter2TestActivity.class);
        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder()
                        .setItems(List.of(goToAppSelectionItem))
                        .setLinkedItemComponentName(resolveComponent)
                        .build();
        mRouter2.setRouteListingPreference(routeListingPreference);
        mRouter2.showSystemOutputSwitcher();
        clickRouteInDialog(ROUTE_NAME1);

        Activity activity = activityMonitor.waitForActivityWithTimeout(TIMEOUT_MS);
        Intent startedIntent = activity.getIntent();
        assertThat(startedIntent).isNotNull();
        assertThat(startedIntent.getAction()).isEqualTo(ACTION_TRANSFER_MEDIA);
        assertThat(startedIntent.getComponent()).isEqualTo(resolveComponent);
        String extraRoute = startedIntent.getStringExtra(RouteListingPreference.EXTRA_ROUTE_ID);
        assertThat(extraRoute).isEqualTo(uniqueId);
    }

    @Test
    public void showSystemOutputSwitcher_customOrdering_routesAreOrderedAccordingToPreference()
            throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));
        registerRouteCallback(List.of(FEATURE_SAMPLE));

        RouteListingPreference.Item item1 =
                new RouteListingPreference.Item.Builder(getRouteUniqueId(ROUTE_ID1)).build();
        RouteListingPreference.Item item2 =
                new RouteListingPreference.Item.Builder(getRouteUniqueId(ROUTE_ID2)).build();
        RouteListingPreference prefRoute2First =
                new RouteListingPreference.Builder()
                        .setUseSystemOrdering(false)
                        .setItems(List.of(item2, item1))
                        .build();
        mRouter2.setRouteListingPreference(prefRoute2First);
        mRouter2.showSystemOutputSwitcher();
        assertRoutesInOrderInUi(ROUTE_NAME2, ROUTE_NAME1);

        dismissSystemDialogs();
        UiAutomatorUtils2.waitUntilObjectGone(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE));

        RouteListingPreference prefRoute1First =
                new RouteListingPreference.Builder()
                        .setUseSystemOrdering(false)
                        .setItems(List.of(item1, item2))
                        .build();
        mRouter2.setRouteListingPreference(prefRoute1First);
        mRouter2.showSystemOutputSwitcher();
        assertRoutesInOrderInUi(ROUTE_NAME1, ROUTE_NAME2);
    }

    /**
     * Verifies that the first route appears before the second route in the logical UI tree, which
     * works regardless of whether the UI is a vertical list, horizontal row, or grid.
     */
    private void assertRoutesInOrderInUi(String firstRouteName, String secondRouteName)
            throws UiObjectNotFoundException {
        UiAutomatorUtils2.waitFindObject(
                By.text(firstRouteName).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(
                By.text(secondRouteName).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);

        Pattern routeNamesPattern =
                Pattern.compile(
                        "^("
                                + Pattern.quote(firstRouteName)
                                + "|"
                                + Pattern.quote(secondRouteName)
                                + ")$");

        // Fetch all text views on the screen in their logical accessibility tree order
        List<UiObject2> allTextViews =
                UiAutomatorUtils2.getUiDevice()
                        .findObjects(By.text(routeNamesPattern).pkg(SYSTEM_UI_PACKAGE));
        int firstRouteIndex = -1;
        int secondRouteIndex = -1;
        for (int i = 0; i < allTextViews.size(); i++) {
            String text = allTextViews.get(i).getText();
            if (firstRouteName.equals(text)) {
                firstRouteIndex = i;
            } else if (secondRouteName.equals(text)) {
                secondRouteIndex = i;
            }
        }

        assertThat(firstRouteIndex).isNotEqualTo(-1);
        assertThat(secondRouteIndex).isNotEqualTo(-1);
        assertThat(firstRouteIndex).isLessThan(secondRouteIndex);
    }

    /** Get a route unique ID, which includes the provider ID. */
    private String getRouteUniqueId(String id) {
        CompletableFuture<String> idFuture = new CompletableFuture<>();
        PollingCheck.waitFor(
                TIMEOUT_MS,
                () -> {
                    for (MediaRoute2Info route : mRouter2.getRoutes()) {
                        if (route.getOriginalId().equals(id)) {
                            idFuture.complete(route.getId());
                            return true;
                        }
                    }
                    return false;
                });
        return idFuture.resultNow();
    }

    private void registerRouteCallback(List<String> features) {
        mRouter2.registerRouteCallback(
                mExecutor,
                mEmptyRouteCallback,
                new RouteDiscoveryPreference.Builder(features, true).build());
    }

    private static void dismissSystemDialogs() {
        InstrumentationRegistry.getInstrumentation()
                .getContext()
                .sendBroadcast(
                        new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        UiAutomatorUtils2.waitUntilObjectGone(By.pkg(SYSTEM_UI_PACKAGE).focused(true));
    }

    private static void clickRouteInDialog(String routeName) throws UiObjectNotFoundException {
        UiObject2 route =
                UiAutomatorUtils2.waitFindObject(
                        By.text(routeName).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        route.click();
    }

    private static void clickThisDeviceRoute() throws UiObjectNotFoundException {
        UiObject2 route =
                UiAutomatorUtils2.waitFindObject(
                        By.textStartsWith(THIS_DEVICE_PREFIX).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        route.click();
    }

    // This finds an element with the given route name, and then tries to find an element with an
    // accessibility label of "Add device to group" as close as possible to it, and clicks on that.
    private static void clickAddDeviceToGroup(String routeName) throws UiObjectNotFoundException {
        UiObject2 route =
                UiAutomatorUtils2.waitFindObject(
                        By.text(routeName).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        clickNearestElementMatchingSelector(route, addDeviceToGroupSelector(routeName));
    }

    // Similar to the above, but searches for "Remove device from group".
    private static void clickRemoveDeviceFromGroup(String routeName)
            throws UiObjectNotFoundException {
        UiObject2 route =
                UiAutomatorUtils2.waitFindObject(
                        By.text(routeName).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        clickNearestElementMatchingSelector(route, removeDeviceFromGroupSelector(routeName));
    }

    private static BySelector addDeviceToGroupSelector(String routeName) {
        return By.descContains(
                        String.format(
                                "Add %s to group",
                                BidiFormatter.getInstance().unicodeWrap(routeName)))
                .pkg(SYSTEM_UI_PACKAGE);
    }

    private static BySelector removeDeviceFromGroupSelector(String routeName) {
        return By.descContains(
                        String.format(
                                "Remove %s from group",
                                BidiFormatter.getInstance().unicodeWrap(routeName)))
                .pkg(SYSTEM_UI_PACKAGE);
    }

    private static void clickNearestElementMatchingSelector(
            UiObject2 startNode, BySelector selector) {
        UiObject2[] foundElement = {null};
        UiAutomatorUtils2.assertWithUiDump(
                () ->
                        PollingCheck.waitFor(
                                TIMEOUT_MS,
                                () -> {
                                    foundElement[0] =
                                            findNearestElementMatchingSelector(startNode, selector);
                                    return foundElement[0] != null;
                                },
                                "Unable to find element matching selector : " + selector));
        foundElement[0].click();
    }

    private static UiObject2 findNearestElementMatchingSelector(
            UiObject2 startNode, BySelector selector) {
        while (startNode != null) {
            UiObject2 element = startNode.findObject(selector);
            if (element != null) {
                return element;
            }
            // Keep moving search start point farther up in the tree as long as we haven't found the
            // target.
            startNode = startNode.getParent();
        }
        return null;
    }

    private static void assertDialogShowsConnectionTo(String routeName) throws Exception {
        assertDialogShowsConnectionToRoute(By.text(routeName).pkg(SYSTEM_UI_PACKAGE));
    }

    private static void assertDialogShowsConnectionToThisDevice() throws Exception {
        assertDialogShowsConnectionToRoute(
                By.textStartsWith(THIS_DEVICE_PREFIX).pkg(SYSTEM_UI_PACKAGE));
    }

    private static void assertDialogShowsConnectionToRoute(BySelector selector) throws Exception {
        UiObject2 routeNode = UiAutomatorUtils2.waitFindObject(selector, TIMEOUT_MS);
        UiAutomatorUtils2.assertWithUiDump(
                () ->
                        PollingCheck.waitFor(
                                TIMEOUT_MS,
                                () -> hasConnectedState(routeNode),
                                "Timed out waiting for node to have state description '"
                                        + CONNECTED_STATE
                                        + "'"));
    }

    private static boolean hasConnectedState(UiObject2 routeNode) {
        UiObject2 obj = routeNode;

        // We're looking for a particular accessibility state description, either on the route node
        // itself or one of its parents.
        while (obj != null) {
            CharSequence stateDescription = obj.getAccessibilityNodeInfo().getStateDescription();
            if (stateDescription != null
                    && CONNECTED_STATE.equalsIgnoreCase(stateDescription.toString())) {
                return true;
            }
            obj = obj.getParent();
        }
        return false;
    }

    private void clickButtonWithLabel(String label) throws Exception {
        // In AOSP builds, button labels sometimes have all-caps text, so we do a case-insensitive
        // search here.
        UiObject2 button =
                UiAutomatorUtils2.waitFindObject(
                        By.text(Pattern.compile(label, Pattern.CASE_INSENSITIVE))
                                .pkg(SYSTEM_UI_PACKAGE),
                        TIMEOUT_MS);
        button.click();
    }

    private static Map<String, MediaRoute2Info> routeMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> map = new ArrayMap<>();
        routes.forEach(route -> map.put(route.getOriginalId(), route));
        return map;
    }

    private Map<String, MediaRoute2Info> selectedRoutes(RoutingController controller) {
        return routeMap(controller.getSelectedRoutes());
    }

    private Map<String, MediaRoute2Info> selectableRoutes(RoutingController controller) {
        return routeMap(controller.getSelectableRoutes());
    }

    private Map<String, MediaRoute2Info> deselectableRoutes(RoutingController controller) {
        return routeMap(controller.getDeselectableRoutes());
    }
}
