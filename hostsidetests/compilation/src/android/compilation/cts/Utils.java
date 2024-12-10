/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.compilation.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.Pair;
import com.android.tradefed.util.RunUtil;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {
    private static final Duration SOFT_REBOOT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration HOST_COMMAND_TIMEOUT = Duration.ofSeconds(10);

    private final TestInformation mTestInfo;

    public Utils(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        mTestInfo = testInfo;
    }

    public String assertCommandSucceeds(String... command) throws Exception {
        CommandResult result =
                mTestInfo.getDevice().executeShellV2Command(String.join(" ", command));
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
        // Remove trailing \n's.
        return result.getStdout().trim();
    }

    public String assertHostCommandSucceeds(String... command) throws Exception {
        CommandResult result =
                RunUtil.getDefault().runTimedCmd(HOST_COMMAND_TIMEOUT.toMillis(), command);
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
        // Remove trailing \n's.
        return result.getStdout().trim();
    }

    /**
     * Implementation details.
     *
     * @param packages A list of packages, where each entry is a list of files in the package.
     * @param multiPackage True for {@code install-multi-package}, false for {@code
     *         install-multiple}.
     */
    private void installImpl(IAbi abi, List<String> args, List<List<String>> packages,
            boolean multiPackage) throws Exception {
        // We cannot use `ITestDevice.installPackage` or `SuiteApkInstaller` here because they don't
        // support DM files.
        List<String> cmd =
                new ArrayList<>(List.of("adb", "-s", mTestInfo.getDevice().getSerialNumber(),
                        multiPackage ? "install-multi-package" : "install-multiple", "--abi",
                        abi.getName()));

        cmd.addAll(args);

        if (!multiPackage && packages.size() != 1) {
            throw new IllegalArgumentException(
                    "'install-multiple' only supports exactly one package");
        }

        for (List<String> files : packages) {
            if (multiPackage) {
                // The format is 'pkg1-base.dm:pkg1-base.apk:pkg1-split1.dm:pkg1-split1.apk
                // pkg2-base.dm:pkg2-base.apk:pkg2-split1.dm:pkg2-split1.apk'.
                cmd.add(String.join(":", files));
            } else {
                // The format is 'pkg1-base.dm pkg1-base.apk pkg1-split1.dm pkg1-split1.apk'.
                cmd.addAll(files);
            }
        }

        // We can't use `INativeDevice.executeAdbCommand`. It only returns stdout on success and
        // returns null on failure, while we want to get the exact error message.
        CommandResult result = RunUtil.getDefault().runTimedCmd(
                mTestInfo.getDevice().getOptions().getAdbCommandTimeout(),
                cmd.toArray(String[] ::new));
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
    }

    /**
     * Implementation details.
     *
     * @param packages A list of packages, where each entry is a list of APK-DM pairs.
     * @param multiPackage True for {@code install-multi-package}, false for {@code
     *         install-multiple}.
     */
    private void installFromResourcesImpl(IAbi abi, List<String> args,
            List<List<Pair<String, String>>> packages, boolean multiPackage) throws Exception {
        List<List<String>> packageFileLists = new ArrayList<>();
        for (List<Pair<String, String>> apkDmResources : packages) {
            List<String> files = new ArrayList<>();
            for (Pair<String, String> pair : apkDmResources) {
                String apkResource = pair.first;
                File apkFile = copyResourceToFile(apkResource, File.createTempFile("temp", ".apk"));
                apkFile.deleteOnExit();

                String dmResource = pair.second;
                if (dmResource != null) {
                    File dmFile = copyResourceToFile(
                            dmResource, new File(getDmPath(apkFile.getAbsolutePath())));
                    dmFile.deleteOnExit();
                    files.add(dmFile.getAbsolutePath());
                }

                // To make `install-multi-package` happy, the last file must end with ".apk".
                files.add(apkFile.getAbsolutePath());
            }
            packageFileLists.add(files);
        }

        installImpl(abi, args, packageFileLists, multiPackage);
    }

    /**
     * Installs a package from resources with arguments.
     *
     * @param apkDmResources For each pair, the first item is the APK resource name, and the second
     *         item is the DM resource name or null.
     */
    public void installFromResourcesWithArgs(IAbi abi, List<String> args,
            List<Pair<String, String>> apkDmResources) throws Exception {
        installFromResourcesImpl(abi, args, List.of(apkDmResources), false /* multiPackage */);
    }

    /** Same as above, but takes no argument. */
    public void installFromResources(IAbi abi, List<Pair<String, String>> apkDmResources)
            throws Exception {
        installFromResourcesWithArgs(abi, List.of() /* args */, apkDmResources);
    }

    public void installFromResources(IAbi abi, String apkResource, String dmResource)
            throws Exception {
        installFromResources(abi, List.of(Pair.create(apkResource, dmResource)));
    }

    public void installFromResources(IAbi abi, String apkResource) throws Exception {
        installFromResources(abi, apkResource, null);
    }

    public void installFromResourcesMultiPackage(
            IAbi abi, List<List<Pair<String, String>>> packages) throws Exception {
        installFromResourcesImpl(abi, List.of() /* args */, packages, true /* multiPackage */);
    }

    public void installFromResourcesWithSdm(IAbi abi, String apkResource, File dmFile, File sdmFile)
            throws Exception {
        File apkFile = copyResourceToFile(apkResource, File.createTempFile("temp", ".apk"));
        apkFile.deleteOnExit();
        File dmFileCopy = new File(getDmPath(apkFile.getAbsolutePath()));
        Files.copy(dmFile.toPath(), dmFileCopy.toPath());
        dmFileCopy.deleteOnExit();
        File sdmFileCopy = new File(getSdmPath(apkFile.getAbsolutePath()));
        Files.copy(sdmFile.toPath(), sdmFileCopy.toPath());
        sdmFileCopy.deleteOnExit();

        installImpl(abi, List.of() /* args */,
                List.of(List.of(dmFileCopy.getAbsolutePath(), sdmFileCopy.getAbsolutePath(),
                        apkFile.getAbsolutePath())),
                true /* multiPackage */);
    }

    public void pushFromResource(String resource, String remotePath) throws Exception {
        File tempFile = copyResourceToFile(resource, File.createTempFile("temp", ".tmp"));
        tempFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pushFile(tempFile, remotePath)).isTrue();
    }

    public File copyResourceToFile(String resourceName, File file) throws Exception {
        try (OutputStream outputStream = new FileOutputStream(file);
                InputStream inputStream = getClass().getResourceAsStream(resourceName)) {
            assertThat(ByteStreams.copy(inputStream, outputStream)).isGreaterThan(0);
        }
        return file;
    }

    public void softReboot() throws Exception {
        // `waitForBootComplete` relies on `dev.bootcomplete`.
        mTestInfo.getDevice().executeShellCommand("setprop dev.bootcomplete 0");
        mTestInfo.getDevice().executeShellCommand("setprop ctl.restart zygote");
        boolean success = mTestInfo.getDevice().waitForBootComplete(SOFT_REBOOT_TIMEOUT.toMillis());
        assertWithMessage("Soft reboot didn't complete in %ss", SOFT_REBOOT_TIMEOUT.getSeconds())
                .that(success)
                .isTrue();
    }

    public static void dumpContainsDexFile(String dump, String dexFile) {
        assertThat(dump).containsMatch(dexFileToPattern(dexFile));
    }

    public static void dumpDoesNotContainDexFile(String dump, String dexFile) {
        assertThat(dump).doesNotContainMatch(dexFileToPattern(dexFile));
    }

    public static int countSubstringOccurrence(String str, String subStr) {
        return str.split(subStr, -1 /* limit */).length - 1;
    }

    public CompilationArtifacts generateCompilationArtifacts(
            String apkResource, String profileResource) throws Exception {
        String tempDir = "/data/local/tmp/CtsCompilationTestCases_" + UUID.randomUUID();
        assertCommandSucceeds("mkdir", tempDir);
        String remoteApkFile = tempDir + "/app.apk";
        pushFromResource(apkResource, remoteApkFile);
        String remoteProfileFile = tempDir + "/app.prof";
        pushFromResource(profileResource, remoteProfileFile);

        String remoteOdexFile = tempDir + "/app.odex";
        String remoteVdexFile = tempDir + "/app.vdex";
        String remoteArtFile = tempDir + "/app.art";
        assertCommandSucceeds("dex2oat", "--dex-file=" + remoteApkFile,
                "--profile-file=" + remoteProfileFile, "--oat-file=" + remoteOdexFile,
                "--output-vdex=" + remoteVdexFile, "--app-image-file=" + remoteArtFile,
                "--compiler-filter=speed-profile", "--compilation-reason=cloud");

        File odexFile = File.createTempFile("temp", ".odex");
        odexFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pullFile(remoteOdexFile, odexFile)).isTrue();
        File vdexFile = File.createTempFile("temp", ".vdex");
        vdexFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pullFile(remoteVdexFile, vdexFile)).isTrue();
        File artFile = File.createTempFile("temp", ".art");
        artFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pullFile(remoteArtFile, artFile)).isTrue();

        mTestInfo.getDevice().deleteFile(tempDir);

        return new CompilationArtifacts(odexFile, vdexFile, artFile);
    }

    public File createDm(String profileResource, File vdexFile) throws Exception {
        File dmFile = File.createTempFile("test", ".dm");
        dmFile.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(dmFile))) {
            zip.setLevel(0);
            zip.putNextEntry(new ZipEntry("primary.prof"));
            try (InputStream inputStream = getClass().getResourceAsStream(profileResource)) {
                assertThat(ByteStreams.copy(inputStream, zip)).isGreaterThan(0);
            }
            zip.putNextEntry(new ZipEntry("primary.vdex"));
            try (InputStream inputStream = new FileInputStream(vdexFile)) {
                assertThat(ByteStreams.copy(inputStream, zip)).isGreaterThan(0);
            }
            zip.closeEntry();
        }
        return dmFile;
    }

    // We cannot generate an SDM file in the build system because the contents have to come from the
    // device.
    public File createSdm(File odexFile, File artFile) throws Exception {
        File sdmFile = File.createTempFile("test", ".sdm");
        sdmFile.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(sdmFile))) {
            zip.setLevel(0);
            zip.putNextEntry(new ZipEntry("primary.odex"));
            try (InputStream inputStream = new FileInputStream(odexFile)) {
                assertThat(ByteStreams.copy(inputStream, zip)).isGreaterThan(0);
            }
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("primary.art"));
            try (InputStream inputStream = new FileInputStream(artFile)) {
                assertThat(ByteStreams.copy(inputStream, zip)).isGreaterThan(0);
            }
            zip.closeEntry();
        }
        signApk(sdmFile);
        return sdmFile;
    }

    private void signApk(File file) throws Exception {
        File apksigner = mTestInfo.getDependencyFile("apksigner.jar", false /* targetFirst */);
        File key = mTestInfo.getDependencyFile("testkey.pk8", false /* targetFirst */);
        File cert = mTestInfo.getDependencyFile("testkey.x509.pem", false /* targetFirst */);
        assertHostCommandSucceeds("java", "-jar", apksigner.getAbsolutePath(), "sign", "--key",
                key.getAbsolutePath(), "--cert", cert.getAbsolutePath(), "--min-sdk-version=35",
                file.getAbsolutePath());
    }

    private String getDmPath(String apkPath) throws Exception {
        return apkPath.replaceAll("\\.apk$", ".dm");
    }

    private String getSdmPath(String apkPath) throws Exception {
        return apkPath.replaceAll("\\.apk$", ".sdm");
    }

    private static Pattern dexFileToPattern(String dexFile) {
        return Pattern.compile(String.format("[\\s/](%s)\\s?", Pattern.quote(dexFile)));
    }

    /** Represents the compilation artifacts of an APK. All the files are on host. */
    public record CompilationArtifacts(File odexFile, File vdexFile, File artFile) {}
}
