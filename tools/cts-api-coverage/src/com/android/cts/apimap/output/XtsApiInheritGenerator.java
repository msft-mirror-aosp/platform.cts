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
 *         <override-api class="ApiClassA" package="android.api1" type="override"/>
 *         <parameter type="int"/>
 *     </method>
 *     <method name="methodB" class="ClassA" package="android.cts.module1" >
 *         <override-api class="ApiClassA" package="android.api1" type="inherit"/>
 *         <parameter type="int"/>
 *     </method>
 * </xts-api-inherit>
 */
public final class XtsApiInheritGenerator extends XtsXmlGenerator {

    private static final String TOP_ELEMENT_NAME = "xts-api-inherit";

    private final Set<String> mClassCache = new HashSet<>();

    XtsApiInheritGenerator(Document doc) {
        super(doc);
        addTopElement(TOP_ELEMENT_NAME);
        getTopElement(TOP_ELEMENT_NAME).setAttribute("type", "xts");
    }

    @Override
    public void generateData(ModuleProfile module) {
        module.getClasses().stream().filter(this::shouldRecord).forEach(this::processClass);
    }

    private void processClass(ClassProfile classProfile) {
        mClassCache.add(classProfile.getClassSignature());
        if (!classProfile.getInheritedApiClasses().isEmpty()) {
            classProfile
                    .getMethods()
                    .forEach(
                            (signature, method) -> {
                                for (Map.Entry<ClassProfile, MethodProfile> classMethod :
                                        classProfile
                                                .getOverriddenApiMethods(signature)
                                                .entrySet()) {
                                    getTopElement(TOP_ELEMENT_NAME)
                                            .appendChild(
                                                    createMethodElement(
                                                            classProfile,
                                                            method,
                                                            classMethod.getKey(),
                                                            classMethod.getValue(),
                                                            "override"));
                                }
                            });
        }
        for (Map.Entry<ClassProfile, List<MethodProfile>> methods :
                classProfile.getInheritedApiMethods().entrySet()) {
            for (MethodProfile method : methods.getValue()) {
                getTopElement(TOP_ELEMENT_NAME)
                        .appendChild(
                                createMethodElement(
                                        classProfile, method, methods.getKey(), method, "inherit"));
            }
        }
    }

    private Element createMethodElement(
            ClassProfile classProfile,
            MethodProfile methodProfile,
            ClassProfile superClass,
            MethodProfile superMethod,
            String type) {
        Element methodElement =
                createElement(
                        "method",
                        Map.of(
                                "name", methodProfile.getMethodName(),
                                "class", classProfile.getClassName(),
                                "package", classProfile.getPackageName(),
                                "abstract", methodProfile.isAbstract()));
        methodElement.appendChild(createApiElement(superClass, type, superMethod.isAbstract()));
        addParameterTypes(methodProfile.getMethodParams(), methodElement);
        return methodElement;
    }

    private Element createApiElement(ClassProfile classProfile, String type, boolean isAbstract) {
        return createElement(
                "override-api",
                Map.of(
                        "package",
                        classProfile.getPackageName(),
                        "class",
                        classProfile.getClassName(),
                        "type",
                        type,
                        "abstract",
                        isAbstract));
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
        return !mClassCache.contains(classSignature) && !classProfile.isApiClass();
    }
}
