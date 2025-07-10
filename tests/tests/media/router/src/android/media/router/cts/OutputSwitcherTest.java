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

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.router.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.router.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID4_TO_SELECT_AND_DESELECT;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID5_TO_TRANSFER_TO;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID9_REMOTE;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID_SPECIAL_FEATURE;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME2;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME4;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME5;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME9;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME_SPECIAL_FEATURE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.RoutingController;
import android.media.RouteDiscoveryPreference;
import android.media.session.MediaSession;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.BidiFormatter;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.UiAutomatorUtils2;
import com.android.media.flags.Flags;

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

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
    private static final int TIMEOUT_MS = 5000;

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
    @Mock private MediaRouter2.ControllerCallback mControllerCallback;

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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
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
        clickNearestElementWithDescription(route,
               String.format("Add %s to group",
                        BidiFormatter.getInstance().unicodeWrap(routeName)));
    }

    // Similar to the above, but searches for "Remove device from group".
    private static void clickRemoveDeviceFromGroup(String routeName)
            throws UiObjectNotFoundException {
        UiObject2 route =
                UiAutomatorUtils2.waitFindObject(
                        By.text(routeName).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        clickNearestElementWithDescription(route,
                String.format("Remove %s from group",
                        BidiFormatter.getInstance().unicodeWrap(routeName)));
    }

    private static void clickNearestElementWithDescription(
            UiObject2 startNode, String description) {
        while (startNode != null) {
            UiObject2 element = startNode.findObject(By.descContains(description));
            if (element != null) {
                element.click();
                return;
            }
            // Keep moving search start point farther up in the tree as long as we haven't found the
            // target.
            startNode = startNode.getParent();
        }

        UiAutomatorUtils2.assertWithUiDump(
                () -> {
                    throw new RuntimeException(
                            "Unable to find element with description: " + description);
                });
    }

    private static void assertDialogShowsConnectionTo(String routeName) throws Exception {
        assumeTrue(Flags.enableOutputSwitcherRedesign());
        assertConnectedState(
                UiAutomatorUtils2.waitFindObject(
                        By.text(routeName).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS));
    }

    private static void assertDialogShowsConnectionToThisDevice() throws Exception {
        assumeTrue(Flags.enableOutputSwitcherRedesign());
        assertConnectedState(
                UiAutomatorUtils2.waitFindObject(
                        By.textStartsWith(THIS_DEVICE_PREFIX).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS));
    }

    private static void assertConnectedState(UiObject2 routeNode) {
        UiObject2 obj = routeNode;

        // We're looking for a particular accessibility state description, either on the route node
        // itself or one of its parents.
        while (obj != null) {
            CharSequence stateDescription = obj.getAccessibilityNodeInfo().getStateDescription();
            if (stateDescription != null
                    && CONNECTED_STATE.equalsIgnoreCase(stateDescription.toString())) {
                return;
            }
            obj = obj.getParent();
        }

        UiAutomatorUtils2.assertWithUiDump(
                () -> {
                    throw new RuntimeException(
                            "Failed to find node with accessibility state description: "
                                    + CONNECTED_STATE);
                });
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
}
