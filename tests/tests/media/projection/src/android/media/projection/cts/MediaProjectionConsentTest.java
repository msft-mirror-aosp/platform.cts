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

package android.media.projection.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.media.cts.MediaProjectionRule;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjectionManager} consent flow.
 *
 * <p>Run with: atest CtsMediaProjectionTestCases:MediaProjectionManagerTest
 */
@FrameworkSpecificTest
public class MediaProjectionConsentTest {

    // While the test itself does not check the consent flow - it relies on the test rule to click
    // through the consent flow to ensure it is working as expected.
    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    private int mTimeoutMs;

    @Before
    public void setUp() throws Exception {
        mMediaProjectionRule.enableConsentFlow();
        mTimeoutMs = 1000 * HW_TIMEOUT_MULTIPLIER;
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection() throws Exception {
        // Launch the activity.
        MediaProjection mediaProjection =
                mMediaProjectionRule.startMediaProjection(
                        MediaProjectionConfig.createConfigForDefaultDisplay());
        // Ensure MediaProjection instance is valid.
        assertThat(mediaProjection).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.media.projection.MediaProjection#registerCallback",
                "android.media.projection.MediaProjection#stop",
                "android.media.projection.MediaProjection.Callback#onStop"
            })
    @Test
    public void testCallbackOnStop() throws Exception {
        MediaProjection mediaProjection = mMediaProjectionRule.startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latch.countDown();
                    }
                });
        mMediaProjectionRule.createVirtualDisplay();
        mediaProjection.stop();

        assertWithMessage("Could not stop the MediaProjection in " + mTimeoutMs + "ms")
                .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS))
                .isTrue();
    }
}
