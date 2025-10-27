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

package android.security.cts.advancedprotection;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.os.Environment;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.util.ArraySet;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.internal.pm.pkg.parsing.ParsingPackageUtils;
import com.android.server.security.advancedprotection.config.AdvancedProtectionConfig;
import com.android.server.security.advancedprotection.config.Protections;
import com.android.server.security.advancedprotection.config.XmlParser;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

@RunWith(TestParameterInjector.class)
public abstract class BaseAdvancedProtectionFeatureTest extends BaseAdvancedProtectionTest {
    private static final String TAG = "BaseAdvancedProtectionFeatureTest";

    // System config constants.
    private static final String ADVANCED_PROTECTION_CONFIG_DIR = "advanced-protection-config";
    private static final String ETC_DIR = "etc";
    private static final String ADVANCED_PROTECTION_CONFIG_FILE_NAME =
            "advanced-protection-config.xml";
    private static final File SYSTEM_CONFIG_FILE =
            Environment.buildPath(
                    Environment.getRootDirectory(),
                    ETC_DIR,
                    ADVANCED_PROTECTION_CONFIG_DIR,
                    ADVANCED_PROTECTION_CONFIG_FILE_NAME);
    private static final ArraySet<Integer> sAvailableFeatureIdsInConfig = new ArraySet<>();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void setupClass() {
        readSystemConfigForFeatureAvailability();
    }

    protected abstract int getFeatureId();

    protected abstract String getFeatureName();

    protected abstract boolean isSupportedOnDevice();

    protected abstract void assertFeatureEnabled();

    protected abstract void assertFeatureDisabled();

    @RequiresFlagsDisabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures",
            })
    @Test
    public void testGetFeatures_apiV2Disabled(@TestParameter boolean deviceSupportsFeature) {
        assumeThat(isSupportedOnDevice(), is(deviceSupportsFeature));

        long numFeatures =
                mManager.getAdvancedProtectionFeatures().stream()
                        .filter(feature -> feature.getId() == getFeatureId())
                        .count();

        if (deviceSupportsFeature) {
            assertEquals(
                    "The "
                            + getFeatureName()
                            + " feature is supported on the device, but is not in the feature list",
                    1,
                    numFeatures);
        } else {
            assertEquals(
                    "The "
                            + getFeatureName()
                            + " feature is not supported on the device, so should not be in the"
                            + " feature list",
                    0,
                    numFeatures);
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testEnableProtection_apiV2Disabled() throws InterruptedException {
        assumeTrue(isSupportedOnDevice());

        setAdvancedProtectionEnabled(true);
        assertFeatureEnabled();
    }

    @RequiresFlagsDisabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testDisableProtection_apiV2Disabled() throws InterruptedException {
        assumeTrue(isSupportedOnDevice());

        setAdvancedProtectionEnabled(false);
        assertFeatureDisabled();
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures"
            })
    @Test
    public void testGetFeatures_apiV2Enabled(
            @TestParameter boolean deviceSupportsFeature,
            @TestParameter boolean featureIsAvailableInConfig) {
        assumeThat(isSupportedOnDevice(), is(deviceSupportsFeature));
        assumeThat(isFeatureAvailableInConfig(), is(featureIsAvailableInConfig));

        long numFeatures =
                mManager.getAdvancedProtectionFeatures().stream()
                        .filter(feature -> feature.getId() == getFeatureId())
                        .count();

        if (deviceSupportsFeature && featureIsAvailableInConfig) {
            assertEquals(
                    "The "
                            + getFeatureName()
                            + " feature is supported on the device and available in configs, but is"
                            + " not in the feature list",
                    1,
                    numFeatures);
        } else {
            String isSupportedOnDeviceString =
                    deviceSupportsFeature
                            ? "is supported on the device"
                            : "is not supported on the device";
            String isFeatureAvailableInConfigString =
                    featureIsAvailableInConfig
                            ? "is available in configs"
                            : "is not available in configs";
            assertEquals(
                    "The "
                            + getFeatureName()
                            + " feature "
                            + isSupportedOnDeviceString
                            + " and "
                            + isFeatureAvailableInConfigString
                            + ", so should not be in the feature list",
                    0,
                    numFeatures);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testEnableProtection_apiV2Enabled() throws InterruptedException {
        assumeTrue(isSupportedOnDevice());
        assumeTrue(isFeatureAvailableInConfig());

        setAdvancedProtectionEnabled(true);
        assertFeatureEnabled();
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testDisableProtection_apiV2Enabled() throws InterruptedException {
        assumeTrue(isSupportedOnDevice());
        assumeTrue(isFeatureAvailableInConfig());

        setAdvancedProtectionEnabled(false);
        assertFeatureDisabled();
    }

    /** Note that the config is guarded by android.security.Flags.FLAG_AAPM_API_V2. */
    private boolean isFeatureAvailableInConfig() {
        return sAvailableFeatureIdsInConfig.contains(getFeatureId());
    }

    private static void readSystemConfigForFeatureAvailability() {
        if (!SYSTEM_CONFIG_FILE.exists()) {
            Log.d(TAG, "System config file doesn't exist");
            return;
        }
        try (InputStream in = new FileInputStream(SYSTEM_CONFIG_FILE)) {
            AdvancedProtectionConfig systemConfig = XmlParser.read(in);
            if (systemConfig == null) {
                Log.d(TAG, "System config couldn't be parsed");
                return;
            }
            List<Protections.Protection> protections =
                    systemConfig.getAvailableProtections().getProtection();
            for (int i = 0; i < protections.size(); i++) {
                Protections.Protection protection = protections.get(i);
                String featureFlag = protection.getFeatureFlag();
                if (featureFlag == null || isFeatureFlagEnabled(featureFlag)) {
                    String protectionIdString = protection.getId().getRawName();
                    sAvailableFeatureIdsInConfig.add(
                            AdvancedProtectionManager.featureStringToId(protectionIdString));
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to read advanced protection system config: " + e);
        }
    }

    private static boolean isFeatureFlagEnabled(String featureFlag) {
        boolean negated = false;
        if (featureFlag.startsWith("!")) {
            negated = true;
            featureFlag = featureFlag.substring(1).strip();
        }
        Boolean featureFlagValue = ParsingPackageUtils.getAconfigFlags().getFlagValue(featureFlag);
        if (featureFlagValue == null) {
            throw new IllegalArgumentException("Invalid feature flag: " + featureFlag);
        }
        return featureFlagValue != negated;
    }
}
