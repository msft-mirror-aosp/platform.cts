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

import com.android.cts.apicommon.ApiClass;
import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apicommon.ApiMethod;
import com.android.cts.apicommon.ApiPackage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

/**
 * Generates XML representing API methods inheriting or overriding super methods.
 *
 * <p>The element structure is:
 * <xts-api-inherit type="api">
 *     <method abstract="false" class="TvAdService" name="onBind" package="android.media.tv.ad">
 *         <override-api abstract="true" class="Service" package="android.app" type="override"/>
 *         <parameter type="android.content.Intent"/>
 *     </method>
 *     <method abstract="false" class="TvAdService" name="dump" package="android.media.tv.ad">
 *         <override-api abstract="false" class="Service" package="android.app" type="inherit"/>
 *         <parameter type="java.io.FileDescriptor"/>
 *         <parameter type="java.io.PrintWriter"/>
 *         <parameter type="java.lang.String[]"/>
 *     </method>
 * </xts-api-inherit>
 */
public class ApiInheritGenerator extends ApiXmlGenerator {

    private static final String TOP_ELEMENT_NAME = "xts-api-inherit";

    ApiInheritGenerator(Document doc) {
        super(doc);
        addTopElement(TOP_ELEMENT_NAME);
        getTopElement(TOP_ELEMENT_NAME).setAttribute("type", "api");
    }

    @Override
    void generateData(ApiCoverage apiCoverage) {
        for (ApiPackage apiPackage : apiCoverage.getPackages()) {
            for (ApiClass apiClass : apiPackage.getClasses()) {
                processClass(apiClass);
            }
        }
    }

    private void processClass(ApiClass apiClass) {
        for (ApiMethod apiMethod : apiClass.getDeclaredMethods()) {
            apiClass.getOverriddenMethods(apiMethod.getName(), apiMethod.getParameterTypes())
                    .forEach(
                            (superClass, superMethod) ->
                                    getTopElement(TOP_ELEMENT_NAME)
                                            .appendChild(
                                                    createMethodElement(
                                                            apiClass,
                                                            apiMethod,
                                                            superClass,
                                                            superMethod,
                                                            "override")));
        }
        apiClass.getInheritedMethods()
                .forEach(
                        superClassMethod ->
                                getTopElement(TOP_ELEMENT_NAME)
                                        .appendChild(
                                                createMethodElement(
                                                        apiClass,
                                                        superClassMethod.getSecond(),
                                                        superClassMethod.getFirst(),
                                                        superClassMethod.getSecond(),
                                                        "inherit")));
    }

    private Element createMethodElement(
            ApiClass apiClass,
            ApiMethod apiMethod,
            ApiClass superClass,
            ApiMethod superMethod,
            String type) {
        Element methodElement =
                createElement(
                        "method",
                        Map.of(
                                "name", apiMethod.getName(),
                                "class", apiClass.getName(),
                                "package", apiClass.getPackageName(),
                                "abstract", apiMethod.isAbstractMethod()));
        methodElement.appendChild(
                createApiElement(superClass, type, superMethod.isAbstractMethod()));
        addParameterTypes(apiMethod.getParameterTypes(), methodElement);
        return methodElement;
    }

    private void addParameterTypes(List<String> params, Element parent) {
        params.forEach(
                param -> parent.appendChild(createElement("parameter", Map.of("type", param))));
    }

    private Element createApiElement(ApiClass apiClass, String type, boolean isAbstract) {
        return createElement(
                "override-api",
                Map.of(
                        "package",
                        apiClass.getPackageName(),
                        "class",
                        apiClass.getName(),
                        "type",
                        type,
                        "abstract",
                        isAbstract));
    }
}
