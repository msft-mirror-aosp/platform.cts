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

package android.service.chooser;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_CHOOSER)
@RunWith(AndroidJUnit4.class)
public class ChooserManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private ChooserManager mChooserManager;

    @Before
    public void init() {
        mChooserManager =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(ChooserManager.class);
    }

    /** Test that only Intent#ACTION_CHOOSER intents is accepted by the startSession method. */
    @ApiTest(apis = {"android.service.chooser.ChooserManager#startSession"})
    @Test(expected = IllegalArgumentException.class)
    public void test_startSession_nonChooserActionIntent_exceptionThrown() {
        mChooserManager.startSession(mock(Context.class), new Intent(ACTION_SEND));
    }

    /** Test that startSession method clears task-related intent flags. */
    @ApiTest(apis = {"android.service.chooser.ChooserManager#startSession"})
    @Test
    public void test_startSession_intentWithFlags_intentProperlyConfigured() {
        Context context = mock(Context.class);
        int flags =
                FLAG_ACTIVITY_SINGLE_TOP
                        | FLAG_ACTIVITY_NEW_TASK
                        | FLAG_ACTIVITY_CLEAR_TASK
                        | FLAG_ACTIVITY_CLEAR_TOP
                        | FLAG_ACTIVITY_MULTIPLE_TASK
                        | FLAG_ACTIVITY_REORDER_TO_FRONT
                        | FLAG_ACTIVITY_TASK_ON_HOME
                        | FLAG_ACTIVITY_LAUNCH_ADJACENT;
        Intent chooserIntent = Intent.createChooser(new Intent(ACTION_SEND), null);
        chooserIntent.setFlags(flags);

        mChooserManager.startSession(context, chooserIntent);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        Mockito.verify(context, times(1)).startActivity(intentCaptor.capture(), any());

        assertEquals(0, intentCaptor.getValue().getFlags() & flags);
    }

    /**
     * Test that getSession returns correct session instance for an active session token and {@code
     * null} when the session is closed.
     */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserManager#startSession",
                "android.service.chooser.ChooserManager#getSession"
            })
    @Test
    public void test_getSession_nullForClosedSession() {
        Context context = mock(Context.class);
        Intent chooserIntent = Intent.createChooser(new Intent(ACTION_SEND), null);

        ChooserSession session = mChooserManager.startSession(context, chooserIntent);

        assertEquals(session, mChooserManager.getSession(session.getToken()));

        session.close();

        assertNull(mChooserManager.getSession(session.getToken()));
    }
}
