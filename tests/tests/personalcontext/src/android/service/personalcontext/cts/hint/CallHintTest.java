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

package android.service.personalcontext.cts.hint;

import static com.google.common.truth.Truth.assertThat;

import android.app.Person;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.CallHint;
import android.service.personalcontext.hint.ContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/** Build/Install/Run: atest CtsPersonalContextTestCases:CallHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class CallHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.CallHint.Builder#build",
                "android.service.personalcontext.hint.CallHint#createHintFromBundle",
                "android.service.personalcontext.hint.CallHint#getParticipants",
            })
    @Test
    public void testPhoneCallHint_participants() {
        final String address = "tel:123-456-7890";
        final Set<Person> participants =
                Set.of(new Person.Builder().setName("Tom Brown").setUri(address).build());
        final CallHint hint = new CallHint.Builder(CallHint.MODALITY_AUDIO, participants).build();

        final CallHint outputHint = assertBundleUnbundle(hint);
        assertThat(outputHint.getParticipants()).isEqualTo(participants);

        final Person participant = outputHint.getParticipants().stream().findFirst().get();
        assertThat(participant.getUri()).isEqualTo(address);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.CallHint.Builder#build",
                "android.service.personalcontext.hint.CallHint#createHintFromBundle",
                "android.service.personalcontext.hint.CallHint#getModality",
            })
    @Test
    public void testPhoneCallHint_modality() {
        final int modality = CallHint.MODALITY_VIDEO;
        final Set<Person> participants = Set.of(new Person.Builder().setName("Tom Brown").build());
        final CallHint hint = new CallHint.Builder(modality, participants).build();

        final CallHint outputHint = assertBundleUnbundle(hint);
        assertThat(outputHint.getModality()).isEqualTo(modality);
    }

    private CallHint assertBundleUnbundle(ContextHint hint) {
        final ContextHint outputHint = ContextHint.createHintFromBundle(hint.toBundle());
        assertThat(outputHint).isNotNull();
        assertThat(outputHint).isInstanceOf(CallHint.class);
        assertThat(outputHint).isEqualTo(hint);
        return (CallHint) outputHint;
    }
}
