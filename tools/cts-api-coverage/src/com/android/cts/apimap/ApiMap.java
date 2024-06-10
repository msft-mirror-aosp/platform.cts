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

import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apicommon.ApiXmlHandler;
import com.android.cts.ctsprofiles.ClassProfile;
import com.android.cts.ctsprofiles.ModuleProfile;
import com.android.cts.ctsprofiles.Utils;

import org.apache.commons.math3.util.Pair;
import org.objectweb.asm.ClassReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

public final class ApiMap {

    private static final int FORMAT_XML = 1;

    private static final int FORMAT_HTML = 2;

    private static final Set<String> IGNORE_PACKAGES = new HashSet<>(
            List.of("androidx.", "javax", "kotlinx.")
    );

    private static void printUsage() {
        System.out.println("Usage: api-map [OPTION]... [jar]...");
        System.out.println();
        System.out.println("Generates a report about what Android framework methods are called ");
        System.out.println("from the given jars. Jars can be listed in a file in case there are ");
        System.out.println("too many jars to be dealt with.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o FILE                output file");
        System.out.println("  -f [xml|html]          format of output");
        System.out.println("  -a PATH                path to the API XML file");
        System.out.println("  -i PATH                path to the file containing a list of jars: "
                + "Jars must be split by a whitespace, e.g. {jar1} {jar2}.");
        System.out.println("  -j PARALLELISM         number of tasks to run in parallel, defaults"
                + " to number of cpus");
        System.out.println();
        System.exit(1);
    }

    /** Entry of the CTS-M automation tool. */
    public static void main(String[] args)
            throws IOException, TransformerException, ParserConfigurationException, SAXException {
        Map<String, Path> jars = new HashMap<>();
        File outputFile = null;
        int format = FORMAT_XML;
        String apiXmlPath = "";
        int parallelism = Runtime.getRuntime().availableProcessors();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-o".equals(args[i])) {
                    outputFile = new File(Objects.requireNonNull(getExpectedArg(args, ++i)));
                } else if ("-f".equals(args[i])) {
                    String formatSpec = getExpectedArg(args, ++i);
                    if ("xml".equalsIgnoreCase(formatSpec)) {
                        format = FORMAT_XML;
                    } else if ("html".equalsIgnoreCase(formatSpec)) {
                        format = FORMAT_HTML;
                    } else {
                        printUsage();
                    }
                } else if ("-a".equals(args[i])) {
                    apiXmlPath = getExpectedArg(args, ++i);
                } else if ("-j".equals(args[i])) {
                    parallelism = Integer.parseInt(
                            Objects.requireNonNull(getExpectedArg(args, ++i)));
                } else if ("-i".equals(args[i])) {
                    for (Path jar : FileUtils.getJarFilesFromFile(getExpectedArg(args, ++i))) {
                        jars.putIfAbsent(jar.getFileName().toString(), jar);
                    }
                } else {
                    printUsage();
                }
            } else {
                Path jar = FileUtils.getJarFile(args[i]);
                if (jar != null) {
                    jars.putIfAbsent(jar.getFileName().toString(), jar);
                }
            }
        }

        if (outputFile == null) {
            printUsage();
            throw new IllegalArgumentException("missing output file");
        }

        ApiCoverage apiCoverage = getApiCoverage(apiXmlPath);
        apiCoverage.resolveSuperClasses();
        ExecutorService service = Executors.newFixedThreadPool(parallelism);
        List<Future<CallGraphManager>> tasks = new ArrayList<>();

        XmlWriter xmlWriter = new XmlWriter();
        for (Map.Entry<String, Path> jar : jars.entrySet()) {
            String moduleName = jar.getKey();
            int dotIndex = moduleName.lastIndexOf('.');
            moduleName = (dotIndex == -1) ? moduleName : moduleName.substring(0, dotIndex);
            tasks.add(scanJarFile(
                    service, jar.getValue(), moduleName, apiCoverage));
            // Clear tasks when there are too many in the blocking queue to avoid memory issue.
            if (tasks.size() > parallelism * 5) {
                executeTasks(tasks, xmlWriter);
                tasks.clear();
            }
        }
        executeTasks(tasks, xmlWriter);
        service.shutdown();

        xmlWriter.generateApiMapData(apiCoverage);
        FileOutputStream output = new FileOutputStream(outputFile);
        if (format == FORMAT_XML) {
            xmlWriter.dumpXml(output);
        } else {
            HtmlWriter.printHtmlReport(xmlWriter, output);
        }
    }

    /** Executes given tasks. */
    private static void executeTasks(List<Future<CallGraphManager>> tasks, XmlWriter xmlWriter) {
        for (Future<CallGraphManager> task : tasks) {
            try {
                CallGraphManager callGraphManager = task.get();
                xmlWriter.generateXtsAnnotationMapData(callGraphManager.getModule());
            } catch (ExecutionException e) {
                System.out.println("Task was completed unsuccessfully.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread was interrupted before the task completed.");
            }
        }
    }

    /** Gets the argument or prints out the usage and exits. */
    private static String getExpectedArg(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            printUsage();
            return null;
        }
    }

    /**
     * Scans classes inside the jar file and adds coverage statistics.
     *
     * @param service executor service
     * @param filePath jar file to be analyzed
     * @param moduleName test module name
     * @param apiCoverage object to which the coverage statistics will be added to
     */
    private static Future<CallGraphManager> scanJarFile(
            ExecutorService service,
            Path filePath,
            String moduleName,
            ApiCoverage apiCoverage) {
        return service.submit(() -> {
            ModuleProfile moduleProfile = new ModuleProfile(moduleName);
            try (ZipFile zipSrc = new ZipFile(filePath.toString())) {
                Enumeration<? extends ZipEntry> srcEntries = zipSrc.entries();
                while (srcEntries.hasMoreElements()) {
                    ZipEntry entry = srcEntries.nextElement();
                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    newEntry.setTime(entry.getTime());
                    BufferedInputStream bis = new BufferedInputStream(zipSrc.getInputStream(entry));
                    String className = entry.getName();
                    if (!className.endsWith(".class")) {
                        continue;
                    }
                    Pair<String, String> packageClass = Utils.getPackageClassFromASM(
                            className.substring(0, className.length() - 6));
                    String packageName = packageClass.getFirst();
                    if (ignorePackage(packageName)) {
                        continue;
                    }
                    ClassReader cr = new ClassReader(bis);
                    ClassProfile classProfile = moduleProfile.getOrCreateClass(
                            packageClass.getFirst(), packageClass.getSecond(), apiCoverage);
                    ClassAnalyzer visitor = new ClassAnalyzer(
                            classProfile, moduleProfile, apiCoverage);
                    cr.accept(visitor, 0);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            CallGraphManager callGraphManager = new CallGraphManager(moduleProfile);
            callGraphManager.resolveCoveredApis(apiCoverage);
            return callGraphManager;
        });
    }

    private static ApiCoverage getApiCoverage(String apiXmlPath)
            throws SAXException, IOException {
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        ApiXmlHandler currentXmlHandler = new ApiXmlHandler();
        xmlReader.setContentHandler(currentXmlHandler);

        File currentXml = new File(apiXmlPath);
        try (FileReader fileReader = new FileReader(currentXml)) {
            xmlReader.parse(new InputSource(fileReader));
        }
        return currentXmlHandler.getApi();
    }

    private static boolean ignorePackage(String packageName) {
        for (String ignorePackage: IGNORE_PACKAGES) {
            if (packageName.startsWith(ignorePackage)) {
                return true;
            }
        }
        return false;
    }
}
