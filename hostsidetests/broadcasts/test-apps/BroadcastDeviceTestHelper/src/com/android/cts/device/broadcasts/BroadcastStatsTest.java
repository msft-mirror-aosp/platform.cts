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
package com.android.cts.device.broadcasts;

import static com.android.cts.host.broadcasts.Constants.TEST_BROADCAST_ACTION;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BroadcastStatsTest {
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testBroadcastSent() {
        final Intent intent = new Intent(TEST_BROADCAST_ACTION)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        mContext.sendBroadcast(intent);
    }
}
