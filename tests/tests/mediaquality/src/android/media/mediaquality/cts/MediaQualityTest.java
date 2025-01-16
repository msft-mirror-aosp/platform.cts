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
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.quality.AmbientBacklightEvent;
import android.media.quality.AmbientBacklightSettings;
import android.media.quality.MediaQualityContract.PictureQuality;
import android.media.quality.MediaQualityContract.SoundQuality;
import android.media.quality.MediaQualityManager;
import android.media.quality.PictureProfile;
import android.media.quality.SoundProfile;
import android.media.tv.flags.Flags;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaQualityTest {
    private MediaQualityManager mManager;
    private static final String PACKAGE_NAME = "android.media.mediaquality.cts";
    private AmbientBacklightSettings mAmbientBacklightSettings;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        mManager = context.getSystemService(MediaQualityManager.class);
        mAmbientBacklightSettings = createAmbientBacklightSettings();
        assumeTrue(mManager != null);
        if (mManager == null || !isSupported()) {
            return;
        }
    }

    private boolean isSupported() {
        return mManager.isSupported();
    }

    @After
    public void tearDown() {
        if (mManager != null) {
            // Remove all picture profiles.
            List<PictureProfile> pictureProfiles =
                    mManager.getPictureProfilesByPackage(PACKAGE_NAME, includeParams(false));
            for (PictureProfile profile : pictureProfiles) {
                mManager.removePictureProfile(profile.getProfileId());
            }

            // Remove all sound profiles.
            List<SoundProfile> soundProfiles =
                    mManager.getSoundProfilesByPackage(PACKAGE_NAME, includeParams(false));
            for (SoundProfile profile : soundProfiles) {
                mManager.removeSoundProfile(profile.getProfileId());
            }
        }
    }

    public static class MockCallback implements MediaQualityManager.AmbientBacklightCallback {
        public MockCallback() {
            super();
        }

        @Override
        public void onAmbientBacklightEvent(AmbientBacklightEvent event) {
            assertNotNull("Ambient backlight event is null", event);
            if (event.getEventType() == MediaQualityManager.AMBIENT_BACKLIGHT_EVENT_METADATA) {
                assertNotNull("Ambient Backlight Metadata is null", event.getMetadata());
                assertNotNull(
                        "Ambient Backlight Metadata zone color is null",
                        event.getMetadata().getZoneColors());
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetAvailablePictureProfiles() throws Exception {
        mManager.getAvailablePictureProfiles(null);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetPictureProfileAllowlist() {
        Exception exception = null;
        try {
            List<String> allow = Arrays.asList("Profile1", "Profile2", "Profile3");
            mManager.setPictureProfileAllowList(allow);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfileAllowlist() {
        List<String> allow = Arrays.asList("Profile4", "Profile5", "Profile6");
        mManager.setPictureProfileAllowList(allow);

        List<String> queries = mManager.getPictureProfileAllowList();
        Assert.assertNotNull(queries);
        Assert.assertEquals(queries.size(), 3);
        for (String a : allow) {
            Assert.assertTrue(queries.contains(a));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetSoundProfileAllowlist() {
        Exception exception = null;
        try {
            List<String> allow = Arrays.asList("Profile1", "Profile2", "Profile3");
            mManager.setSoundProfileAllowList(allow);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfileAllowlist() {
        List<String> allow = Arrays.asList("Profile4", "Profile5", "Profile6");
        mManager.setSoundProfileAllowList(allow);

        List<String> queries = mManager.getSoundProfileAllowList();
        Assert.assertNotNull(queries);
        Assert.assertEquals(queries.size(), 3);
        for (String a : allow) {
            Assert.assertTrue(queries.contains(a));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testCreatePictureProfile() {
        Exception exception = null;
        try {
            PictureProfile toCreate = getTestPictureProfile("createPictureProfile");

            mManager.createPictureProfile(toCreate);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUpdatePictureProfile() {
        PictureProfile toCreate = getTestPictureProfile("updatePictureProfile");
        mManager.createPictureProfile(toCreate);
        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getInt(PictureQuality.PARAMETER_BRIGHTNESS),
                expected.getInt(PictureQuality.PARAMETER_BRIGHTNESS));

        PersistableBundle newParams = new PersistableBundle();
        newParams.putInt(
                PictureQuality.PARAMETER_BRIGHTNESS,
                expected.getInt(PictureQuality.PARAMETER_BRIGHTNESS) + 1);
        newParams.putInt(
                PictureQuality.PARAMETER_SATURATION,
                expected.getInt(PictureQuality.PARAMETER_SATURATION) + 1);
        newParams.putInt(
                PictureQuality.PARAMETER_CONTRAST,
                expected.getInt(PictureQuality.PARAMETER_CONTRAST) + 1);

        PictureProfile toUpdate =
                new PictureProfile.Builder(profile).setParameters(newParams).build();
        mManager.updatePictureProfile(profile.getProfileId(), toUpdate);
        PictureProfile profile2 =
                mManager.getPictureProfile(
                        toUpdate.getProfileType(), toUpdate.getName(), includeParams(true));
        Assert.assertNotNull(profile2);
        PersistableBundle created = toCreate.getParameters();
        PersistableBundle updated = profile2.getParameters();
        Assert.assertNotEquals(
                created.getInt(PictureQuality.PARAMETER_BRIGHTNESS),
                updated.getInt(PictureQuality.PARAMETER_BRIGHTNESS));
        Assert.assertNotEquals(
                created.getInt(PictureQuality.PARAMETER_SATURATION),
                updated.getInt(PictureQuality.PARAMETER_SATURATION));
        Assert.assertNotEquals(
                created.getInt(PictureQuality.PARAMETER_CONTRAST),
                updated.getInt(PictureQuality.PARAMETER_CONTRAST));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRemovePictureProfile() {
        PictureProfile toCreate = getTestPictureProfile("removePictureProfile");

        mManager.createPictureProfile(toCreate);
        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNotNull(profile);

        mManager.removePictureProfile(profile.getProfileId());
        PictureProfile profile2 =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNull(profile2);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfile() {
        PictureProfile toCreate = getTestPictureProfile("getPictureProfile");

        mManager.createPictureProfile(toCreate);
        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        Assert.assertEquals(profile.getProfileType(), toCreate.getProfileType());
        Assert.assertEquals(profile.getName(), toCreate.getName());
        Assert.assertEquals(profile.getInputId(), toCreate.getInputId());
        Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getString(PictureQuality.PARAMETER_BRIGHTNESS),
                expected.getString(PictureQuality.PARAMETER_BRIGHTNESS));
        Assert.assertEquals(
                actual.getString(PictureQuality.PARAMETER_SATURATION),
                expected.getString(PictureQuality.PARAMETER_SATURATION));
        Assert.assertEquals(
                actual.getString(PictureQuality.PARAMETER_CONTRAST),
                expected.getString(PictureQuality.PARAMETER_CONTRAST));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfilesByPackage() {
        PictureProfile toCreate = getTestPictureProfile("getPictureProfilesByPackage");

        mManager.createPictureProfile(toCreate);
        List<PictureProfile> profiles =
                mManager.getPictureProfilesByPackage(
                        toCreate.getPackageName(), includeParams(false));
        Assert.assertNotNull(profiles);
        for (PictureProfile profile : profiles) {
            Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAmbientBacklightEnabled() throws Exception {
        mManager.setAmbientBacklightEnabled(true);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRegisterAmbientBacklightCallback() throws Exception {
        mManager.registerAmbientBacklightCallback(
                Executors.newSingleThreadExecutor(), new MockCallback());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAmbientBacklightSettings() throws Exception {
        mManager.setAmbientBacklightSettings(mAmbientBacklightSettings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testCreateSoundProfile() {
        Exception exception = null;
        try {
            SoundProfile toCreate = getTestSoundProfile("createSoundProfile");

            mManager.createSoundProfile(toCreate);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUpdateSoundProfile() {
        SoundProfile toCreate = getTestSoundProfile("updateSoundProfile");
        mManager.createSoundProfile(toCreate);
        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_BALANCE),
                expected.getInt(SoundQuality.PARAMETER_BALANCE));

        PersistableBundle newParams = new PersistableBundle();
        newParams.putInt(
                SoundQuality.PARAMETER_BALANCE,
                expected.getInt(SoundQuality.PARAMETER_BALANCE) + 1);
        newParams.putInt(
                SoundQuality.PARAMETER_BASS, expected.getInt(SoundQuality.PARAMETER_BASS) + 1);
        newParams.putInt(
                SoundQuality.PARAMETER_TREBLE, expected.getInt(SoundQuality.PARAMETER_TREBLE) + 1);

        SoundProfile toUpdate = new SoundProfile.Builder(profile).setParameters(newParams).build();

        mManager.updateSoundProfile(profile.getProfileId(), toUpdate);
        SoundProfile profile2 =
                mManager.getSoundProfile(
                        toUpdate.getProfileType(), toUpdate.getName(), includeParams(true));
        Assert.assertNotNull(profile2);
        PersistableBundle created = toCreate.getParameters();
        PersistableBundle updated = profile2.getParameters();
        Assert.assertNotEquals(
                created.getInt(SoundQuality.PARAMETER_BALANCE),
                updated.getInt(SoundQuality.PARAMETER_BALANCE));
        Assert.assertNotEquals(
                created.getInt(SoundQuality.PARAMETER_BASS),
                updated.getInt(SoundQuality.PARAMETER_BASS));
        Assert.assertNotEquals(
                created.getInt(SoundQuality.PARAMETER_TREBLE),
                updated.getInt(SoundQuality.PARAMETER_TREBLE));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRemoveSoundProfile() {
        SoundProfile toCreate = getTestSoundProfile("removeSoundProfile");

        mManager.createSoundProfile(toCreate);
        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNotNull(profile);

        mManager.removeSoundProfile(profile.getProfileId());
        SoundProfile profile2 =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNull(profile2);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfile() {
        SoundProfile toCreate = getTestSoundProfile("getSoundProfile");

        mManager.createSoundProfile(toCreate);
        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        Assert.assertEquals(profile.getProfileType(), toCreate.getProfileType());
        Assert.assertEquals(profile.getName(), toCreate.getName());
        Assert.assertEquals(profile.getInputId(), toCreate.getInputId());
        Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_BALANCE),
                expected.getInt(SoundQuality.PARAMETER_BALANCE));
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_BASS),
                expected.getInt(SoundQuality.PARAMETER_BASS));
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_TREBLE),
                expected.getInt(SoundQuality.PARAMETER_TREBLE));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfilesByPackage() {
        SoundProfile toCreate = getTestSoundProfile("getSoundProfilesByPackage");

        mManager.createSoundProfile(toCreate);
        List<SoundProfile> profiles =
                mManager.getSoundProfilesByPackage(toCreate.getPackageName(), includeParams(false));
        Assert.assertNotNull(profiles);
        for (SoundProfile profile : profiles) {
            Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        }
    }

    private PictureProfile getTestPictureProfile(String methodName) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(PictureQuality.PARAMETER_BRIGHTNESS, 56);
        bundle.putInt(PictureQuality.PARAMETER_SATURATION, 23);
        bundle.putInt(PictureQuality.PARAMETER_CONTRAST, 87);

        return new PictureProfile.Builder("testName" + methodName)
                .setInputId("testInputId" + methodName)
                .setProfileType(PictureProfile.TYPE_APPLICATION)
                .setPackageName(PACKAGE_NAME)
                .setParameters(bundle)
                .build();
    }

    private SoundProfile getTestSoundProfile(String methodName) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(SoundQuality.PARAMETER_BALANCE, 12);
        bundle.putInt(SoundQuality.PARAMETER_BASS, 24);
        bundle.putInt(SoundQuality.PARAMETER_TREBLE, 36);

        return new SoundProfile.Builder("testName" + methodName)
                .setInputId("testInputId" + methodName)
                .setProfileType(SoundProfile.TYPE_APPLICATION)
                .setPackageName(PACKAGE_NAME)
                .setParameters(bundle)
                .build();
    }

    private MediaQualityManager.ProfileQueryParams includeParams(boolean include) {
        return new MediaQualityManager.ProfileQueryParams(include);
    }

    private AmbientBacklightSettings createAmbientBacklightSettings() {
        AmbientBacklightSettings settings =
                new AmbientBacklightSettings(
                        AmbientBacklightSettings.SOURCE_VIDEO, // Example source
                        30, // Example max FPS
                        PixelFormat.RGBA_8888, // Example color format
                        10, // Example horizontal zones
                        8, // Example vertical zones
                        true, // Example letterbox omitted
                        5 // Example threshold
                        );
        return settings;
    }
}
