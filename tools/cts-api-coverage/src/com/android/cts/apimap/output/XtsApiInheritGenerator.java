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

package com.android.cts.apimap.output;

import com.android.cts.ctsprofiles.ClassProfile;
import com.android.cts.ctsprofiles.MethodProfile;
import com.android.cts.ctsprofiles.ModuleProfile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates XML representing xTS methods inheriting API methods or overriding abstract API methods.
 *
 * <p>The element structure is:
 * <xts-api-inherit>
 *     <method name="methodA" class="ClassA" package="android.cts.module1" >
 *         <override-api class="ApiClassA", package="android.api1" />
 *         <parameter type="int"/>
 *     </method>
 * </xts-api-inherit>
 */
public final class XtsApiInheritGenerator extends XtsXmlGenerator {

    private static final String TOP_ELEMENT_NAME = "xts-api-inherit";
    private static final List<String> API_CLASS_PREFIXES =
            List.of("android.", "com.android.", "dalvik.", "libcore.");

    private final Set<String> mClassCache = new HashSet<>();

    XtsApiInheritGenerator(Document doc) {
        super(doc);
        addTopElement(TOP_ELEMENT_NAME);
    }

    @Override
    public void generateData(ModuleProfile module) {
        module.getClasses().stream().filter(this::shouldRecord).forEach(this::processClass);
    }

    private void processClass(ClassProfile classProfile) {
        mClassCache.add(classProfile.getClassSignature());
        Map<String, Map<String, MethodProfile>> overriddenApiMethods =
                classProfile.getOverriddenAbstractApiMethods();
        for (MethodProfile methodProfile : classProfile.getMethods().values()) {
            Map<String, MethodProfile> apis =
                    overriddenApiMethods.get(methodProfile.getMethodSignatureWithClass());
            if (apis != null) {
                getTopElement(TOP_ELEMENT_NAME)
                        .appendChild(createMethodElement(methodProfile, apis));
            }
        }
    }

    /**
     * Creates an XML element representing a method and its overridden abstract API methods.
     *
     * @param methodProfile The profile of the method.
     * @param apis abstract API methods overridden by the method.
     * @return The created XML element.
     */
    private Element createMethodElement(
            MethodProfile methodProfile, Map<String, MethodProfile> apis) {
        Element methodElement =
                createElement(
                        "method",
                        Map.of(
                                "name", methodProfile.getMethodName(),
                                "class", methodProfile.getClassName(),
                                "package", methodProfile.getPackageName()));
        for (MethodProfile api : apis.values()) {
            methodElement.appendChild(createApiElement(api));
        }
        addParameterTypes(methodProfile.getMethodParams(), methodElement);
        return methodElement;
    }

    private Element createApiElement(MethodProfile methodProfile) {
        return createElement(
                "override-api",
                Map.of(
                        "package", methodProfile.getPackageName(),
                        "class", methodProfile.getClassName()));
    }

    private void addParameterTypes(List<String> params, Element parent) {
        params.forEach(
                param -> parent.appendChild(createElement("parameter", Map.of("type", param))));
    }

    /**
     * Determines whether a class should be recorded in the XML output. A class is recorded if it
     * meets the following criteria:
     *
     * <ul>
     *   <li>It's not already in the class cache.
     *   <li>It's not an API class itself.
     *   <li>It inherits from at least one API class with a recognized prefix.
     * </ul>
     *
     * @param classProfile The profile of the class to check.
     * @return {@code true} if the class should be recorded, {@code false} otherwise.
     */
    private boolean shouldRecord(ClassProfile classProfile) {
        String classSignature = classProfile.getClassSignature();
        if (mClassCache.contains(classSignature) || classProfile.isApiClass()) {
            return false;
        }
        return classProfile.getInheritedApiClasses().keySet().stream()
                .anyMatch(apiClass -> API_CLASS_PREFIXES.stream().anyMatch(apiClass::startsWith));
    }
}
