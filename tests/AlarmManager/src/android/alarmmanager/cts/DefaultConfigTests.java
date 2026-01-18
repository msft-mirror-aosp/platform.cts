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

package android.alarmmanager.cts;

import static org.junit.Assert.assertEquals;

import android.app.AlarmManager;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for default DeviceConfig values. */
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class DefaultConfigTests {
    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private final AlarmManager mAm = mContext.getSystemService(AlarmManager.class);

    @Test
    @Ignore("b/473898720")
    public void testPrioritizedAlarmDelayIsOneMinute() {
        final long expectedDelay = TimeUnit.MINUTES.toMillis(1);
        final long delay = mAm.getPrioritizedAlarmDelay();
        assertEquals("Prioritized alarm delay should be one minute", expectedDelay, delay);
    }
}
