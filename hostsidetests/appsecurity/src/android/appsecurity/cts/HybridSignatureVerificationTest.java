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
}
