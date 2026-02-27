/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.appsecurity.cts;

import static android.appsecurity.cts.PackageInstallTestUtils.assertInstallOnDeviceFails;
import static android.appsecurity.cts.PackageInstallTestUtils.assertInstallOnDeviceFromBuildSucceeds;
import static android.appsecurity.cts.PackageInstallTestUtils.assertInstallOnDeviceSucceeds;

import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;
import android.security.Flags;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.testtype.junit4.DeviceParameterizedRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host side tests to verify the platform behaves as expected when verifying the new v3.2 hybrid
 * signature scheme and interactions with other apps signed with the new scheme.
 */
@Presubmit
@RunWith(DeviceParameterizedRunner.class)
public class HybridSignatureVerificationTest extends BaseAppSecurityTest {
    private static final String TEST_PACKAGE = "android.appsecurity.cts.tinyapp";
    private static final String COMPANION_PACKAGE = "android.appsecurity.cts.tinyapp_companion";
    private static final String COMPANION_PACKAGE2 = "android.appsecurity.cts.tinyapp_companion2";
    private static final String COMPANION_PACKAGE3 = "android.appsecurity.cts.tinyapp_companion3";
    private static final String DEVICE_TEST_APK = "CtsV32HybridSigningSchemeTest.apk";
    private static final String DEVICE_TEST_PACKAGE = "android.appsecurity.cts.v32hybridtests";
    private static final String DEVICE_TEST_CLASS = DEVICE_TEST_PACKAGE + ".V32HybridTests";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    @Before
    public void setUp() throws Exception {
        Utils.prepareSingleUser(getDevice());
        uninstallPackages();
        assertInstallOnDeviceFromBuildSucceeds(DEVICE_TEST_APK, getDevice(), getBuild());
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackages();
    }

    private void uninstallPackages() throws Exception {
        getDevice().uninstallPackage(TEST_PACKAGE);
        getDevice().uninstallPackage(DEVICE_TEST_PACKAGE);
        getDevice().uninstallPackage(COMPANION_PACKAGE);
        getDevice().uninstallPackage(COMPANION_PACKAGE2);
        getDevice().uninstallPackage(COMPANION_PACKAGE3);
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_originalKeyAndHybridV32Enabled_succeeds() throws Exception {
        // The APK for this test is signed with the RSA-2048 as the original key and the RSA-2048_2
        // and ML-DSA-65 keys in the hybrid block; this test verifies that the platform allows the
        // update from the original signer and uses the hybrid block for verification.
        assertInstallOnDeviceSucceeds("v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048-ver2.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_originalKeyAndV32HybridConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_originalKeyAndHybridV32Disabled_succeeds() throws Exception {
        // The APK for this test is the same as above and is intended to verify that the hybrid
        // block is ignored and the original signing key in the v3.0 block is used when the v3.2
        // hybrid flag is disabled.
        assertInstallOnDeviceSucceeds("v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048-ver2.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_originalKeyAndHybridV32Disabled");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3OriginalV31RotatedV32HybridEnabled_succeeds() throws Exception {
        // The APK for this test is signed with the RSA-2048 as the original key, the RSA-2048_2 as
        // the rotated key in the v3.1 block, and the RSA-2048_3 and ML-DSA-65 keys in the hybrid
        // block; this test verifies that the platform allows the update from the v3.1 rotated
        // signer and uses the hybrid block for verification.
        assertInstallOnDeviceSucceeds("v31-rsa-2048_2-tgt-33-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_3-mldsa-v31-rsa-2048_2-v3-rsa-2048-ver2.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV31RotatedV32HybridConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3OriginalV31RotatedV32HybridDisabled_succeeds() throws Exception {
        // The APK for this test is the same as above and is intended to verify that the hybrid
        // block is ignored and the rotated signing key in the v3.1 block is used when the v3.2
        // hybrid flag is disabled.
        assertInstallOnDeviceSucceeds("v31-rsa-2048_2-tgt-33-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_3-mldsa-v31-rsa-2048_2-v3-rsa-2048-ver2.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV31RotatedV32HybridDisabled");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_hybridWithOnlyOneSigner_fails() throws Exception {
        // The hybrid block requires two signers targeting a platform release; this test verifies
        // if the hybrid block only has a single signer, the install fails.
        assertInstallOnDeviceFails("v32-mldsa-only-hybrid-sig-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_hybridTwoClassicalSigners_fails() throws Exception {
        // The hybrid block requires one classical and one PQC signer targeting a platform release;
        // this test verifies if a hybrid block contains two classical signers, the install fails.
        assertInstallOnDeviceFails("v32-two-classical-sigs-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_hybridTwoPqcSigners_fails() throws Exception {
        // The hybrid block requires one classical and one PQC signer targeting a platform release;
        // this test verifies if a hybrid block contains two PQC signers, the install fails.
        assertInstallOnDeviceFails("v32-two-pqc-sigs-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_hybridSignersTargetDifferentSdkRanges_fails() throws Exception {
        // The hybrid block requires each signer to target the same SDK range; this test verifies if
        // the signers target different SDK ranges, the install fails.
        assertInstallOnDeviceFails("v32-diff-target-sdk-range-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_hybridSignersDifferentNumberOfSignersInLineage_fails() throws Exception {
        // The hybrid block requires each signer to have the same signing history; this test
        // verifies if the number of signers in the lineage between the two signers is different,
        // the install fails.
        assertInstallOnDeviceFails("v32-diff-num-sigs-in-lineage-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_hybridSignersDifferentSignersInLineage_fails() throws Exception {
        // The hybrid block requires each signer to have the same signing history; this test
        // verifies if lineage size is the same but the signers in the lineage are different between
        // the two hybrid signers, the install fails.
        assertInstallOnDeviceFails("v32-diff-sigs-in-lineage-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_hybridSignersDifferentCapabilitiesInLineage_fails() throws Exception {
        // The hybrid block requires each signer to have the same signing history and capabilities
        // assigned to each of the previous signers. This test verifies if the lineage has all the
        // same previous signers but different capabilities granted to them, the install fails.
        assertInstallOnDeviceFails("v32-diff-caps-in-lineage-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_onlyHybridBlock_succeeds() throws Exception {
        // If a signer is targeting the latest release, it can be signed by only the v3.2 hybrid
        // signature block since that would be the first verified by the platform. This test
        // verifies an APK signed only with the v3.2 signature scheme successfully installs on a
        // platform with support for the hybrid block.
        assertInstallOnDeviceSucceeds("v32-rsa-mldsa-no-other-signers.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(), DEVICE_TEST_PACKAGE, DEVICE_TEST_CLASS, "testV32_onlyHybridBlock");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3OriginalV32TargetsMaxInt_succeeds() throws Exception {
        // The v3.2 hybrid block supports SDK targeting similar to the other v3 schemes; if the v3.2
        // block is targeting an SDK range later than that installed on the device, then the install
        // should fall back to one of the previous v3 scheme blocks. This APK uses a v3.2 block that
        // is targeting an SDK range that should not be satisfied by any platform, so the original
        // key in the v3.0 block should be used for verification instead.
        assertInstallOnDeviceSucceeds("v32-tgt-max-int-v3-rsa-2048.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV32TargetsMaxInt");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3OriginalV32Stripped_fails() throws Exception {
        // The v3 and v3.1 blocks have an additional attribute that notes the minSdkVersion that the
        // v3.2 block is targeting; if the v3.2 block is stripped, then the platform should detect
        // this from the additional attribute and block the install. This test verifies stripping
        // protection works when written to the v3.0 signature block.
        assertInstallOnDeviceFails("v32-sig-stripped-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3OriginalV31RotatedV32Stripped_fails() throws Exception {
        // The v3 and v3.1 blocks have an additional attribute that notes the minSdkVersion that the
        // v3.2 block is targeting; if the v3.2 block is stripped, then the platform should detect
        // this from the additional attribute and block the install. This test verifies stripping
        // protection works when written to the v3.1 signature block.
        assertInstallOnDeviceFails("v32-sig-stripped-v31-rsa-2048_2-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32InstallV3ValidMinMaxStripAttr_succeeds() throws Exception {
        // The minimum and maximum SDK versions being targeted by the hybrid block should be written
        // to the v3.0 and v3.1 signature block for stripping / tampering protection. While these
        // attributes should not be verified if the v3.2 block is used for the install, this test
        // verifies an APK that includes these attributes in the v3.0 block successfully installs.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-min-max-strip-attr-vaid.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_originalKeyAndV32HybridConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SdkRangeAbovePlatformV3InRange_succeeds() throws Exception {
        // The v3.2 block is intended to eventually transition away from the hybrid signing back to
        // a single signer in the v3.0 / v3.1 block. To prevent tampering, the hybrid block uses
        // both a min and max SDK version stripping protection attribute to ensure neither of the
        // block's SDK range values are modified. This test verifies when the v3.2 block targets
        // an SDK range beyond that of the platform, both of the attributes successfully confirm the
        // values from the v3.2 block, and the APK installs with the original signer.
        assertInstallOnDeviceSucceeds(
                "v32-min-max-tgt-above-platform-v3-rsa-2048.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV32TargetsMaxInt");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SdkRangeAbovePlatformV31InRange_succeeds() throws Exception {
        // Similar to above, if the v3.2 block targets an SDK range beyond that of the platform, it
        // should fall back to the previous version that does target the platform. This test
        // verifies the APK is verified with the v3.1 signature when that targets the device SDK.
        assertInstallOnDeviceSucceeds(
                "v32-min-max-tgt-above-platform-v31-rsa-2048_2-v3-rsa-2048.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV31RotatedV32SdkRangeOutsideDeviceSdk");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SdkRangeBelowPlatformV31InRange_succeeds() throws Exception {
        // Similar to above, if the v3.2 block targets an SDK range below that of the platform, it
        // should fall back to the previous version that does target the platform. This test is
        // intended to verify the scenario where the hybrid block was used for the PQC transition
        // and the package moved to a single signer config in a later release. Since later platform
        // SDK versions with support are not yet available, this APK targets SDK versions 28-35
        // for the v3.2 hybrid block to force the v3.1 signer to be used and to verify the stripping
        // protection attributes.
        assertInstallOnDeviceSucceeds(
                "v32-min-max-tgt-below-platform-v31-rsa-2048_2-v3-rsa-2048.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV31RotatedV32SdkRangeOutsideDeviceSdk");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SdkRangeAbovePlatformMinSdkAttrMissing_fails() throws Exception {
        // Whenever a V3.2 block is present, the stripping protection additional attributes should
        // be written to the v3.0 and v3.1 signature blocks. While a missing max SDK version
        // attribute implies that the max SDK version of the v3.2 block is all platform versions,
        // the platform cannot determine the intended minimum SDK version if that attribute is
        // missing but the maximum SDK version stripping protection attribute is present; in that
        // case, the platform should block the install.
        assertInstallOnDeviceFails(
                "v32-min-max-tgt-above-platform-v3-min-attr-missing.apk", getDevice());
        assertInstallOnDeviceFails(
                "v32-min-max-tgt-above-platform-v31-v3-min-attr-missing.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SdkRangeAbovePlatformMinSdkAttrMismatch_fails() throws Exception {
        // The v3.2 hybrid stripping protection attributes are intended to ensure that if the v3.2
        // signature is skipped due to it targeting an SDK range outside that of the platform, the
        // attributes will confirm that the SDK range of the block was not modified. If the minimum
        // SDK version of the v3.2 block is modified, the stripping protection attribute in the v3.0
        // or v3.1 signer should catch this and block the install.
        assertInstallOnDeviceFails(
                "v32-min-max-tgt-above-platform-v3-min-attr-mismatch.apk", getDevice());
        assertInstallOnDeviceFails(
                "v32-min-max-tgt-above-platform-v31-v3-min-attr-mismatch.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SdkRangeAbovePlatformMaxSdkAttrMismatch_fails() throws Exception {
        // The v3.2 hybrid stripping protection attributes are intended to ensure that if the v3.2
        // signature is skipped due to it targeting an SDK range outside that of the platform, the
        // attributes will confirm that the SDK range of the block was not modified. If the maximum
        // SDK version of the v3.2 block is modified, the stripping protection attribute in the v3.0
        // or v3.1 signer should catch this and block the install.
        assertInstallOnDeviceFails(
                "v32-min-max-tgt-above-platform-v3-max-attr-mismatch.apk", getDevice());
        assertInstallOnDeviceFails(
                "v32-min-max-tgt-above-platform-v31-v3-max-attr-mismatch.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SdkRangeAbovePlatformMaxSdkMismatch_fails() throws Exception {
        // The v3.2 signers must target the same minimum and maximum SDK version. If the v3.2
        // signers target an SDK version beyond that of the device, then the min and max SDK values
        // targeted by the signers will be stored for verification against the stripping / tampering
        // protection attributes in the v3.0 and v3.1 signature blocks. This test verifies if the
        // max SDK versions being targeted by the hybrid signers are not the same, then the install
        // is blocked.
        assertInstallOnDeviceFails(
                "v32-min-max-tgt-above-platform-max-mismatch-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32InvalidSignature_fails() throws Exception {
        // During signature verification, if any errors are encountered with the latest signature
        // version being verified, the verification should immediately fail instead of attempting
        // to verify an earlier signature scheme. This test verifies that an invalid signature
        // in the V3.2 block causes verification to immediately fail even though the V3.0 block
        // has a valid signature.
        assertInstallOnDeviceFails("v32-mldsa-invalid-sig-rsa-2048_2-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceFails("v32-mldsa-rsa-2048_2-invalid-sig-v3-rsa-2048.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3V32UpdateWithSameHybridConfig_succeeds() throws Exception {
        // This is the standard update case from an APK signed with an original signing key and a
        // v3.2 block updated to an APK signed with the same signing config.
        assertInstallOnDeviceSucceeds("v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048-ver2.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_originalKeyAndV32HybridConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3v31v32UpdateWithSameHybridConfig_succeeds() throws Exception {
        // This is the other standard update case from an APK signed with a rotated signing key and
        // a v3.2 block updated to an APK signed with the same signing config.
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_3-mldsa-v31-rsa-2048_2-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_3-mldsa-v31-rsa-2048_2-v3-rsa-2048-ver2.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV31RotatedV32HybridConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32UpdateToSingleSigner_succeeds() throws Exception {
        // The v3.2 signature scheme is intended as a transition to PQC signing. Once a package is
        // ready to be rotated back to a single signer config, the platform should allow the install
        // as long as the previous hybrid signers are both in the lineage and the APK is signed with
        // a new key that was not part of the hybrid config.
        // Note, this test does not yet verify the transition to a single PQC signer config since
        // this is not yet supported on the platform.
        assertInstallOnDeviceSucceeds("v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v31-rsa-2048_3-mldsa-65-rsa-2048_2-in-por-v3-rsa-2048-ver2.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV32HybridV31RotatedConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32UpdateToSharedSigner_fails() throws Exception {
        // The v3.2 signature scheme requires that a new signing key be used whenever rotating to a
        // new single signer or hybrid config. This test verifies the platform properly rejects
        // an update if either of the hybrid keys are used as the new single signing identity or as
        // one of the signers in the rotated hybrid config.
        assertInstallOnDeviceSucceeds("v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048.apk", getDevice());

        assertInstallOnDeviceFails(
                "v31-rsa-2048_2-mldsa-65-in-por-v3-rsa-2048-ver2.apk", getDevice());
        assertInstallOnDeviceFails(
                "v31-mldsa-65-rsa-2048_2-in-por-v3-rsa-2048-ver2.apk", getDevice());
        assertInstallOnDeviceFails(
                "v32-mldsa-65-rsa-2048_3-rsa-2048_2-in-por-v3-rsa-2048-ver2.apk", getDevice());
        assertInstallOnDeviceFails(
                "v32-mldsa-87-rsa-2048_2-mldsa-65-in-por-v3-rsa-2048-ver2.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3OriginalToV32SameClassicalSigner_fails() throws Exception {
        // The v3.2 signature scheme requires that the signing key be rotated when moving from the
        // v3 / v3.1 single signer config to a hybrid signing config. This test verifies if the
        // original classical key is reused as part of the hybrid signing config, then the platform
        // will not allow the update.
        assertInstallOnDeviceSucceeds("v3-rsa-2048.apk", getDevice());

        assertInstallOnDeviceFails("v32-rsa-mldsa-no-other-signers.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32RollbackToV3OriginalSigner_succeeds() throws Exception {
        // The ROLLBACK capability should continue to function as expected when a package signed
        // with a hybrid signature and the original signing key granted the ROLLBACK capability is
        // installed. However, an update APK signed with one of the hybrid keys should fail the
        // roll back attempt, even if the hybrid key has been granted the ROLLBACK capability.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-65-rsa-2048_2-rollback-v3-rsa-2048-rollback.apk", getDevice());

        assertInstallOnDeviceFails("v31-rsa-2048_2-tgt-33-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds("v3-rsa-2048.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(), DEVICE_TEST_PACKAGE, DEVICE_TEST_CLASS, "testV32_v3OriginalConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32RollbackToV31RotatedSigner_succeeds() throws Exception {
        // Similar to the previous ROLLBACK test, this test verifies that an attempt to roll back to
        // a v3.1 single signer that has been granted the ROLLBACK capability should succeed.
        assertInstallOnDeviceSucceeds(
                "v32-rsa-2048_3-mldsa-v31-rsa-2048_2-rollback-v3-rsa-2048.apk", getDevice());

        assertInstallOnDeviceSucceeds("v31-rsa-2048_2-tgt-33-v3-rsa-2048.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(), DEVICE_TEST_PACKAGE, DEVICE_TEST_CLASS, "testV32_v31RotatedConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32ToV32RotatedConfig_succeeds() throws Exception {
        // A hybrid signing config can be rotated to a new hybrid config by adding the current
        // hybrid signers to the lineage and using this lineage to attest to the new hybrid signers.
        // This test verifies that a rotation from an initial hybrid config to a new hybrid config
        // is successful only when both original hybrid signers are in the lineage.
        assertInstallOnDeviceSucceeds("v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048.apk", getDevice());

        assertInstallOnDeviceFails(
                "v32-mldsa-87-rsa-2048_3-mldsa-65-in-por-v3-rsa-2048-ver2.apk", getDevice());
        assertInstallOnDeviceFails(
                "v32-mldsa-87-rsa-2048_3-rsa-2048_2-in-por-v3-rsa-2048-ver2.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-rsa-2048_2-mldsa-65-in-por-v3-rsa-2048-ver2.apk",
                getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v3OriginalV32RotatedConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32UpdateHybridToSingleToHybrid_succeeds() throws Exception {
        // Verifies the full transition path where a developer adopts a hybrid signing config,
        // rotates back to a classical signer, then rotates forward to a new hybrid config.
        assertInstallOnDeviceSucceeds("v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v31-rsa-2048_3-mldsa-65-rsa-2048_2-in-por-v3-rsa-2048-ver2.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_4-rsa-2048_2_3-mldsa-65-in-por-v3-rsa-2048-ver3.apk",
                getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_v32UpdateHybridToSingleToHybridConfig");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32UpdateToV31SingleMissingHybridSignerInLineage_fails() throws Exception {
        // When an APK is signed with a hybrid config and attempts to rotate to a single signer
        // config, both current hybrid signers must be in the lineage of the update APK to ensure
        // that the developer was in control of both hybrid keys when the rotation occurred. This
        // test verifies that if either of the hybrid signers are missing from the lineage when
        // rotating from a hybrid config to a single classical config, the update is not allowed.
        assertInstallOnDeviceSucceeds("v32-rsa-2048_2-mldsa-tgt-36-v3-rsa-2048.apk", getDevice());

        assertInstallOnDeviceFails(
                "v31-rsa-2048_3-mldsa-65-in-por-v3-rsa-2048-ver2.apk", getDevice());
        assertInstallOnDeviceFails(
                "v31-rsa-2048_3-rsa-2048_2-in-por-v3-rsa-2048-ver2.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SharedUserIdSameV32Config_succeeds() throws Exception {
        // When two apps share the same current signing config, both should successfully install and
        // join the sharedUserId.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-shUid.apk", getDevice());
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-shUid.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SharedUserIdDifferentV32Config_fails() throws Exception {
        // The first app installed that declares the sharedUserId sets its signing identity; if a
        // subsequent app attempts to join with a completely different hybrid signing identity, the
        // install should fail.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-shUid.apk", getDevice());
        assertInstallOnDeviceFails("v32-mldsa-87-rsa-2048_3-shUid.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3ToV32SharedUserId_onlyMatchingConfigSucceeds() throws Exception {
        // To join a sharedUserid, the joining app must either be signed with the same key, or a key
        // in the lineage of the apps within the sharedUserId that has been granted the
        // SHARED_USER_ID capability. For a hybrid signed app, this requires that either both of
        // the hybrid keys are the current signer of the app requesting to join, or that the app
        // requesting to join is signed with a key that is in the lineage of the sharedUserId and
        // is still granted the SHARED_USER_ID capability.
        // This APK establishes the sharedUserId with signing identity rsa-2048.
        assertInstallOnDeviceSucceeds("v3-rsa-2048-shUid.apk", getDevice());

        // This verifies a package signed with a hybrid config that matches the identity of the
        // sharedUserId can join. The sharedUserId identity is now rsa-2048 -> (rsa-2048_2 +
        // mldsa-65).
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-shUid.apk", getDevice());

        // A hybrid signed package with only one of the hybrid keys for the sharedUserId should fail
        // to install and join the sharedUserId.
        assertInstallOnDeviceFails(
                "v32-mldsa-87-rsa-2048_2-v3-rsa-2048-companion3-shUid.apk", getDevice());
        assertInstallOnDeviceFails(
                "v32-mldsa-65-rsa-2048_3-v3-rsa-2048-companion3-shUid.apk", getDevice());

        // An APK signed with a single key from the current hybrid identity should fail to install.
        assertInstallOnDeviceFails("v31-rsa-2048_2-v3-rsa-2048-companion3-shUid.apk", getDevice());

        // A rotation to a new hybrid signing identity should be able to join the sharedUserId since
        // the current hybrid signers for the sharedUserId are in the lineage. The sharedUserId
        // identity is now rsa-2048 -> rsa-2048_2 -> mldsa-65 -> (rsa-2048_3 + mldsa-87).
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-mldsa-65-rsa-2048_2-in-por-v3-rsa-2048-companion2-shUid"
                        + ".apk",
                getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SharedUserIdOlderIdentitiesJoiningLater_succeeds() throws Exception {
        // When a package that is part of a sharedUserId is installed, the signing identity of the
        // sharedUserid is merged with that of the installed package. If a package is installed that
        // has the latest signing identity for the sharedUserId, packages signed with older signing
        // identities that are still in the lineage of the sharedUserId should still be able to
        // install and join.
        // This sets the identity of the sharedUserId to rsa-2048 -> rsa-2048_2 -> mldsa-65 ->
        // rsa-2048_3 -> (rsa-2048_4 + mldsa-87).
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_4-rsa-2048_2_3-mldsa-65-in-por-v3-rsa-2048-companion3"
                        + "-shUid.apk",
                getDevice());

        // An APK signed with only the classical key from the hybrid block should fail to install.
        assertInstallOnDeviceFails("v3-rsa-2048_4-companion-shUid.apk", getDevice());

        // An APK signed with the original signer in the lineage should still install as long as the
        // original signer been granted the SHARED_USER_ID capability.
        assertInstallOnDeviceSucceeds("v3-rsa-2048-shUid.apk", getDevice());

        // A hybrid signed APK that is signed with the previous two hybrid keys in the lineage
        // should install as long as the two signers are still granted the SHARED_USER_ID
        // capability.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-shUid.apk", getDevice());

        // An APK that has rotated from the hybrid block back to a classical single signer that is
        // part of the sharedUserId's lineage should install.
        assertInstallOnDeviceSucceeds(
                "v31-rsa-2048_3-mldsa-rsa-2048_2-in-por-v3-rsa-2048-companion2-shUid.apk",
                getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32SharedUserIdHybridToSingleToHybrid_succeeds() throws Exception {
        // The V3.2 hybrid block can be rotated to / from a single signer config at any time. This
        // test verifies that if an APK is part of a sharedUserId with a hybrid signature, moving
        // between single and hybrid signed APKs can still join the sharedUserId.
        // The sharedUserId starts with rsa-2048 -> (rsa-2048_2 + mldsa-65).
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-shUid.apk", getDevice());

        // An APK signed with a key rotated from the hybrid back to the single signer config should
        // succeed as long as both hybrid keys are in the lineage. The sharedUserId identity is
        // rsa-2048 -> rsa_2-2048 -> mldsa-65 -> rsa-2048_3
        assertInstallOnDeviceSucceeds(
                "v31-rsa-2048_3-mldsa-rsa-2048_2-in-por-v3-rsa-2048-companion2-shUid.apk",
                getDevice());

        // An APK signed with the current single signer of the sharedUserId should be able to join.
        assertInstallOnDeviceSucceeds("v3-rsa-2048_3-shUid.apk", getDevice());

        // An APK that rotates back to a hybrid signing config should install successfully as long
        // as the sharedUserId's identity attests to the rotation to the hybrid block.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_4-rsa-2048_2_3-mldsa-65-in-por-v3-rsa-2048-companion3"
                        + "-shUid.apk",
                getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32HybridSharedUserIdCapabilityRevoked_failsToInstall() throws Exception {
        // If a key is compromised, or if a developer just doesn't want to allow older keys to
        // continue joining the sharedUserId, the SHARED_USER_ID capability can be revoked from the
        // key. This test verifies that if this capability is revoked from a previous hybrid signer,
        // then an APK signed with one of those hybrid keys will not be able to install and join.
        // This sets the identity of the sharedUserId to rsa-2048 -> rsa-2048_2 (no SHARED_USER_ID)
        // -> mldsa-65 -> rsa-2048_3 -> (rsa-2048_4 + mldsa-87).
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_4-rsa-2048_3_2-no-shUid-mldsa-65-in-por-v3-rsa-2048"
                        + "-companion3-shUid.apk",
                getDevice());

        // An APK signed with a single signer that still has the capability granted should still
        // install and join the sharedUserId. Note, this also simulates an older package in the
        // ecosystem that still has the capability granted to the previous signer; the platform
        // should apply the most restrictive lineage which would keep the capability revoked from
        // the previous hybrid classical signer.
        assertInstallOnDeviceSucceeds(
                "v31-rsa-2048_3-mldsa-rsa-2048_2-in-por-v3-rsa-2048-companion2-shUid.apk",
                getDevice());

        // The classical hybrid key from this APK has had the SHARED_USER_ID capability revoked, so
        // even though the hybrid PQC key still has the capability, the install should fail.
        assertInstallOnDeviceFails(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-shUid.apk", getDevice());

        // A previous signer in the lineage should not be affected by the revoked capability.
        assertInstallOnDeviceSucceeds("v3-rsa-2048-shUid.apk", getDevice());
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionSameHybridRequesting_granted() throws Exception {
        // If an APK declaring a permission is signed by the same hybrid signign config as an APK
        // requesting the permission, the permission should be granted.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageGrantedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionSharedClassicalInV3Requesting_granted() throws Exception {
        // Permissions are granted to a requesting app if they share a common signer with the
        // declaring app. This test verifies that a hybrid app with a classical signer in its
        // lineage with the PERMISSION capability granted can grant a permission to a requesting
        // app signed by that previous classical key.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds("v3-rsa-2048-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageGrantedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionSharedClassicalFromV32Requesting_denied() throws Exception {
        // If the app declaring a permission is signed by a hybrid config and the requesting app is
        // only signed by one of the keys in the hybrid config, the permission request should be
        // denied.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds("v3-rsa-2048_2-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageDeniedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionSharedPqcInV32Requesting_denied() throws Exception {
        // If the app declaring a permission is signed by a hybrid config and the requesting app
        // only has the PQC key in common with the declaring app, then the request should be denied.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-65-rsa-2048_3-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageDeniedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionDifferentV32Requesting_denied() throws Exception {
        // If an app requesting a permission has a completely different hybrid config from the app
        // that declared the permission, then the permission request should be denied.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageDeniedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3PermissionSharedClassicalInV32Requesting_denied() throws Exception {
        // If a declaring app is signed by a single classical key and a requesting app is signed by
        // a hybrid config with the declaring app's classical key in the hybrid block, the request
        // should be denied since a requesting hybrid signed app must have both signers in the
        // lineage of the declaring app to be considered shared.
        assertInstallOnDeviceSucceeds("v3-rsa-2048_3-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-65-rsa-2048_3-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageDeniedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v3PermissionSharedClassicalInV32LineageRequesting_granted()
            throws Exception {
        // If a declaring app is signed by a single classical key and a requesting app is signed
        // by a hybrid signign config with the declaring app's single classical key in its lineage,
        // then the permission should be granted since this is the standard case where the
        // requesting app has been rotated before the declaring app.
        assertInstallOnDeviceSucceeds("v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageGrantedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionToRotatedV32WithoutPqcInLineage_denied() throws Exception {
        // A hybrid signed app should support granting a permission to a requesting app if the
        // hybrid keys for the declaring app are both in the lineage of the requesting app. The
        // requesting app for this test only has the classical key in its lineage, so the request
        // should be denied.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-rsa-2048_2-in-por-v3-rsa-2048-companion-usesperm.apk",
                getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageDeniedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionToRotatedV32WithoutClassicalInLineage_denied()
            throws Exception {
        // A hybrid signed app should support granting a permission to a requesting app if the
        // hybrid keys for the declaring app are both in the lineage of the requesting app. The
        // requesting app for this test only has the PQC key in its lineage, so the request should
        // be denied.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-mldsa-65-in-por-v3-rsa-2048-companion-usesperm.apk",
                getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageDeniedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionSharedClassicalFromV32RequestingRevoked_denied()
            throws Exception {
        // If the declaring app is hybrid signed and has revoked the PERMISSION capability from the
        // previous signer in the lineage and the requesting app is signed by this signer, then the
        // request should be denied.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-no-perm-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds("v3-rsa-2048-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageDeniedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32RotatedPermissionSharedV32InLineageRequesting_granted()
            throws Exception {
        // If the declaring app has rotated the hybrid config multiple times, and the requesting app
        // is signed by the first hybrid config that is in the lineage and granted the PERMISSION
        // capability, then the request should be granted.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-mldsa-65-rsa-2048_2-in-por-v3-rsa-2048-declperm.apk",
                getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageGrantedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32PermissionRotatedV32WithSharedHybridInLineageRequesting_granted()
            throws Exception {
        // If the declaring app is signed with a hybrid config and the requesting app has rotated
        // its hybrid config with the declaring app's hybrid config in its lineage, then the request
        // should be granted.
        assertInstallOnDeviceSucceeds("v32-mldsa-rsa-2048_2-v3-rsa-2048-declperm.apk", getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-mldsa-65-rsa-2048_2-in-por-v3-rsa-2048-companion"
                        + "-usesperm.apk",
                getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageGrantedPerm");
    }

    @CddTest(requirement = "4/C-0-2")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APK_PQC_HYBRID_SIGNING)
    public void testV32_v32RotatedPermissionV32FromLineageClassicalRevokedRequesting_granted()
            throws Exception {
        // This is a unique case where the declaring app has rotated its hybrid key multiple times
        // and has revoked the PERMISSION capability from the classical signer in its lineage that
        // is used as the classical signer in the requesting app's hybrid block. Since both of the
        // hybrid keys from the requesting app are in the declaring app's lineage, and one of them
        // still has the PERMISSION capability, the permission is granted. While the general
        // guidance is to either fully revoke or grant a capability to both hybrid signers in the
        // lineage, if the classical is compromised and a developer only wants to revoke the
        // capability from that signer, then the permission should still be granted for older apps
        // still signed with this hybrid pair. This is intended since the hybrid signed requesting
        // app could be rotated to any new key, and the common PQC signer in the lineage would allow
        // the requesting app to be granted the permission.
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-87-rsa-2048_3-mldsa-65-rsa-2048_2-no-perm-in-por-v3-rsa-2048-declperm"
                        + ".apk",
                getDevice());
        assertInstallOnDeviceSucceeds(
                "v32-mldsa-rsa-2048_2-v3-rsa-2048-companion-usesperm.apk", getDevice());

        Utils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PACKAGE,
                DEVICE_TEST_CLASS,
                "testV32_companionPackageGrantedPerm");
    }
}
