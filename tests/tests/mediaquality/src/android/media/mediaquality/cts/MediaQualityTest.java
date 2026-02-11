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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.quality.ActiveProcessingPicture;
import android.media.quality.AmbientBacklightEvent;
import android.media.quality.AmbientBacklightMetadata;
import android.media.quality.AmbientBacklightSettings;
import android.media.quality.EqualizerBand;
import android.media.quality.EqualizerCapabilities;
import android.media.quality.EqualizerSettings;
import android.media.quality.MediaQualityContract;
import android.media.quality.MediaQualityContract.PictureQuality;
import android.media.quality.MediaQualityContract.SoundQuality;
import android.media.quality.MediaQualityManager;
import android.media.quality.ParameterCapability;
import android.media.quality.PictureProfile;
import android.media.quality.PictureProfileHandle;
import android.media.quality.SoundProfile;
import android.media.quality.SoundProfileHandle;
import android.media.tv.flags.Flags;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaQualityTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private MediaQualityManager mManager;
    private static final String PACKAGE_NAME = "android.media.mediaquality.cts";
    private AmbientBacklightSettings mAmbientBacklightSettings;
    private static final int POLLING_TIMEOUT_MS = 5000; // 5 seconds max wait
    private static final int POLLING_INTERVAL_MS = 100; // Check every 0.1 seconds
    private static final String TAG = "MediaQualityTest";

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        mManager = context.getSystemService(MediaQualityManager.class);
        mAmbientBacklightSettings = createAmbientBacklightSettings();

        // Ensure the manager exists before proceeding
        assumeTrue(mManager != null);

        if (mManager == null || !isSupported()) {
            return;
        }
    }

    private boolean isSupported() {
        return mManager.isSupported();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mManager != null) {
            // Remove all picture profiles created by this package
            List<PictureProfile> pictureProfiles =
                    mManager.getPictureProfilesByPackage(PACKAGE_NAME, includeParams(false));
            for (PictureProfile profile : pictureProfiles) {
                mManager.removePictureProfile(profile.getProfileId());
            }

            // Remove all sound profiles created by this package
            List<SoundProfile> soundProfiles =
                    mManager.getSoundProfilesByPackage(PACKAGE_NAME, includeParams(false));
            for (SoundProfile profile : soundProfiles) {
                mManager.removeSoundProfile(profile.getProfileId());
            }

            // Wait for cleanup to finish to prevent interference with subsequent tests
            waitForCondition(
                    () ->
                            mManager.getPictureProfilesByPackage(PACKAGE_NAME, includeParams(false))
                                            .isEmpty()
                                    && mManager.getSoundProfilesByPackage(
                                                    PACKAGE_NAME, includeParams(false))
                                            .isEmpty());
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
    public void testUpdatePictureProfile() throws InterruptedException {
        PictureProfile toCreate = getTestPictureProfile("updatePictureProfile");
        mManager.createPictureProfile(toCreate);
        boolean created =
                waitForCondition(
                        () -> {
                            return mManager.getPictureProfile(
                                            toCreate.getProfileType(),
                                            toCreate.getName(),
                                            includeParams(true))
                                    != null;
                        });
        Assert.assertTrue("Profile was not created within the timeout.", created);

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
        final int newBrightness = newParams.getInt(PictureQuality.PARAMETER_BRIGHTNESS);
        boolean updated =
                waitForCondition(
                        () -> {
                            PictureProfile p =
                                    mManager.getPictureProfile(
                                            toUpdate.getProfileType(),
                                            toUpdate.getName(),
                                            includeParams(true));
                            return p != null
                                    && p.getParameters().getInt(PictureQuality.PARAMETER_BRIGHTNESS)
                                            == newBrightness;
                        });
        Assert.assertTrue("Profile was not updated within the timeout.", updated);

        PictureProfile profile2 =
            mManager.getPictureProfile(
                    toUpdate.getProfileType(), toUpdate.getName(), includeParams(true));
        Assert.assertNotNull(profile2);
        PersistableBundle createdParams = toCreate.getParameters();
        PersistableBundle updatedParams = profile2.getParameters();
        Assert.assertNotEquals(
                createdParams.getInt(PictureQuality.PARAMETER_BRIGHTNESS),
                updatedParams.getInt(PictureQuality.PARAMETER_BRIGHTNESS));
        Assert.assertNotEquals(
                createdParams.getInt(PictureQuality.PARAMETER_SATURATION),
                updatedParams.getInt(PictureQuality.PARAMETER_SATURATION));
        Assert.assertNotEquals(
                createdParams.getInt(PictureQuality.PARAMETER_CONTRAST),
                updatedParams.getInt(PictureQuality.PARAMETER_CONTRAST));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRemovePictureProfile() throws InterruptedException {
        PictureProfile toCreate = getTestPictureProfile("removePictureProfile");

        mManager.createPictureProfile(toCreate);

        // Verification: Wait until the profile actually exists
        boolean created =
                waitForCondition(
                        () -> {
                            return mManager.getPictureProfile(
                                            toCreate.getProfileType(),
                                            toCreate.getName(),
                                            includeParams(false))
                                    != null;
                        });
        Assert.assertTrue("Profile was not created within the timeout.", created);

        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNotNull(profile);

        mManager.removePictureProfile(profile.getProfileId());
        // Verification: Wait until the profile is actually gone
        boolean removed =
                waitForCondition(
                        () ->
                                mManager.getPictureProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(false))
                                        == null);
        Assert.assertTrue("Profile was not removed within the timeout.", removed);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfile() throws InterruptedException {
        PictureProfile toCreate = getTestPictureProfile("getPictureProfile");

        mManager.createPictureProfile(toCreate);
        boolean created =
                waitForCondition(
                        () ->
                                mManager.getPictureProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(true))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout.", created);

        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        Assert.assertEquals(profile.getProfileType(), toCreate.getProfileType());
        Assert.assertEquals(profile.getName(), toCreate.getName());
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
    public void testGetPictureProfilePackageNames() {
        PictureProfile toCreate = getTestPictureProfile("testGetPictureProfilePackageNames");
        mManager.createPictureProfile(toCreate);

        List<String> packageNames = mManager.getPictureProfilePackageNames();
        Assert.assertNotNull(packageNames);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetAvailablePictureProfiles() throws Exception {
        mManager.getAvailablePictureProfiles(null);
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
    public void testUpdateSoundProfile() throws InterruptedException {
        SoundProfile toCreate = getTestSoundProfile("updateSoundProfile");
        mManager.createSoundProfile(toCreate);

        boolean createdCheck =
                waitForCondition(
                        () ->
                                mManager.getSoundProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(true))
                                        != null);

        Assert.assertTrue("Profile was not created within the timeout.", createdCheck);

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

        boolean updatedCheck =
                waitForCondition(
                        () -> {
                            SoundProfile p =
                                    mManager.getSoundProfile(
                                            toUpdate.getProfileType(),
                                            toUpdate.getName(),
                                            includeParams(true));
                            return p != null
                                    && p.getParameters().getInt(SoundQuality.PARAMETER_BALANCE)
                                            == 13;
                        });
        Assert.assertTrue("Profile was not updated within the timeout.", updatedCheck);

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
    public void testRemoveSoundProfile() throws InterruptedException {
        SoundProfile toCreate = getTestSoundProfile("removeSoundProfile");

        mManager.createSoundProfile(toCreate);
        boolean created =
                waitForCondition(
                        () ->
                                mManager.getSoundProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(false))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout.", created);

        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNotNull(profile);

        mManager.removeSoundProfile(profile.getProfileId());
        boolean removed =
                waitForCondition(
                        () ->
                                mManager.getSoundProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(false))
                                        == null);
        Assert.assertTrue("Profile was not removed within the timeout.", removed);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfile() throws InterruptedException {
        SoundProfile toCreate = getTestSoundProfile("getSoundProfile");

        mManager.createSoundProfile(toCreate);
        boolean created =
                waitForCondition(
                        () ->
                                mManager.getSoundProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(true))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout.", created);

        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        Assert.assertEquals(profile.getProfileType(), toCreate.getProfileType());
        Assert.assertEquals(profile.getName(), toCreate.getName());
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

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfilePackageNames() {
        SoundProfile toCreate = getTestSoundProfile("testGetSoundProfilePackageNames");
        mManager.createSoundProfile(toCreate);

        List<String> packageNames = mManager.getSoundProfilePackageNames();
        Assert.assertNotNull(packageNames);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetAvailableSoundProfiles() throws Exception {
        mManager.getAvailableSoundProfiles(null);
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
    public void testGetPictureProfileAllowlist() throws InterruptedException {
        List<String> allow = Arrays.asList("Profile4", "Profile5", "Profile6");
        mManager.setPictureProfileAllowList(allow);

        boolean updated =
                waitForCondition(
                        () -> {
                            List<String> queries = mManager.getPictureProfileAllowList();
                            return queries != null
                                    && queries.containsAll(allow)
                                    && queries.size() == allow.size();
                        });
        Assert.assertTrue("Allow list not updated within timeout", updated);

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
    public void testGetSoundProfileAllowlist() throws InterruptedException {
        List<String> allow = Arrays.asList("Profile4", "Profile5", "Profile6");
        mManager.setSoundProfileAllowList(allow);

        boolean updated =
                waitForCondition(
                        () -> {
                            List<String> queries = mManager.getSoundProfileAllowList();
                            return queries != null
                                    && queries.containsAll(allow)
                                    && queries.size() == allow.size();
                        });
        Assert.assertTrue("Allow list not updated within timeout", updated);

        List<String> queries = mManager.getSoundProfileAllowList();
        Assert.assertNotNull(queries);
        Assert.assertEquals(3, queries.size());
        for (String a : allow) {
            Assert.assertTrue(queries.contains(a));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfileHandle() throws InterruptedException {
        PictureProfile profile = getTestPictureProfile("testGetPictureProfileHandle");

        mManager.createPictureProfile(profile);
        boolean created =
                waitForCondition(
                        () ->
                                mManager.getPictureProfile(
                                                profile.getProfileType(),
                                                profile.getName(),
                                                includeParams(false))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout for handle test.", created);
        PictureProfile createdProfile =
                mManager.getPictureProfile(
                        profile.getProfileType(), profile.getName(), includeParams(false));
        Assert.assertNotNull(createdProfile);
        String[] ids = {createdProfile.getProfileId()};
        List<PictureProfileHandle> ppHandle = new ArrayList<>();
        boolean handleRetrieved =
                waitForCondition(
                        () -> {
                            List<PictureProfileHandle> handles =
                                    mManager.getPictureProfileHandle(ids);
                            if (handles != null && !handles.isEmpty()) {
                                ppHandle.clear();
                                ppHandle.addAll(handles);
                                return true;
                            }
                            return false;
                        });
        Assert.assertTrue(
                "PictureProfileHandle was not retrieved within the timeout.", handleRetrieved);

        Assert.assertNotNull(ppHandle);
        Assert.assertEquals(1, ppHandle.size());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetPictureProfileHandles() throws InterruptedException {
        PictureProfile profile = getTestPictureProfile("testGetPictureProfileHandles");

        mManager.createPictureProfile(profile);
        boolean created =
                waitForCondition(
                        () ->
                                mManager.getPictureProfile(
                                                profile.getProfileType(),
                                                profile.getName(),
                                                includeParams(false))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout for handle test.", created);
        PictureProfile createdProfile =
                mManager.getPictureProfile(
                        profile.getProfileType(), profile.getName(), includeParams(false));
        Assert.assertNotNull(createdProfile);
        String[] ids = {createdProfile.getProfileId()};
        List<PictureProfileHandle> ppHandle = new ArrayList<>();
        boolean handleRetrieved =
                waitForCondition(
                        () -> {
                            List<PictureProfileHandle> handles =
                                    mManager.getPictureProfileHandles(ids);
                            if (handles != null && !handles.isEmpty()) {
                                ppHandle.clear();
                                ppHandle.addAll(handles);
                                return true;
                            }
                            return false;
                        });
        Assert.assertTrue(
                "PictureProfileHandle was not retrieved within the timeout.", handleRetrieved);

        Assert.assertNotNull(ppHandle);
        Assert.assertEquals(1, ppHandle.size());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testPictureProfileBuilder_addStreamStatusVariant() throws InterruptedException {
        String profileName = "testVariantProfile";
        PersistableBundle variantParams = new PersistableBundle();
        variantParams.putInt(PictureQuality.PARAMETER_BRIGHTNESS, 80);
        String status = PictureProfile.STATUS_HDR10;

        PictureProfile profile =
                new PictureProfile.Builder(profileName)
                        .setProfileType(PictureProfile.TYPE_APPLICATION)
                        .setPackageName(PACKAGE_NAME)
                        .addStreamStatusVariant(status, variantParams)
                        .build();

        Assert.assertNotNull("Profile should not be null", profile);

        java.util.Map<String, PersistableBundle> variants = profile.getStreamStatusVariants();
        Assert.assertNotNull("Variants map should not be null", variants);
        Assert.assertTrue("Variants should contain the added status", variants.containsKey(status));

        PersistableBundle retrievedParams = variants.get(status);
        Assert.assertEquals(80, retrievedParams.getInt(PictureQuality.PARAMETER_BRIGHTNESS));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfileHandles() throws InterruptedException {
        SoundProfile profile = getTestSoundProfile("testGetSoundProfileHandles");

        mManager.createSoundProfile(profile);
        boolean created =
                waitForCondition(
                        () ->
                                mManager.getSoundProfile(
                                                profile.getProfileType(),
                                                profile.getName(),
                                                includeParams(false))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout.", created);

        SoundProfile createdProfile =
                mManager.getSoundProfile(
                        profile.getProfileType(), profile.getName(), includeParams(false));
        assertNotNull(createdProfile);

        String[] ids = {createdProfile.getProfileId()};
        List<SoundProfileHandle> spHandle = new ArrayList<>();
        boolean handleRetrieved =
                waitForCondition(
                        () -> {
                            List<SoundProfileHandle> handles = mManager.getSoundProfileHandles(ids);
                            if (handles != null && !handles.isEmpty()) {
                                spHandle.clear();
                                spHandle.addAll(handles);
                                return true;
                            }
                            return false;
                        });
        Assert.assertTrue(
                "SoundProfileHandle was not retrieved within the timeout.", handleRetrieved);
        assertNotNull(spHandle);
        long soundProfileHandleId = spHandle.getFirst().getId();
        assertNotEquals(-1L, soundProfileHandleId);
        assertEquals(1, spHandle.size());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAndGetDefaultPictureProfile() throws InterruptedException {
        PictureProfile toCreate = getTestPictureProfile("testSetAndGetDefaultPictureProfile");
        mManager.createPictureProfile(toCreate);

        boolean created =
                waitForCondition(
                        () ->
                                mManager.getPictureProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(false))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout.", created);
        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));

        mManager.setDefaultPictureProfile(profile.getProfileId());
        boolean defaultSet =
                waitForCondition(
                        () -> {
                            PictureProfile defaultProfile = mManager.getDefaultPictureProfile();
                            return defaultProfile != null
                                    && toCreate.getName().equals(defaultProfile.getName());
                        });
        Assert.assertTrue("Default picture profile was not set within the timeout.", defaultSet);
        PictureProfile defaultProfile = mManager.getDefaultPictureProfile();
        assertNotNull(defaultProfile);
        assertEquals(toCreate.getName(), defaultProfile.getName());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAndGetDefaultSoundProfile() throws InterruptedException {
        SoundProfile toCreate = getTestSoundProfile("testSetAndGetDefaultSoundProfile");
        mManager.createSoundProfile(toCreate);

        boolean created =
                waitForCondition(
                        () ->
                                mManager.getSoundProfile(
                                                toCreate.getProfileType(),
                                                toCreate.getName(),
                                                includeParams(false))
                                        != null);
        Assert.assertTrue("Profile was not created within the timeout.", created);
        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));

        mManager.setDefaultSoundProfile(profile.getProfileId());
        boolean defaultSet =
                waitForCondition(
                        () -> {
                            SoundProfile defaultProfile = mManager.getDefaultSoundProfile();
                            return defaultProfile != null
                                    && toCreate.getName().equals(defaultProfile.getName());
                        });
        Assert.assertTrue("Default sound profile was not set within the timeout.", defaultSet);
        SoundProfile defaultSoundProfile = mManager.getDefaultSoundProfile();
        assertNotNull(defaultSoundProfile);
        assertEquals(toCreate.getName(), defaultSoundProfile.getName());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAndGetAutoPictureQualityEnabled() {
        boolean originalState = mManager.isAutoPictureQualityEnabled();

        // Attempt to toggle to the opposite state
        mManager.setAutoPictureQualityEnabled(!originalState);

        // Check if the state actually changed
        boolean newState = mManager.isAutoPictureQualityEnabled();

        if (newState == originalState) {
            // The state did NOT change. This indicates the HAL does not support
            // Auto Picture Quality, or the feature is currently unavailable.
            // We treat this as a PASS (or effectively a skip) because we cannot
            // force hardware support.
            return;
        }

        try {
            mManager.setAutoPictureQualityEnabled(originalState);
            Assert.assertEquals(
                    "Feature is supported but failed to restore original state",
                    originalState,
                    mManager.isAutoPictureQualityEnabled());
        } finally {
            // Restore even if assertion fails
            mManager.setAutoPictureQualityEnabled(originalState);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testSetMutedColor() {
        mManager.setMutedColor(Color.GREEN);
        mManager.setMutedColor(Color.BLACK);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testSetColorMuteEnabled() {
        mManager.setColorMuteEnabled(true);
        mManager.setColorMuteEnabled(false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testSetAndGetSuperResolutionEnabled() {
        boolean originalState = mManager.isSuperResolutionEnabled();

        // Attempt to toggle to the opposite state
        mManager.setSuperResolutionEnabled(!originalState);

        // Check if the state actually changed
        if (mManager.isSuperResolutionEnabled() == originalState) {
            // The state did NOT change. This indicates the HAL does not support
            // Super resolution, or the feature is currently unavailable.
            // We treat this as a PASS (or effectively a skip) because we cannot
            // force hardware support.
            return;
        }

        try {
            mManager.setSuperResolutionEnabled(originalState);
            Assert.assertEquals(originalState, mManager.isSuperResolutionEnabled());
        } finally {
            mManager.setSuperResolutionEnabled(originalState);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testSetAndGetAutoSoundQualityEnabled() {
        boolean originalState = mManager.isAutoSoundQualityEnabled();

        // Attempt to toggle to the opposite state
        mManager.setAutoSoundQualityEnabled(!originalState);

        // Check if the state actually changed
        if (mManager.isAutoSoundQualityEnabled() == originalState) {
            // The state did NOT change. This indicates the HAL does not support
            // Auto Sound Quality, or the feature is currently unavailable.
            // We treat this as a PASS (or effectively a skip) because we cannot
            // force hardware support.
            return;
        }

        try {
            mManager.setAutoSoundQualityEnabled(originalState);
            Assert.assertEquals(originalState, mManager.isAutoSoundQualityEnabled());
        } finally {
            mManager.setAutoSoundQualityEnabled(originalState);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetCurrentPictureProfileHandleForTvInput() {
        // TODO: add more test cases
        String dummyInputId = "com.example.tvinput/HDMI_DOES_NOT_EXIST";
        PictureProfileHandle handle =
                mManager.getCurrentPictureProfileHandleForTvInput(dummyInputId);

        Assert.assertEquals(
                "Should return PictureProfileHandle.NONE for invalid input",
                PictureProfileHandle.NONE.getId(),
                handle.getId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetAllPictureProfilesForTvInput() {
        // TODO: add more test cases
        String dummyInputId = "com.example.tvinput/HDMI1";

        List<PictureProfile> profiles = mManager.getAllPictureProfilesForTvInput(dummyInputId);
        Assert.assertNotNull("Resulting list should not be null", profiles);
        Assert.assertTrue("List should be empty for dummy input", profiles.isEmpty());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetCurrentPictureProfileForTvInput() {
        String dummyInputId = "com.example.tvinput/HDMI1";

        PictureProfile profile = mManager.getCurrentPictureProfileForTvInput(dummyInputId);

        Assert.assertNull("Should be null for dummy input", profile);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAmbientBacklightEnabled() {
        mManager.setAmbientBacklightEnabled(true);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsAmbientBacklightEnabled() {
        mManager.isAmbientBacklightEnabled();
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRegisterPictureProfileCallback() {
        mManager.registerPictureProfileCallback(
                Executors.newSingleThreadExecutor(),
                Mockito.mock(MediaQualityManager.PictureProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUnregisterPictureProfileCallback() {
        mManager.unregisterPictureProfileCallback(
                Mockito.mock(MediaQualityManager.PictureProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRegisterSoundProfileCallback() {
        mManager.registerSoundProfileCallback(
                Executors.newSingleThreadExecutor(),
                Mockito.mock(MediaQualityManager.SoundProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUnregisterSoundProfileCallback() {
        mManager.unregisterSoundProfileCallback(
                Mockito.mock(MediaQualityManager.SoundProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRegisterAmbientBacklightCallback() {
        mManager.registerAmbientBacklightCallback(
                Executors.newSingleThreadExecutor(), new MockAmbientBacklightCallback());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUnregisterAmbientBacklightCallback() {
        mManager.unregisterAmbientBacklightCallback(new MockAmbientBacklightCallback());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testOnAmbientBacklightEvent() {
        MockAmbientBacklightCallback callback = new MockAmbientBacklightCallback();
        AmbientBacklightMetadata metadata = createAmbientBacklightMetadata();

        AmbientBacklightEvent event = new AmbientBacklightEvent(
                MediaQualityManager.AMBIENT_BACKLIGHT_EVENT_METADATA, metadata);

        callback.onAmbientBacklightEvent(event);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAmbientBacklightSettings() {
        mManager.setAmbientBacklightSettings(mAmbientBacklightSettings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testAreParametersIncluded() {
        MediaQualityManager.ProfileQueryParams params =
                new MediaQualityManager.ProfileQueryParams.Builder()
                        .setParametersIncluded(true)
                        .build();

        assumeTrue(params.areParametersIncluded());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testAddActiveProcessingPictureListener() {
        mManager.addActiveProcessingPictureListener(
                Executors.newSingleThreadExecutor(), Mockito.mock(Consumer.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRemoveActiveProcessingPictureListener() {
        mManager.removeActiveProcessingPictureListener(Mockito.mock(Consumer.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testAddGlobalActiveProcessingPictureListener() {
        mManager.addGlobalActiveProcessingPictureListener(
                Executors.newSingleThreadExecutor(), Mockito.mock(Consumer.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testActiveProcessingPicture() {
        int id = 12;
        String profileId = "profileId";
        ActiveProcessingPicture app = new ActiveProcessingPicture(id, profileId);
        Assert.assertEquals(id, app.getId());
        Assert.assertEquals(profileId, app.getProfileId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetParameterCapabilities() {
        List<String> names = new ArrayList<>();
        names.add(PictureQuality.PARAMETER_BRIGHTNESS);
        mManager.getParameterCapabilities(names);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testUsesDisplayTechnology() {
        mManager.usesDisplayTechnology(MediaQualityContract.PANEL_TECHNOLOGY_OLED);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetEqualizerCapabilities() {
        EqualizerCapabilities caps = mManager.getEqualizerCapabilities();

        // If the device supports Equalizer, caps should not be null.
        // If it doesn't support it, it might return null or empty caps.
        if (caps != null) {
            Assert.assertNotNull(caps.getSupportedFrequenciesHz());
            Assert.assertTrue(caps.getMinLevelDb() <= caps.getMaxLevelDb());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetAndSetEqualizerSettings() {
        // Get real current settings
        EqualizerSettings currentSettings = mManager.getEqualizerSettings();

        // If EQ is not supported/active, this will be null
        if (currentSettings == null) {
            return;
        }

        Assert.assertNotNull(currentSettings.getBands());

        // Set the settings back.
        // (Modifying them is risky without knowing the capabilities,
        // so we verify the round-trip of valid settings works).
        try {
            mManager.setEqualizerSettings(currentSettings);

            // Verify we can fetch them again
            EqualizerSettings newSettings = mManager.getEqualizerSettings();
            Assert.assertNotNull(newSettings);
            Assert.assertEquals(currentSettings.getBands().size(), newSettings.getBands().size());
        } catch (Exception e) {
            Assert.fail("Failed to set valid equalizer settings: " + e.getMessage());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testEqualizerSettingsBuilderAndIntegration() {
        // Save original settings to restore later
        EqualizerSettings originalSettings = mManager.getEqualizerSettings();

        // Fetch capabilities to construct VALID bands for this specific device
        EqualizerCapabilities caps = mManager.getEqualizerCapabilities();

        // If the device reports no EQ capabilities, we fallback to a standalone
        // unit test of the Builder to ensure code coverage of addBands/build.
        if (caps == null || caps.getSupportedFrequenciesHz().isEmpty()) {
            verifyBuilderStandalone();
            return;
        }

        try {
            // Create a valid band using the first supported frequency
            int validFreq = caps.getSupportedFrequenciesHz().getFirst();
            int validGain = caps.getMinLevelDb();
            EqualizerBand band = new EqualizerBand(validFreq, validGain, 1.0f);

            // Test Builder, addBands, and build()
            EqualizerSettings.Builder builder = new EqualizerSettings.Builder();
            List<EqualizerBand> bandsToAdd = new ArrayList<>();
            bandsToAdd.add(band);

            // COVERAGE: Explicitly calling addBands
            builder.addBands(bandsToAdd);

            // COVERAGE: Explicitly calling build
            EqualizerSettings newSettings = builder.build();

            // Verify the object was constructed correctly before sending to service
            Assert.assertNotNull("Built settings should not be null", newSettings);
            Assert.assertFalse("Bands should not be empty", newSettings.getBands().isEmpty());
            Assert.assertEquals(
                    "Frequency mismatch in built object",
                    validFreq,
                    newSettings.getBands().getFirst().getFrequencyHz());

            mManager.setEqualizerSettings(newSettings);

            EqualizerSettings retrievedSettings = mManager.getEqualizerSettings();
            Assert.assertNotNull("Retrieved settings should not be null", retrievedSettings);

            // Find the band we modified (device might have multiple bands)
            boolean bandFound = false;
            for (EqualizerBand b : retrievedSettings.getBands()) {
                if (b.getFrequencyHz() == validFreq) {
                    Assert.assertEquals(
                            "Gain was not updated in service", validGain, b.getGainDb());
                    bandFound = true;
                    break;
                }
            }
            Assert.assertTrue(
                    "The band set via Builder was not found in retrieved settings", bandFound);

        } finally {
            // Restore original settings
            if (originalSettings != null) {
                try {
                    mManager.setEqualizerSettings(originalSettings);
                } catch (Exception e) {
                    Log.e(TAG, "setEqualizerSettings failed");
                }
            }
        }
    }

    /** Helper to verify Builder logic even if device hardware doesn't support EQ. */
    private void verifyBuilderStandalone() {
        EqualizerBand dummyBand = new EqualizerBand(1000, 0, 1.0f);
        List<EqualizerBand> bands = new ArrayList<>();
        bands.add(dummyBand);

        EqualizerSettings.Builder builder = new EqualizerSettings.Builder();
        builder.addBands(bands);
        EqualizerSettings settings = builder.build();

        Assert.assertNotNull(settings);
        Assert.assertEquals(1, settings.getBands().size());
        Assert.assertEquals(1000, settings.getBands().getFirst().getFrequencyHz());
    }

    private PictureProfile getTestPictureProfile(String methodName) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(PictureQuality.PARAMETER_BRIGHTNESS, 56);
        bundle.putInt(PictureQuality.PARAMETER_SATURATION, 23);
        bundle.putInt(PictureQuality.PARAMETER_CONTRAST, 87);

        return new PictureProfile.Builder("testName" + methodName)
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
                .setProfileType(SoundProfile.TYPE_APPLICATION)
                .setPackageName(PACKAGE_NAME)
                .setParameters(bundle)
                .build();
    }

    private MediaQualityManager.ProfileQueryParams includeParams(boolean include) {
        return new MediaQualityManager.ProfileQueryParams.Builder()
                .setParametersIncluded(include)
                .build();
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

    private AmbientBacklightMetadata createAmbientBacklightMetadata() {
        int[] zoneColors = {0xFF0000, 0x00FF00, 0x0000FF};
        AmbientBacklightMetadata metadata =
                new AmbientBacklightMetadata(
                        "com.example.test", // Example package name
                        1, // Example compression algorithm
                        1, // Example source
                        1, // Example color format
                        1, // Example horizontalZonesNumber
                        1, // Example verticalZonesNumber
                        zoneColors // Example zoneColors
                        );
        return metadata;
    }

    public static class MockAmbientBacklightCallback
            implements MediaQualityManager.AmbientBacklightCallback {
        public MockAmbientBacklightCallback() {
            super();
        }

        @Override
        public void onAmbientBacklightEvent(AmbientBacklightEvent event) {
            assertNotNull("Ambient backlight event is null", event);
            if (event.getEventType() == MediaQualityManager.AMBIENT_BACKLIGHT_EVENT_METADATA) {
                AmbientBacklightMetadata metadata = event.getMetadata();
                int compressionAlgorithm = metadata.getCompressionAlgorithm();
                int source = metadata.getSource();
                int colorFormat = metadata.getColorFormat();
                int horizontalZonesCount = metadata.getHorizontalZonesCount();
                int verticalZonesCount = metadata.getVerticalZonesCount();
                assertNotNull("Ambient Backlight Metadata is null", metadata);
                assertNotNull("Ambient Backlight package name is null", metadata.getPackageName());
                assertNotNull(
                        "Ambient Backlight Metadata zone color is null", metadata.getZoneColors());
            }
        }
    }

    public static class MockPictureProfileCallback
            extends MediaQualityManager.PictureProfileCallback {

        @Override
        public void onPictureProfileAdded(String profileId, PictureProfile profile) {
            super.onPictureProfileAdded(profileId, profile);
        }

        @Override
        public void onPictureProfileUpdated(String profileId, PictureProfile profile) {
            super.onPictureProfileUpdated(profileId, profile);
        }

        @Override
        public void onPictureProfileRemoved(String profileId, PictureProfile profile) {
            super.onPictureProfileRemoved(profileId, profile);
        }

        @Override
        public void onError(String profileId, int errorCode) {
            super.onError(profileId, errorCode);
        }

        @Override
        public void onParameterCapabilitiesChanged(
                String profileId, List<ParameterCapability> updatedCaps) {
            boolean isSupported, isMutable;
            int paramType;
            for (ParameterCapability paramCap : updatedCaps) {
                assertNotNull("param cap name is null", paramCap.getParameterName());
                assertNotNull("param cap is null", paramCap.getCapabilities());
                isSupported = paramCap.isSupported();
                isMutable = paramCap.isMutable();
                paramType = paramCap.getParameterType();
            }
            super.onParameterCapabilitiesChanged(profileId, updatedCaps);
        }
    }

    public static class MockSoundProfileCallback extends MediaQualityManager.SoundProfileCallback {
        @Override
        public void onSoundProfileAdded(String profileId, SoundProfile profile) {
            super.onSoundProfileAdded(profileId, profile);
        }

        @Override
        public void onSoundProfileUpdated(String profileId, SoundProfile profile) {
            super.onSoundProfileUpdated(profileId, profile);
        }

        @Override
        public void onSoundProfileRemoved(String profileId, SoundProfile profile) {
            super.onSoundProfileRemoved(profileId, profile);
        }

        @Override
        public void onError(String profileId, int errorCode) {
            super.onError(profileId, errorCode);
        }

        @Override
        public void onParameterCapabilitiesChanged(
                String profileId, List<ParameterCapability> updatedCaps) {
            boolean isSupported, isMutable;
            int paramType;
            for (ParameterCapability paramCap : updatedCaps) {
                assertNotNull("param cap name is null", paramCap.getParameterName());
                assertNotNull("param cap is null", paramCap.getCapabilities());
                isSupported = paramCap.isSupported();
                isMutable = paramCap.isMutable();
                paramType = paramCap.getParameterType();
            }
            super.onParameterCapabilitiesChanged(profileId, updatedCaps);
        }
    }

    /**
     * Waits for a condition to become true, polling at a regular interval.
     *
     * @param condition A supplier that returns true when the condition is met.
     * @return true if the condition was met within the timeout, false otherwise.
     */
    private boolean waitForCondition(Supplier<Boolean> condition) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < POLLING_TIMEOUT_MS) {
            if (condition.get()) {
                return true; // Condition met, exit immediately
            }
            Thread.sleep(POLLING_INTERVAL_MS); // Wait a short interval before next check
        }
        return false; // Condition was not met within the timeout
    }
}
