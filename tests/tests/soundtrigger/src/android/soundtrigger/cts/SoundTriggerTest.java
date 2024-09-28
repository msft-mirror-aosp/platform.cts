/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.soundtrigger.cts;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;

import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseRecognitionExtra;
import android.hardware.soundtrigger.SoundTrigger.RecognitionConfig;
import android.media.AudioFormat;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.ImmutableList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class SoundTriggerTest {
    private static final int TEST_KEYPHRASE_ID = 200;
    private static final String TEST_KEYPHRASE_TEXT = "test_keyphrase";
    private static final int[] TEST_SUPPORTED_USERS = new int[] {1, 2, 3};
    private static final int TEST_RECOGNITION_MODES = SoundTrigger.RECOGNITION_MODE_GENERIC
            | SoundTrigger.RECOGNITION_MODE_USER_AUTHENTICATION
            | SoundTrigger.RECOGNITION_MODE_USER_IDENTIFICATION
            | SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER;

    private static final UUID TEST_MODEL_UUID = UUID.randomUUID();
    private static final UUID TEST_VENDOR_UUID = UUID.randomUUID();
    private static final int TEST_MODEL_VERSION = 123456;

    private static final int TEST_MODULE_ID = 1;
    private static final String TEST_IMPLEMENTOR = "test implementor";
    private static final String TEST_DESCRIPTION = "test description";
    private static final UUID TEST_MODULE_UUID = UUID.randomUUID();
    private static final int TEST_MODULE_VERSION = 45678;
    private static final String TEST_SUPPORTED_MODEL_ARCH = UUID.randomUUID().toString();
    private static final int TEST_MAX_SOUND_MODELS = 10;
    private static final int TEST_MAX_KEYPHRASES = 2;
    private static final int TEST_MAX_USERS = 3;
    private static final boolean TEST_SUPPORT_CAPTURE_TRANSITION = true;
    private static final int TEST_MAX_BUFFER_SIZE = 2048;
    private static final boolean TEST_SUPPORTS_CONCURRENT_CAPTURE = true;
    private static final int TEST_POWER_CONSUMPTION_MW = 50;
    private static final boolean TEST_RETURNES_TRIGGER_IN_EVENT = false;
    private static final int TEST_AUDIO_CAPABILITIES =
            SoundTrigger.ModuleProperties.AUDIO_CAPABILITY_ECHO_CANCELLATION
                    | SoundTrigger.ModuleProperties.AUDIO_CAPABILITY_NOISE_SUPPRESSION;
    private static final byte[] TEST_MODEL_DATA = new byte[1024];

    @BeforeClass
    public static void setUpClass() {
        new Random().nextBytes(TEST_MODEL_DATA);
    }

    private static SoundTrigger.Keyphrase createTestKeyphrase() {
        return new SoundTrigger.Keyphrase(TEST_KEYPHRASE_ID, TEST_RECOGNITION_MODES,
                Locale.forLanguageTag("en-US"), TEST_KEYPHRASE_TEXT, TEST_SUPPORTED_USERS);
    }

    private static void verifyKeyphraseMatchesTestParams(SoundTrigger.Keyphrase keyphrase) {
        assertThat(keyphrase.getId()).isEqualTo(TEST_KEYPHRASE_ID);
        assertThat(keyphrase.getRecognitionModes()).isEqualTo(TEST_RECOGNITION_MODES);
        assertThat(keyphrase.getLocale()).isEqualTo(Locale.forLanguageTag("en-US"));
        assertThat(keyphrase.getText()).isEqualTo(TEST_KEYPHRASE_TEXT);
        assertThat(keyphrase.getUsers()).asList().containsExactly(1, 2, 3).inOrder();
    }

    private static SoundTrigger.KeyphraseSoundModel createTestKeyphraseSoundModel() {
        return new SoundTrigger.KeyphraseSoundModel(TEST_MODEL_UUID, TEST_VENDOR_UUID,
                SoundTriggerTest.TEST_MODEL_DATA,
                new SoundTrigger.Keyphrase[] {createTestKeyphrase()}, TEST_MODEL_VERSION);
    }

    private static void verifyKeyphraseSoundModelMatchesTestParams(
            SoundTrigger.KeyphraseSoundModel keyphraseSoundModel) {
        assertThat(keyphraseSoundModel.getUuid()).isEqualTo(TEST_MODEL_UUID);
        assertThat(keyphraseSoundModel.getVendorUuid()).isEqualTo(TEST_VENDOR_UUID);
        assertArrayEquals(keyphraseSoundModel.getData(), SoundTriggerTest.TEST_MODEL_DATA);
        assertThat(keyphraseSoundModel.getKeyphrases())
                .asList()
                .containsExactly(createTestKeyphrase())
                .inOrder();
        assertThat(keyphraseSoundModel.getVersion()).isEqualTo(TEST_MODEL_VERSION);
        assertThat(keyphraseSoundModel.getType()).isEqualTo(SoundTrigger.SoundModel.TYPE_KEYPHRASE);
    }

    private static SoundTrigger.GenericSoundModel createTestGenericSoundModel() {
        return new SoundTrigger.GenericSoundModel(TEST_MODEL_UUID, TEST_VENDOR_UUID,
                SoundTriggerTest.TEST_MODEL_DATA, TEST_MODEL_VERSION);
    }

    private static void verifyGenericSoundModelMatchesTestParams(
            SoundTrigger.GenericSoundModel genericSoundModel) {
        assertThat(genericSoundModel.getUuid()).isEqualTo(TEST_MODEL_UUID);
        assertThat(genericSoundModel.getVendorUuid()).isEqualTo(TEST_VENDOR_UUID);
        assertArrayEquals(genericSoundModel.getData(), SoundTriggerTest.TEST_MODEL_DATA);
        assertThat(genericSoundModel.getVersion()).isEqualTo(TEST_MODEL_VERSION);
        assertThat(genericSoundModel.getType())
                .isEqualTo(SoundTrigger.SoundModel.TYPE_GENERIC_SOUND);
    }

    private SoundTrigger.ModuleProperties createTestModuleProperties() {
        return new SoundTrigger.ModuleProperties(TEST_MODULE_ID, TEST_IMPLEMENTOR, TEST_DESCRIPTION,
                TEST_MODULE_UUID.toString(), TEST_MODULE_VERSION, TEST_SUPPORTED_MODEL_ARCH,
                TEST_MAX_SOUND_MODELS, TEST_MAX_KEYPHRASES, TEST_MAX_USERS, TEST_RECOGNITION_MODES,
                TEST_SUPPORT_CAPTURE_TRANSITION, TEST_MAX_BUFFER_SIZE,
                TEST_SUPPORTS_CONCURRENT_CAPTURE, TEST_POWER_CONSUMPTION_MW,
                TEST_RETURNES_TRIGGER_IN_EVENT, TEST_AUDIO_CAPABILITIES);
    }

    private static void verifyModulePropertiesMatchesTestParams(
            SoundTrigger.ModuleProperties moduleProperties) {
        assertThat(moduleProperties.getId()).isEqualTo(TEST_MODULE_ID);
        assertThat(moduleProperties.getImplementor()).isEqualTo(TEST_IMPLEMENTOR);
        assertThat(moduleProperties.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(moduleProperties.getUuid()).isEqualTo(TEST_MODULE_UUID);
        assertThat(moduleProperties.getVersion()).isEqualTo(TEST_MODULE_VERSION);
        assertThat(moduleProperties.getSupportedModelArch()).isEqualTo(TEST_SUPPORTED_MODEL_ARCH);
        assertThat(moduleProperties.getMaxSoundModels()).isEqualTo(TEST_MAX_SOUND_MODELS);
        assertThat(moduleProperties.getMaxKeyphrases()).isEqualTo(TEST_MAX_KEYPHRASES);
        assertThat(moduleProperties.getMaxUsers()).isEqualTo(TEST_MAX_USERS);
        assertThat(moduleProperties.getRecognitionModes()).isEqualTo(TEST_RECOGNITION_MODES);
        assertThat(moduleProperties.isCaptureTransitionSupported())
                .isEqualTo(TEST_SUPPORT_CAPTURE_TRANSITION);
        assertThat(moduleProperties.getMaxBufferMillis()).isEqualTo(TEST_MAX_BUFFER_SIZE);
        assertThat(moduleProperties.isConcurrentCaptureSupported())
                .isEqualTo(TEST_SUPPORTS_CONCURRENT_CAPTURE);
        assertThat(moduleProperties.getPowerConsumptionMw()).isEqualTo(TEST_POWER_CONSUMPTION_MW);
        assertThat(moduleProperties.isTriggerReturnedInEvent())
                .isEqualTo(TEST_RETURNES_TRIGGER_IN_EVENT);
        assertThat(moduleProperties.getAudioCapabilities()).isEqualTo(TEST_AUDIO_CAPABILITIES);
        assertThat(moduleProperties.describeContents()).isEqualTo(0);
    }

    @Test
    public void testKeyphraseParcelUnparcel() {
        SoundTrigger.Keyphrase keyphraseSrc = createTestKeyphrase();
        verifyKeyphraseMatchesTestParams(keyphraseSrc);
        Parcel parcel = Parcel.obtain();
        keyphraseSrc.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        SoundTrigger.Keyphrase keyphraseResult = SoundTrigger.Keyphrase.readFromParcel(parcel);
        assertThat(keyphraseSrc).isEqualTo(keyphraseResult);
        verifyKeyphraseMatchesTestParams(keyphraseResult);

        parcel.setDataPosition(0);
        keyphraseResult = SoundTrigger.Keyphrase.CREATOR.createFromParcel(parcel);
        assertThat(keyphraseSrc).isEqualTo(keyphraseResult);
        verifyKeyphraseMatchesTestParams(keyphraseResult);
    }

    @Test
    public void testKeyphraseSoundModelParcelUnparcel() {
        SoundTrigger.KeyphraseSoundModel keyphraseSoundModelSrc =
                createTestKeyphraseSoundModel();
        Parcel parcel = Parcel.obtain();
        keyphraseSoundModelSrc.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        SoundTrigger.KeyphraseSoundModel keyphraseSoundModelResult =
                SoundTrigger.KeyphraseSoundModel.readFromParcel(parcel);
        assertThat(keyphraseSoundModelSrc).isEqualTo(keyphraseSoundModelResult);
        verifyKeyphraseSoundModelMatchesTestParams(keyphraseSoundModelResult);

        parcel.setDataPosition(0);
        keyphraseSoundModelResult = SoundTrigger.KeyphraseSoundModel.CREATOR.createFromParcel(
                parcel);
        assertThat(keyphraseSoundModelSrc).isEqualTo(keyphraseSoundModelResult);
        verifyKeyphraseSoundModelMatchesTestParams(keyphraseSoundModelResult);
    }

    @Test
    public void testGenericSoundModelParcelUnparcel() {
        SoundTrigger.GenericSoundModel genericSoundModelSrc = createTestGenericSoundModel();
        Parcel parcel = Parcel.obtain();
        genericSoundModelSrc.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        SoundTrigger.GenericSoundModel genericSoundModelResult =
                SoundTrigger.GenericSoundModel.CREATOR.createFromParcel(parcel);
        assertThat(genericSoundModelSrc).isEqualTo(genericSoundModelResult);
        verifyGenericSoundModelMatchesTestParams(genericSoundModelResult);
    }

    @Test
    public void testModulePropertiesParcelUnparcel() {
        SoundTrigger.ModuleProperties modulePropertiesSrc = createTestModuleProperties();
        Parcel parcel = Parcel.obtain();
        modulePropertiesSrc.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        SoundTrigger.ModuleProperties modulePropertiesResult =
                SoundTrigger.ModuleProperties.CREATOR.createFromParcel(parcel);
        assertThat(modulePropertiesSrc).isEqualTo(modulePropertiesResult);
        verifyModulePropertiesMatchesTestParams(modulePropertiesResult);
    }

    @Test
    public void testModelParamRangeParcelUnparcel() {
        SoundTrigger.ModelParamRange modelParamRangeSrc = new SoundTrigger.ModelParamRange(-1, 10);
        Parcel parcel = Parcel.obtain();
        modelParamRangeSrc.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        SoundTrigger.ModelParamRange modelParamRangeResult =
                SoundTrigger.ModelParamRange.CREATOR.createFromParcel(parcel);
        assertThat(modelParamRangeSrc).isEqualTo(modelParamRangeResult);
        assertThat(modelParamRangeResult.getStart()).isEqualTo(-1);
        assertThat(modelParamRangeResult.getEnd()).isEqualTo(10);
    }

    @Test
    public void testRecognitionEventBasicGetters() {
        AudioFormat audioFormat = new AudioFormat.Builder().build();
        SoundTrigger.RecognitionEvent recognitionEvent = new SoundTrigger.RecognitionEvent(
                0 /* status */,
                100 /* soundModelHandle */,
                true /* captureAvailable */,
                101 /* captureSession */,
                1000 /* captureDelayMs */,
                1001 /* capturePreambleMs */,
                true /* triggerInData */,
                audioFormat,
                TEST_MODEL_DATA,
                12345 /* halEventReceivedMillis */);
        assertThat(recognitionEvent.getCaptureFormat()).isEqualTo(audioFormat);
        assertThat(recognitionEvent.getCaptureSession()).isEqualTo(101);
        assertArrayEquals(recognitionEvent.getData(), TEST_MODEL_DATA);
        assertThat(recognitionEvent.getHalEventReceivedMillis()).isEqualTo(12345);
    }

    @Test
    public void testRecognitionConfigBuilderDefaultValues() {
        RecognitionConfig recognitionConfig = new RecognitionConfig.Builder().build();
        assertThat(recognitionConfig.isCaptureRequested()).isFalse();
        assertThat(recognitionConfig.isAllowMultipleTriggers()).isFalse();
        assertThat(recognitionConfig.getKeyphrases()).isNotNull();
        assertThat(recognitionConfig.getKeyphrases()).isEmpty();
        assertThat(recognitionConfig.getData()).isNotNull();
        assertThat(recognitionConfig.getData()).hasLength(0);
        assertThat(recognitionConfig.getAudioCapabilities()).isEqualTo(0);
    }

    @Test
    public void testRecognitionConfigBuilderCustomizedValues() {
        byte[] data = new byte[] {0, 1, 2, 3, 4};
        List<KeyphraseRecognitionExtra> keyphrases =
            ImmutableList.of(
                new KeyphraseRecognitionExtra(1, SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER, 1));
        RecognitionConfig recognitionConfig =
                new RecognitionConfig.Builder()
                        .setCaptureRequested(true)
                        .setAllowMultipleTriggers(true)
                        .setKeyphrases(keyphrases)
                        .setData(data)
                        .setAudioCapabilities(1)
                        .build();

        assertThat(recognitionConfig.isCaptureRequested()).isTrue();
        assertThat(recognitionConfig.isAllowMultipleTriggers()).isTrue();
        assertThat(recognitionConfig.getKeyphrases()).isEqualTo(keyphrases);
        assertThat(recognitionConfig.getData())
                .asList()
                .containsExactly((byte) 0, (byte) 1, (byte) 2, (byte) 3, (byte) 4)
                .inOrder();
        assertThat(recognitionConfig.getAudioCapabilities()).isEqualTo(1);
    }
}
