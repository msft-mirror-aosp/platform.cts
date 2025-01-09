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
import static android.media.router.cts.SystemMediaRoutingProviderService.ROUTE_ID_ONLY_SYSTEM_AUDIO;

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
import android.util.ArraySet;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "The system must be able to bind to the route provider service.")
@LargeTest
@FrameworkSpecificTest
public class SystemMediaRoutingTest {

    /** Time to wait for routes to appear before giving up. */
    private static final int TIMEOUT_MS = 5_000;

    /* The volume for the tone to generate in order to test audio capturing. */
    private static final int VOLUME_TONE = 100;
    private static final int EXPECTED_NOISY_BYTE_COUNT = 20_000;

    @Rule
    public final ResourceReleaser mResourceReleaser = new ResourceReleaser(/* useStack= */ true);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // Required by Bedstead.
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    Context mContext;
    private MediaRouter2 mSelfProxyRoute;
    private SystemMediaRoutingProviderService mService;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // TODO:  b/362507305 - Consider moving the enabling and disabling of the provider service
        // to a before class, so that it's done only once per test class run. That should marginally
        // speed up tests.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(
                Manifest.permission.MEDIA_ROUTING_CONTROL,
                Manifest.permission.MODIFY_AUDIO_ROUTING);
        mResourceReleaser.add(uiAutomation::dropShellPermissionIdentity);
        mSelfProxyRoute = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        PackageManager pm = mContext.getPackageManager();
        ComponentName serviceComponent =
                new ComponentName(mContext, SystemMediaRoutingProviderService.class);

        // MODIFY_AUDIO_ROUTING is a signature permission, so the service will not be deemed
        // authorized for system media routing at runtime by adopting the shell permission identity.
        // By enabling the service after getting MODIFY_AUDIO_ROUTING we ensure the proxy is
        // initialized with the right permissions in place.
        pm.setComponentEnabledSetting(
                serviceComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                /* flags= */ PackageManager.DONT_KILL_APP);
        mResourceReleaser.add(
                () ->
                        pm.setComponentEnabledSetting(
                                serviceComponent,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                /* flags= */ PackageManager.DONT_KILL_APP));

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

    // Tests.

    @RequiresFlagsEnabled({FLAG_ENABLE_MIRRORING_IN_MEDIA_ROUTER_2})
    @Test
    public void getRoutes_withNoDiscoveryPreference_returnsSystemMediaRoutesOnly() {
        var routes =
                getSystemRoutesWithNames(
                        ROUTE_ID_ONLY_SYSTEM_AUDIO,
                        ROUTE_ID_ONLY_REMOTE,
                        ROUTE_ID_BOTH_SYSTEM_AND_REMOTE);
        var foundRouteNames =
                routes.stream().map(MediaRoute2Info::getName).collect(Collectors.toSet());
        // We expect system media routes to show up, and we expect the remote-only route not to show
        // up because we haven't set any route discovery preference.
        assertThat(foundRouteNames)
                .containsAtLeast(ROUTE_ID_ONLY_SYSTEM_AUDIO, ROUTE_ID_BOTH_SYSTEM_AND_REMOTE);
        assertThat(foundRouteNames).doesNotContain(ROUTE_ID_ONLY_REMOTE);
    }

    @RequiresFlagsEnabled({FLAG_ENABLE_MIRRORING_IN_MEDIA_ROUTER_2})
    @Test
    public void transferTo_systemMediaProviderServiceRoute_readsAudioAsExpected() {
        var route =
                getSystemRoutesWithNames(ROUTE_ID_ONLY_SYSTEM_AUDIO).stream()
                        .filter(it -> it.getName().toString().equals(ROUTE_ID_ONLY_SYSTEM_AUDIO))
                        .findAny()
                        .get();
        var previouslySelectedRoute =
                mSelfProxyRoute.getSystemController().getSelectedRoutes().getFirst();
        mSelfProxyRoute.transferTo(route);
        // Even though this test transfers to previouslySelectedRoute later, we still schedule a
        // transfer back to the original route so that the routing gets reset, even if the test
        // fails later.
        mResourceReleaser.add(() -> mSelfProxyRoute.transferTo(previouslySelectedRoute));
        waitForSelectedRouteWithName(/* name= */ ROUTE_ID_ONLY_SYSTEM_AUDIO);

        var toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_TONE);
        mResourceReleaser.add(toneGenerator::release);
        toneGenerator.startTone(ToneGenerator.TONE_DTMF_0);
        new PollingCheck(TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mService.getNoisyBytesCount() > EXPECTED_NOISY_BYTE_COUNT;
            }
        }.run();

        mSelfProxyRoute.transferTo(previouslySelectedRoute);
        waitForSelectedRouteWithName(previouslySelectedRoute.getName().toString());

        assertThat(mService.getSelectedRouteOriginalId()).isNull();
        assertThat(mService.getNoisyBytesCount()).isEqualTo(0);
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
        new PollingCheck(TIMEOUT_MS) {
            @Override
            protected boolean check() {
                var controller = mSelfProxyRoute.getSystemController();
                return controller.getSelectedRoutes().getFirst().getName().toString().equals(name);
            }
        }.run();
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
}
