/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.apimap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** A utility class for handling file operations, specifically for locating jar files. */
public final class FileUtils {

    /** Priority list of directories to search for jar files. */
    private static final List<String> JAR_DIRS = List.of("withres", "combined", "javac", "jarjar");

    /** The directory name used by Soong's sandbox environment. */
    private static final String SBOX_DIR = "/out/soong/.temp/sbox";

    private FileUtils() {}

    /**
     * Resolves the path to a jar file based on the given input file path.
     *
     * <p>If the input is a jar file (but not a dex jar), it is returned if it exists. If the input
     * is an APK or a dex jar, this method attempts to find the corresponding source jar file in the
     * {@code android_common} intermediate directories.
     *
     * @param inputFile The path to the input file (jar or apk).
     * @return The {@link Path} to the resolved jar file, or {@code null} if not found.
     */
    public static Path getJarFile(String inputFile) {
        String absolutePath = getAbsoluteFilePath(inputFile);
        boolean isJarFile = absolutePath.endsWith(".jar");
        boolean isDexJarFile = isJarFile && absolutePath.contains("/android_common/dex/");

        if (isJarFile && !isDexJarFile) {
            Path path = Paths.get(absolutePath);
            return Files.exists(path) ? path : null;
        }

        if (absolutePath.endsWith(".apk") || isDexJarFile) {
            // Search for the corresponding jar file for the given apk file or dex jar file. This
            // only works for apk packages or dex jar packages installed under
            // out/soong/.intermediate. For example, if the apk file path is
            // out/soong/.intermediate/.../Module/android_common/Module.apk, then search for the jar
            // file under
            // out/soong/.intermediate/.../Module/android_common/withres(combines, javac, jarjar)/.
            int moduleDirEndPos = absolutePath.indexOf("android_common");
            if (moduleDirEndPos < 0) {
                return null;
            }
            String moduleDir = absolutePath.substring(0, moduleDirEndPos - 1);
            String moduleName = moduleDir.substring(moduleDir.lastIndexOf('/') + 1);
            // Search for the jar file under withres, combined, javac, jarjar directories in order.
            for (String jarDir : JAR_DIRS) {
                Path jarFile =
                        Paths.get(
                                String.format(
                                        "%s/android_common/%s/%s.jar",
                                        moduleDir, jarDir, moduleName));
                if (Files.exists(jarFile)) {
                    return jarFile;
                }
            }
        }
        return null;
    }

    /**
     * Parses jar files from the given file. Listed files must be split by a whitespace:
     * (1) jar file: record the file if it exists, otherwise, ignore it
     * (2) apk file: try to search for the corresponding jar file under the same directory if the
     *               given apk file is installed under out/soong/.intermediate/
     * (3) other types: ignore them
     */
    public static List<Path> getJarFilesFromFile(String inputFile) throws IOException {
        Path filePath = Paths.get(inputFile);
        List<String> lines = Files.readAllLines(filePath);
        List<Path> jarFiles = new ArrayList<>();
        for (String line : lines) {
            for (String file : line.split("\\s+")) {
                Path jarFile = getJarFile(file);
                if (jarFile == null) {
                    continue;
                }
                jarFiles.add(jarFile);
            }
        }
        return jarFiles;
    }

    /**
     * Converts a relative file path to an absolute path, accounting for the Soong sandbox
     * environment.
     *
     * @param filePath The relative file path.
     * @return The absolute file path string.
     */
    private static String getAbsoluteFilePath(String filePath) {
        String dir = Paths.get("").toAbsolutePath().toString();
        // If running inside sbox, strip the sbox suffix to get the project root
        if (dir.contains(SBOX_DIR)) {
            dir = dir.substring(0, dir.indexOf(SBOX_DIR));
        }
        return Paths.get(dir, filePath).toString();
    }
}
