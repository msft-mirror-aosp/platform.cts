/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.eventlib.premade;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.eventlib.EventLogs;
import com.android.eventlib.events.activities.ActivityCreatedEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This test assumes that EventLibAppComponentFactory is in the AndroidManifest.xml of the
 * instrumented test process.
 */
@RunWith(JUnit4.class)
public class EventLibAppComponentFactoryTest {

    // This must exist as an <activity> in AndroidManifest.xml
    private static final String DECLARED_ACTIVITY_WITH_NO_CLASS
            = "com.android.generatedEventLibActivity";

    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Test
    public void startActivity_activityDoesNotExist_startsLoggingActivity() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(CONTEXT.getPackageName(),
                DECLARED_ACTIVITY_WITH_NO_CLASS));
        CONTEXT.startActivity(intent);

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                .whereActivity().className().isEqualTo(DECLARED_ACTIVITY_WITH_NO_CLASS);
        assertThat(eventLogs.poll()).isNotNull();
    }

}
