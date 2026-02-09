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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.tv.mediaquality.IMediaQuality;
import android.media.quality.ActiveProcessingPicture;
import android.media.quality.AmbientBacklightEvent;
import android.media.quality.AmbientBacklightMetadata;
import android.media.quality.AmbientBacklightSettings;
import android.media.quality.EqualizerBand;
import android.media.quality.EqualizerCapabilities;
import android.media.quality.EqualizerSettings;
import android.media.quality.IMediaQualityManager;
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
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

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
    private IMediaQuality mMediaQuality;
    private Object mOriginalService;
    private java.lang.reflect.Field mServiceField;
    private static final String SERVICE_FIELD_NAME = "mService";
    private static final int POLLING_TIMEOUT_MS = 5000; // 5 seconds max wait
    private static final int POLLING_INTERVAL_MS = 100; // Check every 0.1 seconds

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        mManager = context.getSystemService(MediaQualityManager.class);
        mAmbientBacklightSettings = createAmbientBacklightSettings();
        assumeTrue(mManager != null);
        mMediaQuality = Mockito.mock(IMediaQuality.class);

        // [Modified] Backup the real service ONLY. Do NOT inject mock here.
        // This ensures legacy tests use the real service and pass.
        try {
            mServiceField = MediaQualityManager.class.getDeclaredField(SERVICE_FIELD_NAME);
            mServiceField.setAccessible(true);
            mOriginalService = mServiceField.get(mManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to backup real service via reflection.", e);
        }
        if (mManager == null || !isSupported()) {
            return;
        }
    }

    private boolean isSupported() {
        return mManager.isSupported();
    }

    @After
    public void tearDown() throws InterruptedException {
        // [Modified] Restore real service BEFORE cleanup.
        if (mManager != null && mOriginalService != null && mServiceField != null) {
            try {
                mServiceField.set(mManager, mOriginalService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to restore real service during tearDown", e);
            }
        }
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
        Assert.assertEquals(queries.size(), 3);
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
    public void testChangeStreamStatusGrouping() throws Exception {
        String profileName = "testChangeStreamStatusGrouping";
        PictureProfile profile = getTestPictureProfile(profileName);
        Assert.assertNotNull(profile);

        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);
        mServiceField.set(mManager, mockService);
        try {
            String dummyId = "test_profile";
            String targetStatus = PictureProfile.STATUS_HDR10;

            mManager.changeStreamStatus(dummyId, targetStatus);

            Mockito.verify(mockService)
                    .changeStreamStatus(Mockito.eq(dummyId), Mockito.eq(targetStatus), anyInt());

            MediaQualityManager.PictureProfileCallback callback =
                    new MediaQualityManager.PictureProfileCallback() {
                        @Override
                        public void onPictureProfileUpdated(String id, PictureProfile profile) {}
                    };

            mManager.registerPictureProfileCallback(Executors.newSingleThreadExecutor(), callback);
            mManager.unregisterPictureProfileCallback(callback);

        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
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
        assertEquals(spHandle.size(), 1);
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
    public void testSetAutoPictureQualityEnabled() throws Exception {
        assumeTrue(mMediaQuality != null);
        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);

        mServiceField.set(mManager, mockService);

        try {
            mManager.setAutoPictureQualityEnabled(true);
            Mockito.verify(mockService).setAutoPictureQualityEnabled(Mockito.eq(true), anyInt());

            mManager.setAutoPictureQualityEnabled(false);
            Mockito.verify(mockService).setAutoPictureQualityEnabled(Mockito.eq(false), anyInt());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsAutoPictureQualityEnabled() throws Exception {
        assumeTrue(mMediaQuality != null);
        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);

        mServiceField.set(mManager, mockService);

        try {
            Mockito.when(mockService.isAutoPictureQualityEnabled(anyInt())).thenReturn(true);
            Assert.assertTrue(
                    "Should return true when service returns true",
                    mManager.isAutoPictureQualityEnabled());
            Mockito.verify(mockService).isAutoPictureQualityEnabled(anyInt());

            Mockito.when(mockService.isAutoPictureQualityEnabled(anyInt())).thenReturn(false);
            Assert.assertFalse(
                    "Should return false when service returns false",
                    mManager.isAutoPictureQualityEnabled());

            Mockito.verify(mockService, Mockito.times(2)).isAutoPictureQualityEnabled(anyInt());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testSetMutedColor() throws Exception {
        assumeTrue(mMediaQuality != null);
        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);

        mServiceField.set(mManager, mockService);

        try {
            int testColor = Color.GREEN;
            mManager.setMutedColor(testColor);
            Mockito.verify(mockService).setMutedColor(Mockito.eq(testColor), anyInt());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testSetColorMuteEnabled() throws Exception {
        assumeTrue(mMediaQuality != null);
        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);

        mServiceField.set(mManager, mockService);

        try {
            mManager.setColorMuteEnabled(true);
            Mockito.verify(mockService).setColorMuteEnabled(Mockito.eq(true), anyInt());

            mManager.setColorMuteEnabled(false);
            Mockito.verify(mockService).setColorMuteEnabled(Mockito.eq(false), anyInt());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetSuperResolutionEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoSrSupported()).thenReturn(true);
        doNothing().when(mMediaQuality).setAutoSrEnabled(anyBoolean());
        mManager.setSuperResolutionEnabled(false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsSuperResolutionEnable() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoSrSupported()).thenReturn(true);
        when(mMediaQuality.getAutoSrEnabled()).thenReturn(false);
        assertFalse(mManager.isSuperResolutionEnabled());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAutoSoundQualityEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoAqSupported()).thenReturn(true);
        doNothing().when(mMediaQuality).setAutoAqEnabled(anyBoolean());
        mManager.setAutoSoundQualityEnabled(false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsAutoSoundQualityEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoAqSupported()).thenReturn(true);
        when(mMediaQuality.getAutoAqEnabled()).thenReturn(false);
        assertFalse(mManager.isAutoSoundQualityEnabled());
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
    public void testUsesDisplayTechnology() throws Exception {
        assumeTrue(mMediaQuality != null);
        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);

        mServiceField.set(mManager, mockService);

        try {
            Mockito.when(mockService.usesDisplayTechnology(anyInt(), anyInt())).thenReturn(true);
            Assert.assertTrue(
                    "usesDisplayTechnology should return true when service returns true",
                    mManager.usesDisplayTechnology(MediaQualityContract.PANEL_TECHNOLOGY_OLED));

            Mockito.verify(mockService)
                    .usesDisplayTechnology(
                            Mockito.eq(MediaQualityContract.PANEL_TECHNOLOGY_OLED), anyInt());

            Mockito.when(mockService.usesDisplayTechnology(anyInt(), anyInt())).thenReturn(false);
            Assert.assertFalse(
                    "usesDisplayTechnology should return false when service returns false",
                    mManager.usesDisplayTechnology(MediaQualityContract.PANEL_TECHNOLOGY_OLED));

            Mockito.verify(mockService, Mockito.times(2)).usesDisplayTechnology(anyInt(), anyInt());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetEqualizerCapabilities() throws Exception {
        assumeTrue(mMediaQuality != null);
        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);

        int expectedMin = -15;
        int expectedMax = 15;
        List<Integer> expectedFreqs = List.of(60, 230, 910, 3600, 14000);
        boolean expectedQ = true;

        EqualizerCapabilities realCaps =
                new EqualizerCapabilities(expectedMin, expectedMax, expectedFreqs, expectedQ);

        Mockito.when(mockService.getEqualizerCapabilities(anyInt())).thenReturn(realCaps);

        mServiceField.set(mManager, mockService);
        try {
            EqualizerCapabilities result = mManager.getEqualizerCapabilities();
            Assert.assertNotNull("Capabilities should not be null", result);

            Assert.assertEquals("MinLevelDb should match", expectedMin, result.getMinLevelDb());
            Assert.assertEquals("MaxLevelDb should match", expectedMax, result.getMaxLevelDb());
            Assert.assertEquals(
                    "Frequencies should match", expectedFreqs, result.getSupportedFrequenciesHz());
            Assert.assertEquals("AdjustableQ should match", expectedQ, result.hasAdjustableQ());

            Mockito.verify(mockService).getEqualizerCapabilities(anyInt());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testGetEqualizerSettings() throws Exception {
        assumeTrue(mMediaQuality != null);

        EqualizerSettings.Builder builder = new EqualizerSettings.Builder();
        try {
            java.lang.reflect.Field field = builder.getClass().getDeclaredField("mBands");
            field.setAccessible(true);
            field.set(builder, new ArrayList<EqualizerBand>());
        } catch (Exception e) {
            // Note: The framework implementation may be using an immutable list
            // for bands, causing this exception.
        }

        EqualizerBand testBand = new EqualizerBand(1000, 5, 1.2f);
        builder.addBands(List.of(testBand));
        EqualizerSettings realSettings = builder.build();

        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);
        Mockito.when(mockService.getEqualizerSettings(anyInt())).thenReturn(realSettings);
        mServiceField.set(mManager, mockService);
        try {
            EqualizerSettings result = mManager.getEqualizerSettings();
            Assert.assertNotNull(result);
            Assert.assertEquals(1, result.getBands().size());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW_C)
    @Test
    public void testSetEqualizerSettings() throws Exception {
        assumeTrue(mMediaQuality != null);

        EqualizerSettings.Builder builder = new EqualizerSettings.Builder();
        try {
            java.lang.reflect.Field field = builder.getClass().getDeclaredField("mBands");
            field.setAccessible(true);
            field.set(builder, new ArrayList<EqualizerBand>());
        } catch (Exception e) {
            throw new RuntimeException("Reflection failed for EqualizerSettings.Builder", e);
        }

        EqualizerBand testBand = new EqualizerBand(1000, 5, 1.2f);
        builder.addBands(List.of(testBand));
        EqualizerSettings realSettings = builder.build();

        IMediaQualityManager mockService = Mockito.mock(IMediaQualityManager.class);
        Mockito.when(mockService.getEqualizerSettings(anyInt())).thenReturn(realSettings);

        mServiceField.set(mManager, mockService);
        try {
            EqualizerSettings result = mManager.getEqualizerSettings();
            Assert.assertNotNull("Settings should not be null", result);
            Assert.assertEquals("Band count mismatch", 1, result.getBands().size());
            Assert.assertEquals(
                    "Frequency mismatch", 1000, result.getBands().get(0).getFrequencyHz());

            Mockito.verify(mockService).getEqualizerSettings(anyInt());
        } finally {
            mServiceField.set(mManager, mOriginalService);
        }
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
