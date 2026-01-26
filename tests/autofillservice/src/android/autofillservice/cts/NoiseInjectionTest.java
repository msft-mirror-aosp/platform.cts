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

package android.autofillservice.cts;

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure;
import android.autofillservice.cts.activities.CustomPasswordViewLoginActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.service.autofill.Flags;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillNoiseInjectedData;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

@AppModeFull(reason = "AutoFillServiceTestCase.ManualActivityLaunch requires full mode.")
public class NoiseInjectionTest extends AutoFillServiceTestCase.ManualActivityLaunch {

    private static final int FIXED_LENGTH_BYTES = 32;
    private static final int ODD_BITS_MASK = 0xAA;
    private static final int EVEN_BITS_MASK = 0x55;

    private final CannedFillResponse.Builder mLoginResponseBuilder =
            new CannedFillResponse.Builder()
                    .addDataset(
                            new CannedFillResponse.CannedDataset.Builder()
                                    .setField(ID_USERNAME, "dude")
                                    .setField(ID_PASSWORD, "sweet")
                                    .setPresentation(createPresentation("Dropdown Presentation"))
                                    .build());

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STRING_REBUILD_API)
    public void testGetNoiseInjectionMasterSeed_returnsNonNull() throws Exception {
        enableService();
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final AutofillManager afm = context.getSystemService(AutofillManager.class);
        assertThat(afm).isNotNull();
        assertThat(afm.getNoiseInjectionMasterSeed()).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STRING_REBUILD_API)
    public void testNonSensitiveText_noNoiseInFillResponse() throws Exception {
        enableService();

        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start the activity
        startCustomPasswordViewLoginActivity();
        mUiBot.waitForIdle();

        // Trigger autofill
        mUiBot.selectByRelativeId(Helper.ID_USERNAME);
        mUiBot.waitForIdle();

        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        final AssistStructure structure = request.structure;

        // The username_label node in the request should not have noise, since its text is loaded
        // for xml(hence not sensitive).
        assertThat(structure).isNotNull();
        final AssistStructure.ViewNode usernameLabelNode =
                Helper.findNodeByResourceId(structure, "username_label");
        assertThat(usernameLabelNode.getAutofillNoiseInjectedData()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STRING_REBUILD_API)
    public void testEditText_noNoiseInFillResponse() throws Exception {
        enableService();

        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start the activity
        startCustomPasswordViewLoginActivity();
        mUiBot.waitForIdle();

        // Add text to the username input field before triggering autofill
        updateUsername("sensitive_text");
        updatePassword("sensitive_text");

        // Trigger autofill
        mUiBot.selectByRelativeId(Helper.ID_USERNAME);
        mUiBot.waitForIdle();

        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        final AssistStructure structure = request.structure;

        // The username node in the request should not have noise, since it's an EditText.
        assertThat(structure).isNotNull();
        final AssistStructure.ViewNode username =
                Helper.findNodeByResourceId(structure, "username");
        assertThat(username.getAutofillNoiseInjectedData()).isNull();
        // Verify for editable input views which are NOT directly EditText, they should not have
        // noise injected data, either.
        assertThat(structure).isNotNull();
        final AssistStructure.ViewNode password =
                Helper.findNodeByResourceId(structure, "password");
        assertThat(username.getAutofillNoiseInjectedData()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STRING_REBUILD_API)
    public void testSensitiveText_differentNoiseForDifferentViews() throws Exception {
        enableService();

        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start the activity
        startCustomPasswordViewLoginActivity();
        mUiBot.waitForIdle();

        // Update password_label and username_label so that their plaintexts will be sanitized.
        updatePasswordLabel("sensitive_text");
        updateUsernameLabel("sensitive_text");
        mUiBot.waitForIdle();

        // Trigger autofill
        mUiBot.selectByRelativeId(Helper.ID_USERNAME);
        mUiBot.waitForIdle();

        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        final AssistStructure structure = request.structure;

        assertThat(structure).isNotNull();
        final AssistStructure.ViewNode usernameLabelNode =
                Helper.findNodeByResourceId(structure, "username_label");
        final AssistStructure.ViewNode passwordLabelNode =
                Helper.findNodeByResourceId(structure, "password_label");
        // The username_label & password_label nods in the request should have noise-injected data,
        // since their texts are NOT loaded for xml(hence not sensitive).
        assertThat(usernameLabelNode.getAutofillNoiseInjectedData()).isNotNull();
        assertThat(passwordLabelNode.getAutofillNoiseInjectedData()).isNotNull();
        // They should have the same RetainedBitMask value, since this value's randomization is
        // consistent per device.
        assertThat(usernameLabelNode.getAutofillNoiseInjectedData().getRetainedBitMask())
                .isEqualTo(passwordLabelNode.getAutofillNoiseInjectedData().getRetainedBitMask());
        // They should NOT have the same payload even when they have the same text, since this value
        // is randomized with different seed for different views.
        assertThat(usernameLabelNode.getAutofillNoiseInjectedData().getNoiseInjectedPayload())
                .isNotEqualTo(
                        passwordLabelNode.getAutofillNoiseInjectedData().getNoiseInjectedPayload());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STRING_REBUILD_API)
    public void testSensitiveText_sameNoiseForSameView() throws Exception {
        enableService();

        final AssistStructure.ViewNode usernameLabelNode1 =
                fetchAssistStructureAndCancelLogin("sensitive_text");
        // Do one round on the same activity
        final AssistStructure.ViewNode usernameLabelNode2 =
                fetchAssistStructureAndCancelLogin("sensitive_text");

        assertThat(usernameLabelNode1.getAutofillNoiseInjectedData()).isNotNull();
        assertThat(usernameLabelNode2.getAutofillNoiseInjectedData()).isNotNull();

        // They should have the same RetainedBitMask value, since this value's randomization is
        // consistent per device.
        assertThat(usernameLabelNode1.getAutofillNoiseInjectedData().getRetainedBitMask())
                .isEqualTo(usernameLabelNode2.getAutofillNoiseInjectedData().getRetainedBitMask());
        // They should have the same payload, since it's on the same activity's same view, where
        // the same randomization seed is used.
        assertThat(usernameLabelNode1.getAutofillNoiseInjectedData().getNoiseInjectedPayload())
                .isEqualTo(
                        usernameLabelNode2
                                .getAutofillNoiseInjectedData()
                                .getNoiseInjectedPayload());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STRING_REBUILD_API)
    public void testSensitiveText_randomNoiseInjected() throws Exception {
        enableService();

        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start the activity
        startCustomPasswordViewLoginActivity();
        mUiBot.waitForIdle();

        // Update username_label so that their plaintexts will be sanitized.
        final String originalText = "sensitive_text";
        updateUsernameLabel(originalText);
        mUiBot.waitForIdle();

        // Trigger autofill
        mUiBot.selectByRelativeId(Helper.ID_USERNAME);
        mUiBot.waitForIdle();

        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        final AssistStructure structure = request.structure;

        assertThat(structure).isNotNull();
        final AssistStructure.ViewNode usernameLabelNode =
                Helper.findNodeByResourceId(structure, "username_label");
        final AutofillNoiseInjectedData noiseInjectedData =
                usernameLabelNode.getAutofillNoiseInjectedData();
        assertThat(noiseInjectedData).isNotNull();
        // The mask should be either all odd or all even bits.
        int retainedBitMask = noiseInjectedData.getRetainedBitMask() & 0xFF;
        assertThat(retainedBitMask == ODD_BITS_MASK || retainedBitMask == EVEN_BITS_MASK).isTrue();

        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final AutofillManager afm = context.getSystemService(AutofillManager.class);
        final String masterSeed = afm.getNoiseInjectionMasterSeed();

        // Calculate the seed used in the Platform with the same master seed.
        final long seed =
                hashInputs(
                        masterSeed,
                        structure.getActivityComponent().getPackageName(),
                        structure.getActivityComponent().getClassName(),
                        usernameLabelNode.getAutofillId());

        // Recompute the noise with the same implementation as the Platform to verify the coin
        // flipping probabilities, etc.
        byte[] originalBytes = originalText.getBytes(StandardCharsets.UTF_8);
        byte[] adjustedBytes = new byte[FIXED_LENGTH_BYTES];
        int lengthToCopy = Math.min(originalBytes.length, FIXED_LENGTH_BYTES);
        System.arraycopy(originalBytes, 0, adjustedBytes, 0, lengthToCopy);

        Random random = new Random(seed);
        byte[] noisedBytes = Arrays.copyOf(adjustedBytes, adjustedBytes.length);

        // Inject noise at bit level
        for (int byteIndex = 0; byteIndex < FIXED_LENGTH_BYTES; byteIndex++) {
            byte currentByte = noisedBytes[byteIndex];
            byte modifiedByte = 0;
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                int finalBit = (currentByte >> bitIndex) & 1;

                // 50% chance to resample
                if (random.nextInt(100) < 50) {
                    // Flip a coin for the new value
                    finalBit = random.nextInt(100) < 50 ? 0 : 1;
                }

                if (finalBit == 1) {
                    modifiedByte = (byte) (modifiedByte | (1 << bitIndex));
                }
            }
            noisedBytes[byteIndex] = modifiedByte;
        }

        byte[] resultBytes = new byte[FIXED_LENGTH_BYTES];
        for (int byteIndex = 0; byteIndex < FIXED_LENGTH_BYTES; byteIndex++) {
            resultBytes[byteIndex] =
                    (byte) (noisedBytes[byteIndex] & noiseInjectedData.getRetainedBitMask());
        }

        assertThat(noiseInjectedData.getNoiseInjectedPayload()).isEqualTo(resultBytes);
    }

    private AssistStructure.ViewNode fetchAssistStructureAndCancelLogin(String newUsernameLabelText)
            throws Exception {
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start the activity
        startCustomPasswordViewLoginActivity();
        mUiBot.waitForIdle();

        // Update password_label and username_label so that their plaintexts will be sanitized.
        updateUsernameLabel(newUsernameLabelText);
        mUiBot.waitForIdle();

        // Trigger autofill
        mUiBot.selectByRelativeId(Helper.ID_USERNAME);
        mUiBot.waitForIdle();

        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        final AssistStructure structure = request.structure;

        assertThat(structure).isNotNull();

        mUiBot.selectByRelativeId("cancel");
        mUiBot.waitForIdle();

        return Helper.findNodeByResourceId(structure, "username_label");
    }

    private void updateUsernameLabel(String text) {
        CustomPasswordViewLoginActivity customPasswordViewLoginActivity =
                CustomPasswordViewLoginActivity.getCurrentActivity();
        customPasswordViewLoginActivity.syncRunOnUiThread(
                () -> customPasswordViewLoginActivity.getUsernameLabel().setText(text));
        mUiBot.waitForIdle();
    }

    private void updatePasswordLabel(String text) {
        CustomPasswordViewLoginActivity customPasswordViewLoginActivity =
                CustomPasswordViewLoginActivity.getCurrentActivity();
        customPasswordViewLoginActivity.syncRunOnUiThread(
                () -> customPasswordViewLoginActivity.getPasswordLabel().setText(text));
        mUiBot.waitForIdle();
    }

    private void updateUsername(String text) {
        CustomPasswordViewLoginActivity customPasswordViewLoginActivity =
                CustomPasswordViewLoginActivity.getCurrentActivity();
        customPasswordViewLoginActivity.syncRunOnUiThread(
                () -> customPasswordViewLoginActivity.getUsername().setText(text));
        mUiBot.waitForIdle();
    }

    private void updatePassword(String text) {
        CustomPasswordViewLoginActivity customPasswordViewLoginActivity =
                CustomPasswordViewLoginActivity.getCurrentActivity();
        customPasswordViewLoginActivity.syncRunOnUiThread(
                () -> customPasswordViewLoginActivity.getPassword().setText(text));
        mUiBot.waitForIdle();
    }

    private long hashString(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        long hashLong = 0;
        for (int i = 0; i < 8; i++) {
            hashLong = (hashLong << 8) | (hashBytes[i] & 0xFF);
        }
        return hashLong;
    }

    private long hashInputs(
            String masterSeed, String packageName, String className, AutofillId autofillId)
            throws NoSuchAlgorithmException {
        String combined =
                masterSeed
                        + "|"
                        + packageName
                        + "|"
                        + className
                        + "|"
                        + autofillId.getViewId()
                        + "|"
                        + autofillId.getAutofillVirtualId();
        return hashString(combined);
    }
}
