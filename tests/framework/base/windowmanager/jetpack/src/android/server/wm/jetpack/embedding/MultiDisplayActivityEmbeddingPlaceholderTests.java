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

package android.server.wm.jetpack.embedding;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;

import androidx.annotation.Nullable;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitPlaceholderRule;

import org.junit.After;
import org.junit.Before;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the placeholders functionality within Activity Embedding on secondary
 * display. An activity can provide a {@link SplitPlaceholderRule} to the
 * {@link ActivityEmbeddingComponent} which will enable the activity to launch directly into a split
 * with the placeholder activity it is configured to launch with.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:MultiDisplayActivityEmbeddingPlaceholderTests
 */
@Presubmit
public class MultiDisplayActivityEmbeddingPlaceholderTests
        extends ActivityEmbeddingPlaceholderTests {

    private final MultiDisplayTestHelper mTestHelper =
            new MultiDisplayTestHelper(new ActivityManagerTestBase.VirtualDisplaySession());

    @Before
    @Override
    public void setUp() throws Exception {
        assumeTrue(supportsMultiDisplay());

        mTestHelper.setUpTestDisplay();
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        mTestHelper.releaseDisplay();
    }

    @Override
    @Nullable
    public Integer getLaunchingDisplayId() {
        return mTestHelper.getSecondaryDisplayId();
    }
}
