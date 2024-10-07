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

import com.android.cts.apicommon.ApiClass;
import com.android.cts.apicommon.ApiConstructor;
import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apicommon.ApiMethod;
import com.android.cts.apicommon.ApiPackage;
import com.android.cts.apicommon.CoverageComparator;
import com.android.cts.apicommon.TestMethodInfo;
import com.android.cts.ctsprofiles.ClassProfile;
import com.android.cts.ctsprofiles.MethodProfile;
import com.android.cts.ctsprofiles.ModuleProfile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Class that outputs an XML report of API and xTS-annotation mapping data. */
public class XmlWriter {

    private final Document mDoc;

    private final Element mXTSAnnotationMapElement;

    private final Element mXTSApiMapElement;

    private final Element mRootElement;

    public XmlWriter() throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = docFactory.newDocumentBuilder();
        mDoc = documentBuilder.newDocument();
        ProcessingInstruction pi = mDoc.createProcessingInstruction(
                "xml-stylesheet", "type=\"text/xsl\" href=\"api-coverage.xsl\"");
        SimpleDateFormat format = new SimpleDateFormat(
                "EEE, MMM d, yyyy h:mm a z", Locale.US);
        String date = format.format(new Date(System.currentTimeMillis()));
        mRootElement = mDoc.createElement("api-coverage");
        mRootElement.setAttribute("generatedTime", date);
        mRootElement.setAttribute("title", "cts-m-automation");
        mDoc.appendChild(mRootElement);
        mDoc.insertBefore(pi, mRootElement);
        mXTSAnnotationMapElement = mDoc.createElement("xts-annotation");
        mRootElement.appendChild(mXTSAnnotationMapElement);
        mXTSApiMapElement = mDoc.createElement("api");
        mRootElement.appendChild(mXTSApiMapElement);
    }

    /** Dumps the document to an xml file. */
    public void dumpXml(OutputStream outputStream)
            throws FileNotFoundException, TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("indent", "yes");
        DOMSource source = new DOMSource(mDoc);
        StreamResult result = new StreamResult(outputStream);
        transformer.transform(source, result);
    }

    /** Generates the data for API coverage. */
    public void generateApiMapData(ApiCoverage apiCoverage) {
        CoverageComparator comparator = new CoverageComparator();
        List<ApiPackage> packages = new ArrayList<>(apiCoverage.getPackages());
        packages.sort(comparator);
        ApiStatistics statistics = new ApiStatistics();
        for (ApiPackage pkg : packages) {
            if (pkg.getTotalMethods() > 0) {
                mXTSApiMapElement.appendChild(createApiPackageElement(pkg, statistics));
            }
        }
        mRootElement.appendChild(createApiTotalElement(
                statistics.mTotalMethods, statistics.mTotalCoveredMethods));
    }

    /** Generates the data for xTS annotations. */
    public void generateXtsAnnotationMapData(ModuleProfile module) {
        Element moduleElement = mDoc.createElement("test-module");
        moduleElement.setAttribute("name", module.getModuleName());
        for (ClassProfile classProfile : module.getClasses()) {
            if (!classProfile.isNonAbstractTestClass()) {
                continue;
            }
            Element classElement = createTestClassElement(classProfile);
            if (classElement.hasChildNodes()) {
                moduleElement.appendChild(classElement);
            }
        }
        if (moduleElement.hasChildNodes()) {
            mXTSAnnotationMapElement.appendChild(moduleElement);
        }
    }

    private Element createApiConstructorElement(
            ApiConstructor constructor, ApiStatistics statistics) {
        if (constructor.isDeprecated()) {
            if (constructor.isCovered()) {
                statistics.mTotalCoveredMethods -= 1;
            }
            statistics.mTotalMethods -= 1;
        }
        List<String> coveredWithList = new ArrayList<>(constructor.getCoveredWith());
        Collections.sort(coveredWithList);
        String coveredWith = String.join(",", coveredWithList);
        Element element = mDoc.createElement("constructor");
        element.setAttribute("name", constructor.getName());
        element.setAttribute("deprecated", String.valueOf(constructor.isDeprecated()));
        element.setAttribute("covered", String.valueOf(constructor.isCovered()));
        element.setAttribute("with", coveredWith);
        for (String parameterType : constructor.getParameterTypes()) {
            Element paramElement = mDoc.createElement("parameter");
            paramElement.setAttribute("type", parameterType);
            element.appendChild(paramElement);
        }
        for (TestMethodInfo test : constructor.getCoveredTests()) {
            element.appendChild(createCoveredByElement(test));
        }
        return element;
    }

    private Element createApiMethodElement(ApiMethod method, ApiStatistics statistics) {
        if (method.isDeprecated()) {
            if (method.isCovered()) {
                statistics.mTotalCoveredMethods -= 1;
            }
            statistics.mTotalMethods -= 1;
        }
        List<String> coveredWithList = new ArrayList<>(method.getCoveredWith());
        Collections.sort(coveredWithList);
        String coveredWith = String.join(",", coveredWithList);
        Element element = mDoc.createElement("method");
        element.setAttribute("name", method.getName());
        element.setAttribute("returnType", method.getReturnType());
        element.setAttribute("deprecated", String.valueOf(method.isDeprecated()));
        element.setAttribute("static", String.valueOf(method.isStaticMethod()));
        element.setAttribute("final", String.valueOf(method.isFinalMethod()));
        element.setAttribute("visibility", method.getVisibility());
        element.setAttribute("abstract", String.valueOf(method.isAbstractMethod()));
        element.setAttribute("covered", String.valueOf(method.isCovered()));
        element.setAttribute("with", coveredWith);
        for (String parameterType : method.getParameterTypes()) {
            Element paramElement = mDoc.createElement("parameter");
            paramElement.setAttribute("type", parameterType);
            element.appendChild(paramElement);
        }
        for (TestMethodInfo test : method.getCoveredTests()) {
            element.appendChild(createCoveredByElement(test));
        }
        return element;
    }

    private Element createCoveredByElement(TestMethodInfo test) {
        Element element = mDoc.createElement("covered-by");
        element.setAttribute("module", test.moduleName());
        element.setAttribute("package", test.packageName());
        element.setAttribute("class", test.className());
        element.setAttribute("test", test.testName());
        return element;
    }

    private Element createApiPackageElement(ApiPackage pkg, ApiStatistics statistics) {
        Element element = mDoc.createElement("package");
        element.setAttribute("name", pkg.getName());
        element.setAttribute("numCovered", String.valueOf(pkg.getNumCoveredMethods()));
        element.setAttribute("numTotal", String.valueOf(pkg.getTotalMethods()));
        element.setAttribute("coveragePercentage", String.valueOf(
                Math.round(pkg.getCoveragePercentage())));
        statistics.mTotalMethods += pkg.getTotalMethods();
        statistics.mTotalCoveredMethods += pkg.getNumCoveredMethods();
        List<ApiClass> classes = new ArrayList<>(pkg.getClasses());
        CoverageComparator comparator = new CoverageComparator();
        classes.sort(comparator);
        for (ApiClass apiClass : classes) {
            if (apiClass.getTotalMethods() > 0) {
                element.appendChild(createApiClassElement(apiClass, statistics));
            }
        }
        return element;
    }

    private Element createApiClassElement(ApiClass apiClass, ApiStatistics statistics) {
        Element element = mDoc.createElement("class");
        element.setAttribute("name", apiClass.getName());
        element.setAttribute("numCovered", String.valueOf(apiClass.getNumCoveredMethods()));
        element.setAttribute("numTotal", String.valueOf(apiClass.getTotalMethods()));
        element.setAttribute("deprecated", String.valueOf(apiClass.isDeprecated()));
        element.setAttribute("coveragePercentage", String.valueOf(
                Math.round(apiClass.getCoveragePercentage())));
        for (ApiConstructor constructor : apiClass.getConstructors()) {
            element.appendChild(createApiConstructorElement(constructor, statistics));
        }
        for (ApiMethod method : apiClass.getMethods()) {
            element.appendChild(createApiMethodElement(method, statistics));
        }
        return element;
    }

    private Element createApiTotalElement(int totalMethods, int totalCoveredMethods) {
        Element total = mDoc.createElement("total");
        total.setAttribute("numCovered", String.valueOf(totalCoveredMethods));
        total.setAttribute("numTotal", String.valueOf(totalMethods));
        total.setAttribute("coveragePercentage", String.valueOf(
                Math.round((float) totalCoveredMethods / totalMethods * 100.0f)));
        return total;
    }

    private Element createTestClassElement(ClassProfile classProfile) {
        Element classElement = mDoc.createElement("test-class");
        classElement.setAttribute("package", classProfile.getPackageName());
        classElement.setAttribute("name", classProfile.getClassName());
        for (Map.Entry<String, Set<String>> xtsAnnotation :
                classProfile.annotationManagement.getTestMetadata().entrySet()) {
            classElement.appendChild(createXtsAnnotationElement(
                    xtsAnnotation.getKey(),
                    xtsAnnotation.getValue()));
        }
        for (MethodProfile testMethod : classProfile.getTestMethods().values()) {
            Element methodElement = createTestMethodElement(testMethod);
            if (methodElement.hasChildNodes()) {
                classElement.appendChild(methodElement);
            }
        }
        return classElement;
    }

    private Element createTestMethodElement(MethodProfile methodProfile) {
        Element methodElement = mDoc.createElement("test-method");
        methodElement.setAttribute("name", methodProfile.getMethodName());
        for (Map.Entry<String, Set<String>> xtsAnnotation :
                methodProfile.annotationManagement.getTestMetadata().entrySet()) {
            methodElement.appendChild(createXtsAnnotationElement(
                    xtsAnnotation.getKey(),
                    xtsAnnotation.getValue()));
        }
        return methodElement;
    }

    private Element createXtsAnnotationElement(String name, Set<String> values) {
        Element element = mDoc.createElement("annotation");
        element.setAttribute("name", name);
        element.setAttribute("values", String.join(";", values));
        return element;
    }

    static final class ApiStatistics {
        int mTotalMethods = 0;
        int mTotalCoveredMethods = 0;
    }
}
