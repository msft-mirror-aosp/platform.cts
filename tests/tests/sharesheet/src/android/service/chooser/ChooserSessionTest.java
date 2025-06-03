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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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

@RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_CHOOSER)
@RunWith(AndroidJUnit4.class)
public class ChooserSessionTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private ChooserManager mChooserManager;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = context.getPackageManager();
        assumeFalse(
                "Skip test: Device is a wearable, TV or Auto",
                pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                        || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        || pm.hasSystemFeature(PackageManager.FEATURE_WATCH));
        mChooserManager = context.getSystemService(ChooserManager.class);
        assertNotNull("ChooserManager is not available", mChooserManager);
    }

    /** Test that getToken method returns non-{@code null} reference. */
    @ApiTest(apis = {"android.service.chooser.ChooserManager#getToken"})
    @Test
    public void test_getTokenIsNotNull() {
        Context context = mock(Context.class);
        Intent chooserIntent = Intent.createChooser(new Intent(ACTION_SEND), null);
        ChooserSession testSubject = mChooserManager.startSession(context, chooserIntent);

        assertNotNull(testSubject.getToken());
    }

    /** Test the initial ChooserSession state. */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserManager#getState",
                "android.service.chooser.ChooserManager#STATE_INITIALIZED"
            })
    @Test
    public void test_initialState() {
        Context context = mock(Context.class);
        Intent chooserIntent = Intent.createChooser(new Intent(ACTION_SEND), null);
        ChooserSession testSubject = mChooserManager.startSession(context, chooserIntent);

        assertEquals(ChooserSession.STATE_INITIALIZED, testSubject.getState());
    }

    /** Test the initial ChooserSession state. */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserManager#getState",
                "android.service.chooser.ChooserManager#close",
                "android.service.chooser.ChooserManager#STATE_CLOSED"
            })
    @Test
    public void test_closedSessionState() {
        Context context = mock(Context.class);
        Intent chooserIntent = Intent.createChooser(new Intent(ACTION_SEND), null);
        ChooserSession testSubject = mChooserManager.startSession(context, chooserIntent);

        testSubject.close();

        assertEquals(ChooserSession.STATE_CLOSED, testSubject.getState());
    }
}
