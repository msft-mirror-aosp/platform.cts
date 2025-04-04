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
import static android.media.router.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.router.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID5_TO_TRANSFER_TO;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID_SPECIAL_FEATURE;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME2;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME5;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME_SPECIAL_FEATURE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.RoutingController;
import android.media.RouteDiscoveryPreference;
import android.platform.test.annotations.AppModeFull;

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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    // This comes from the value of com.android.systemui.R.string.accessibility_cast_name
    // (frameworks/base/packages/SystemUI/res/values/strings.xml)
    private static final String CONNECTED_STRING_FORMAT = "Connected to %s";

    // Required by Bedstead.
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public StubMediaRoute2ProviderService.Setup mProviderSetup =
            new StubMediaRoute2ProviderService.Setup();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private StubMediaRoute2ProviderService.Proxy mProviderProxy;

    @Mock private MediaRouter2.TransferCallback mTransferCallback;

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

    private static void assertDialogShowsConnectionTo(String routeName) throws Exception {
        // The redesigned OutputSwitcher is missing the accessibility text we rely on for this to
        // function properly. b/408304744
        assumeFalse(Flags.enableOutputSwitcherRedesign());

        assertThat(
                        UiAutomatorUtils2.waitFindObject(
                                By.descContains(String.format(CONNECTED_STRING_FORMAT, routeName))))
                .isNotNull();
    }
}
