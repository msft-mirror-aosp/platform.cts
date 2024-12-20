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

package android.server.wm.jetpack.embedding;

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getWindowExtensions;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assumeActivityEmbeddingSupportedDevice;

import android.server.wm.UiDeviceUtils;
import android.server.wm.jetpack.extensions.util.TestValueCountConsumer;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;

import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitInfo;

import org.junit.After;
import org.junit.Before;

import java.util.Collections;
import java.util.List;

/**
 * Base test class for the {@link androidx.window.extensions} implementation provided on the device
 * (and only if one is available) for the Activity Embedding functionality.
 */
public class ActivityEmbeddingTestBase extends WindowManagerJetpackTestBase {

    protected ActivityEmbeddingComponent mActivityEmbeddingComponent;
    protected TestValueCountConsumer<List<SplitInfo>> mSplitInfoConsumer;
    protected ReportedDisplayMetrics mReportedDisplayMetrics;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeActivityEmbeddingSupportedDevice();
        mReportedDisplayMetrics = ReportedDisplayMetrics.getDisplayMetrics(getMainDisplayId());

        mActivityEmbeddingComponent = getWindowExtensions().getActivityEmbeddingComponent();

        mSplitInfoConsumer = new TestValueCountConsumer<>();
        mActivityEmbeddingComponent.setSplitInfoCallback(mSplitInfoConsumer);
        // The splitInfoCallback will be triggered once upon register, so clear the queue before
        // test starts.
        mSplitInfoConsumer.clearQueue();

        UiDeviceUtils.pressWakeupButton();
        UiDeviceUtils.pressUnlockButton();
    }

    @Override
    @After
    public void tearDown() throws Throwable {
        super.tearDown();
        mReportedDisplayMetrics.restoreDisplayMetrics();
        if (mActivityEmbeddingComponent != null) {
            mActivityEmbeddingComponent.setEmbeddingRules(Collections.emptySet());
            mActivityEmbeddingComponent.clearActivityStackAttributesCalculator();
            mActivityEmbeddingComponent.clearEmbeddedActivityWindowInfoCallback();
            mActivityEmbeddingComponent.clearSplitAttributesCalculator();
            mActivityEmbeddingComponent.clearSplitInfoCallback();
        }
    }
}
