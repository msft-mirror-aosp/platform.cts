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

package android.media.router.cts;

import static android.media.router.cts.SystemMediaRoutingProviderService.ROUTE_ID_BOTH_SYSTEM_AND_REMOTE;
import static android.media.router.cts.SystemMediaRoutingProviderService.ROUTE_ID_ONLY_REMOTE;
import static android.media.router.cts.SystemMediaRoutingProviderService.ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1;
import static android.media.router.cts.SystemMediaRoutingProviderService.ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_2;

import static com.android.media.flags.Flags.FLAG_ENABLE_MIRRORING_IN_MEDIA_ROUTER_2;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.ToneGenerator;
import android.media.cts.ResourceReleaser;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "The system must be able to bind to the route provider service.")
@LargeTest
@FrameworkSpecificTest
public class SystemMediaRoutingTest {

    /** Time to wait for routes to appear before giving up. */
    private static final int TIMEOUT_MS = 5_000;

    /* The volume for the tone to generate in order to test audio capturing. */
    private static final int VOLUME_TONE = 100;
    private static final int EXPECTED_NOISY_BYTE_COUNT = 20_000;
    private static ComponentName sServiceComponentName;

    @Rule
    public final ResourceReleaser mResourceReleaser = new ResourceReleaser(/* useStack= */ true);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private MediaRouter2 mSelfProxyRoute;
    private SystemMediaRoutingProviderService mService;

    @BeforeClass
    public static void setUpBeforeClass() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(
                Manifest.permission.MEDIA_ROUTING_CONTROL,
                Manifest.permission.MODIFY_AUDIO_ROUTING);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager pm = context.getPackageManager();
        sServiceComponentName = new ComponentName(context, SystemMediaRoutingProviderService.class);

        // MODIFY_AUDIO_ROUTING is a signature permission, so the service will not be deemed
        // authorized for system media routing at runtime by adopting the shell permission identity.
        // By enabling the service after getting MODIFY_AUDIO_ROUTING we ensure the proxy is
        // initialized with the right permissions in place. We also do it once for the entire class
        // to accelerate test execution, as this is a slow operation.
        pm.setComponentEnabledSetting(
                sServiceComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                /* flags= */ PackageManager.DONT_KILL_APP);
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // We need to request the permissions again because the check flags rule drops the shell
        // permissions identity before setup, clearing the permissions from before class.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MEDIA_ROUTING_CONTROL,
                        Manifest.permission.MODIFY_AUDIO_ROUTING);
        mSelfProxyRoute = MediaRouter2.getInstance(context, context.getPackageName());
        triggerScan();
        mService =
                PollingCheck.waitFor(
                        TIMEOUT_MS,
                        /* supplier= */ SystemMediaRoutingProviderService::getInstance,
                        /* condition= */ Objects::nonNull);
        assertWithMessage("Service failed to launch after " + TIMEOUT_MS + " milliseconds.")
                .that(mService)
                .isNotNull();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        var pm = InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        pm.setComponentEnabledSetting(
                sServiceComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                /* flags= */ PackageManager.DONT_KILL_APP);
    }

    // Tests.

    @RequiresFlagsEnabled({FLAG_ENABLE_MIRRORING_IN_MEDIA_ROUTER_2})
    @Test
    public void getRoutes_withNoDiscoveryPreference_returnsSystemMediaRoutesOnly() {
        var routes =
                getSystemRoutesWithNames(
                        ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1,
                        ROUTE_ID_ONLY_REMOTE,
                        ROUTE_ID_BOTH_SYSTEM_AND_REMOTE);
        var foundRouteNames =
                routes.stream().map(MediaRoute2Info::getName).collect(Collectors.toSet());
        // We expect system media routes to show up, and we expect the remote-only route not to show
        // up because we haven't set any route discovery preference.
        assertThat(foundRouteNames)
                .containsAtLeast(
                        ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1, ROUTE_ID_BOTH_SYSTEM_AND_REMOTE);
        assertThat(foundRouteNames).doesNotContain(ROUTE_ID_ONLY_REMOTE);
    }

    @RequiresFlagsEnabled({FLAG_ENABLE_MIRRORING_IN_MEDIA_ROUTER_2})
    @Test
    public void transferTo_systemMediaProviderServiceRoute_readsAudioAsExpected() {
        var route = waitForTransferableRouteWithName(ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1);
        var previouslySelectedRoute =
                mSelfProxyRoute.getSystemController().getSelectedRoutes().getFirst();

        // Even though this test transfers to previouslySelectedRoute later, we still schedule a
        // transfer back to the original route so that the routing gets reset, even if the test
        // fails later.
        mResourceReleaser.add(() -> transferAndWaitForSessionUpdate(previouslySelectedRoute));
        transferAndWaitForSessionUpdate(route);

        var toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_TONE);
        mResourceReleaser.add(toneGenerator::release);
        toneGenerator.startTone(ToneGenerator.TONE_DTMF_0);
        new PollingCheck(TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mService.getNoisyBytesCount() > EXPECTED_NOISY_BYTE_COUNT;
            }
        }.run();

        transferAndWaitForSessionUpdate(previouslySelectedRoute);

        assertThat(mService.getSelectedRouteOriginalId()).isNull();
        assertThat(mService.getNoisyBytesCount()).isEqualTo(0);
    }

    @RequiresFlagsEnabled({FLAG_ENABLE_MIRRORING_IN_MEDIA_ROUTER_2})
    @Test
    public void transferTo_withinRoutingSession_updatesRoutingSession() {
        var firstTargetRoute =
                waitForTransferableRouteWithName(ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1);
        var previouslySelectedRoute =
                mSelfProxyRoute.getSystemController().getSelectedRoutes().getFirst();
        mResourceReleaser.add(() -> transferAndWaitForSessionUpdate(previouslySelectedRoute));
        transferAndWaitForSessionUpdate(firstTargetRoute);

        assertThat(mService.getSelectedRouteOriginalId())
                .isEqualTo(ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1);

        // We choose a second route to transfer within the mirroring routing session, in which case
        // no session is created or released. The transfer happens within the same session.
        var secondTargetRoute =
                waitForTransferableRouteWithName(ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_2);

        // We don't need to wait for the non-transferable route, because we know at this point the
        // routing session has updated.
        assertThat(
                        getSystemControllerRouteWithName(
                                ROUTE_ID_BOTH_SYSTEM_AND_REMOTE,
                                MediaRouter2.RoutingController::getTransferableRoutes))
                .isEmpty();

        transferAndWaitForSessionUpdate(secondTargetRoute);

        assertThat(mService.getSelectedRouteOriginalId())
                .isEqualTo(ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_2);
    }

    /**
     * Transfers to the given route and waits for the routing session to be updated with the new
     * selected route.
     */
    private void transferAndWaitForSessionUpdate(MediaRoute2Info previouslySelectedRoute) {
        mSelfProxyRoute.transferTo(previouslySelectedRoute);
        waitForSelectedRouteWithName(previouslySelectedRoute.getName().toString());
    }

    // Internal methods.

    /** Triggers an active scan and schedules its cancellation after the end of the test. */
    private void triggerScan() {
        MediaRouter2.ScanToken scanToken =
                mSelfProxyRoute.requestScan(
                        new MediaRouter2.ScanRequest.Builder().setScreenOffScan(true).build());
        mResourceReleaser.add(() -> mSelfProxyRoute.cancelScanRequest(scanToken));
    }

    /** Waits for the selected system route to have the given {@code name}. */
    private void waitForSelectedRouteWithName(String name) {
        var result =
                waitForRouteInController(name, MediaRouter2.RoutingController::getSelectedRoutes);
        assertWithMessage("Did not find selected route: " + name).that(result).isPresent();
    }

    /** Waits for the selected system route to have the given {@code name}. */
    private MediaRoute2Info waitForTransferableRouteWithName(String name) {
        var result =
                waitForRouteInController(
                        name, MediaRouter2.RoutingController::getTransferableRoutes);
        assertWithMessage("Did not find transferable route: " + name).that(result).isPresent();
        return result.get();
    }

    /**
     * Waits for {@link #TIMEOUT_MS} until {@link #getSystemControllerRouteWithName} returns a route
     * with the given name, and returns is. Otherwise, returns an empty optional.
     */
    private Optional<MediaRoute2Info> waitForRouteInController(
            String name, RouteRetriever routeRetriever) {
        Supplier<Optional<MediaRoute2Info>> supplier =
                () -> getSystemControllerRouteWithName(name, routeRetriever);
        return PollingCheck.waitFor(TIMEOUT_MS, supplier, /* condition= */ Optional::isPresent);
    }

    /**
     * Returns a route (using the given {@link RouteRetriever}) from the system controller that
     * matches the given name.
     */
    private Optional<MediaRoute2Info> getSystemControllerRouteWithName(
            String name, RouteRetriever routeRetriever) {
        return routeRetriever.getRoutesFrom(mSelfProxyRoute.getSystemController()).stream()
                .filter(route -> TextUtils.equals(route.getName(), name))
                .findFirst();
    }

    /**
     * Returns a set of all routes with the given names.
     *
     * <p>This method returns once one or more routes are found for each given name, or once {@link
     * #TIMEOUT_MS} has elapsed.
     *
     * <p>Note that we use the names instead of the ids, because the system route provider will
     * derive an id from the {@link android.media.MediaRoute2ProviderService service-assigned} id,
     * but that derivation is an implementation detail on which we shouldn't depend.
     */
    private Set<MediaRoute2Info> getSystemRoutesWithNames(String... names) {
        Set<String> nameSet = new ArraySet<>(names);
        return PollingCheck.waitFor(
                TIMEOUT_MS,
                /* supplier= */ () -> {
                    var result = new ArraySet<MediaRoute2Info>();
                    var systemController = mSelfProxyRoute.getSystemController();
                    result.addAll(systemController.getSelectableRoutes());
                    result.addAll(systemController.getTransferableRoutes());
                    return result;
                },
                /* condition= */ it ->
                        it.stream()
                                .map(MediaRoute2Info::getName)
                                .collect(Collectors.toSet())
                                .containsAll(nameSet));
    }

    /** Convenience interface for retrieving routes from a routing controller. */
    private interface RouteRetriever {
        List<MediaRoute2Info> getRoutesFrom(MediaRouter2.RoutingController controller);
    }
}
