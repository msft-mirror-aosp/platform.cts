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

package android.security.cts;

import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.tradefed.device.ITestDevice;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SELinuxNeverallowRule {
    private static String[] sConditions = {
        "TREBLE_ONLY",
        "COMPATIBLE_PROPERTY_ONLY",
        "LAUNCHING_WITH_R_ONLY",
        "LAUNCHING_WITH_S_ONLY",
    };

    public String mText;
    public boolean fullTrebleOnly;
    public boolean launchingWithROnly;
    public boolean launchingWithSOnly;
    public boolean compatiblePropertyOnly;

    private SELinuxNeverallowRule(String text, Map<String, Integer> conditions) {
        mText = text;
        if (conditions.getOrDefault("TREBLE_ONLY", 0) > 0) {
            fullTrebleOnly = true;
        }
        if (conditions.getOrDefault("COMPATIBLE_PROPERTY_ONLY", 0) > 0) {
            compatiblePropertyOnly = true;
        }
        if (conditions.getOrDefault("LAUNCHING_WITH_R_ONLY", 0) > 0) {
            launchingWithROnly = true;
        }
        if (conditions.getOrDefault("LAUNCHING_WITH_S_ONLY", 0) > 0) {
            launchingWithSOnly = true;
        }
    }

    public String toString() {
        return "Rule [text= " + mText
                + ", fullTrebleOnly=" + fullTrebleOnly
                + ", compatiblePropertyOnly=" + compatiblePropertyOnly
                + ", launchingWithROnly=" + launchingWithROnly
                + ", launchingWithSOnly=" + launchingWithSOnly
                + "]";
    }

    private boolean isFullTrebleDevice(ITestDevice device) throws Exception {
        return SELinuxHostTest.isFullTrebleDevice(device);
    }

    private boolean isDeviceLaunchingWithR(ITestDevice device) throws Exception {
        return PropertyUtil.getFirstApiLevel(device) > 29;
    }

    private boolean isDeviceLaunchingWithS(ITestDevice device) throws Exception {
        return PropertyUtil.getFirstApiLevel(device) > 30;
    }

    private boolean isCompatiblePropertyEnforcedDevice(ITestDevice device) throws Exception {
        return SELinuxHostTest.isCompatiblePropertyEnforcedDevice(device);
    }

    public boolean isCompatible(ITestDevice device) throws Exception {
        if ((fullTrebleOnly) && (!isFullTrebleDevice(device))) {
            // This test applies only to Treble devices but this device isn't one
            return false;
        }
        if ((launchingWithROnly) && (!isDeviceLaunchingWithR(device))) {
            // This test applies only to devices launching with R or later but this device isn't one
            return false;
        }
        if ((launchingWithSOnly) && (!isDeviceLaunchingWithS(device))) {
            // This test applies only to devices launching with S or later but this device isn't one
            return false;
        }
        if ((compatiblePropertyOnly) && (!isCompatiblePropertyEnforcedDevice(device))) {
            // This test applies only to devices on which compatible property is enforced but this
            // device isn't one
            return false;
        }
        return true;
    }

    public static List<SELinuxNeverallowRule> parsePolicy(String policy) throws Exception {
        String patternConditions = Arrays.stream(sConditions)
                .flatMap(condition -> Stream.of("BEGIN_" + condition, "END_" + condition))
                .collect(Collectors.joining("|"));

        /* Uncomment conditions delimiter lines. */
        Pattern uncommentConditions = Pattern.compile("^\\s*#\\s*(" + patternConditions + ").*$",
                Pattern.MULTILINE);
        Matcher matcher = uncommentConditions.matcher(policy);
        policy = matcher.replaceAll("$1");

        /* Remove all comments. */
        Pattern comments = Pattern.compile("#.*?$", Pattern.MULTILINE);
        matcher = comments.matcher(policy);
        policy = matcher.replaceAll("");

        /* Use a pattern to match all the neverallow rules or a condition. */
        Pattern neverAllowPattern = Pattern.compile(
                "(neverallow\\s[^;]+?;|" + patternConditions + ")",
                Pattern.MULTILINE);

        List<SELinuxNeverallowRule> rules = new ArrayList();
        Map<String, Integer> conditions = new HashMap();

        matcher = neverAllowPattern.matcher(policy);
        while (matcher.find()) {
            String rule = matcher.group(1).replace("\n", " ");
            if (rule.startsWith("BEGIN_")) {
                String section = rule.substring(6);
                conditions.put(section, conditions.getOrDefault(section, 0) + 1);
            } else if (rule.startsWith("END_")) {
                String section = rule.substring(4);
                Integer v = conditions.getOrDefault(section, 0);
                assertTrue("Condition " + rule + " found without BEGIN", v > 0);
                conditions.put(section, v - 1);
            } else if (rule.startsWith("neverallow")) {
                rules.add(new SELinuxNeverallowRule(rule, conditions));
            } else {
                throw new Exception("Unknown rule: " + rule);
            }
        }

        for (Map.Entry<String, Integer> condition : conditions.entrySet()) {
            if (condition.getValue() != 0) {
                throw new Exception("End of input while inside " + condition.getKey() + " section");
            }
        }

        return rules;
    }

    public void testNeverallowRule(File sepolicyAnalyze, File policyFile) throws Exception {
        /* run sepolicy-analyze neverallow check on policy file using given neverallow rules */
        ProcessBuilder pb = new ProcessBuilder(sepolicyAnalyze.getAbsolutePath(),
                policyFile.getAbsolutePath(), "neverallow", "-n",
                mText);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader result = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder errorString = new StringBuilder();
        while ((line = result.readLine()) != null) {
            errorString.append(line);
            errorString.append("\n");
        }
        p.waitFor();
        assertTrue("The following errors were encountered when validating the SELinux"
                   + "neverallow rule:\n" + mText + "\n" + errorString,
                   errorString.length() == 0);
    }
}
