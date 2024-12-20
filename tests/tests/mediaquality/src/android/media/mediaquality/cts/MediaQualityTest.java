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

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.quality.MediaQualityContract.PictureQuality;
import android.media.quality.MediaQualityManager;
import android.media.quality.PictureProfile;
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

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaQualityTest {
    private MediaQualityManager mManager;
    private static final String PACKAGE_NAME = "android.media.mediaquality.cts";

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();

        mManager = context.getSystemService(MediaQualityManager.class);
        assumeTrue(mManager != null);
        if (mManager == null || !isSupported()) {
            return;
        }
    }

    @After
    public void tearDown() {
        List<PictureProfile> profiles =
                mManager.getPictureProfilesByPackage(PACKAGE_NAME, includeParams(false));
        for (PictureProfile profile : profiles) {
            mManager.removePictureProfile(profile.getProfileId());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetAvailablePictureProfiles() throws Exception {
        mManager.getAvailablePictureProfiles(null);
    }

    private boolean isSupported() {
        return mManager.isSupported();
    }

    @Test
    public void testCreatePictureProfile() {
        Exception exception = null;
        try {
            PictureProfile toCreate = getPictureProfile("createPictureProfile");

            mManager.createPictureProfile(toCreate);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @Test
    public void testUpdatePictureProfile() {
        PictureProfile toCreate = getPictureProfile("updatePictureProfile");
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

    @Test
    public void testRemovePictureProfile() {
        PictureProfile toCreate = getPictureProfile("removePictureProfile");

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

    @Test
    public void testGetPictureProfile() {
        PictureProfile toCreate = getPictureProfile("getPictureProfile");

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

    @Test
    public void testGetPictureProfilesByPackage() {
        PictureProfile toCreate = getPictureProfile("getPictureProfilesByPackage");

        mManager.createPictureProfile(toCreate);
        List<PictureProfile> profiles =
                mManager.getPictureProfilesByPackage(
                        toCreate.getPackageName(), includeParams(false));
        Assert.assertNotNull(profiles);
        for (PictureProfile profile : profiles) {
            Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        }
    }

    private PictureProfile getPictureProfile(String methodName) {
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

    private MediaQualityManager.ProfileQueryParams includeParams(boolean include) {
        return new MediaQualityManager.ProfileQueryParams(include);
    }
}
