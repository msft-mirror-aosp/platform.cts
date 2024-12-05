/*
 * Copyright 2024 The Android Open Source Project
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

package android.media.mediaquality.cts;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.media.quality.MediaQualityManager;
import android.media.tv.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaQualityTest {
    private MediaQualityManager mManager;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();

        mManager = context.getSystemService(MediaQualityManager.class);
        if (mManager == null || !isSupported()) {
            return;
        }
    }

    @After
    public void tearDown() {
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetAvailablePictureProfiles() throws Exception {
        mManager.getAvailablePictureProfiles();
    }

    private boolean isSupported() {
        return mManager.isSupported();
    }
}
