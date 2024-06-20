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

package android.nfc.cts.interactive;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.nfc.cts.R;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.ActAndWaitStep;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Reading and writing NFC tags. Asks user to write data to tag. Asks user to scan tag to verify
 * data was properly written and read back. Manual steps = scan tag. Scan tag again
 */
@RunWith(BedsteadJUnit4.class)
public class TagVerifierTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();


    @Before
    public void setUp() {
        Intent intent = new Intent().setPackage(sContext.getPackageName())
                .setClassName(sContext.getPackageName(), TagVerifierActivity.class.getName())
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);

        Activity activity = sInstrumentation.startActivitySync(intent);
        sInstrumentation.runOnMainSync(
                () -> sInstrumentation.callActivityOnResume(activity));
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "User must place tag against phone")
    public void verifyTag() throws Exception {
        Step.execute(WriteTagStep.class);
        Step.execute(VerifyTagStep.class);
        assertThat(TagVerifierActivity.getResult()).isNotNull();
        assertThat(TagVerifierActivity.getResult().isMatch()).isTrue();
    }


    @NotFullyAutomated(reason = "Requires user to place tag")
    public static final class WriteTagStep extends ActAndWaitStep {
        public WriteTagStep() {
            super(sContext.getResources().getString(R.string.nfc_scan_tag),
                    TagVerifierActivity::getWriteComplete);
        }

    }

    @NotFullyAutomated(reason = "Requires user to place tag")
    public static final class VerifyTagStep extends ActAndWaitStep {
        public VerifyTagStep() {
            super(sContext.getResources().getString(R.string.nfc_scan_tag_again),
                    TagVerifierActivity::getVerifyComplete);
        }
    }

}
