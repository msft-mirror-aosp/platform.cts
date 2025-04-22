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

package android.security.cts;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_26443 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 368319929)
    public void testPocCVE_2025_26443() {
        try {
            // Fetch the class loader for 'com.android.managedprovisioning' package.
            final Context context = getApplicationContext();
            final ClassLoader classLoader =
                    context.createPackageContext(
                                    new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                                            .resolveActivity(context.getPackageManager())
                                            .getPackageName(),
                                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE)
                            .getClassLoader();

            // Load the 'ClickableSpanFactory' class.
            final String managedProvisioningPkgClassPrefix = "com.android.managedprovisioning";
            final Class clickableSpanFactoryClass =
                    classLoader.loadClass(
                            managedProvisioningPkgClassPrefix + ".common.ClickableSpanFactory");

            // Load the 'ClickableSpanFactory' constructor.
            final Constructor clickableSpanFactoryConstructor =
                    clickableSpanFactoryClass.getDeclaredConstructor(int.class, Consumer.class);
            clickableSpanFactoryConstructor.setAccessible(true);

            // Create an instance of the 'ClickableSpanFactory' class.
            final Object clickableSpanFactoryInstance =
                    clickableSpanFactoryConstructor.newInstance(
                            0 /* linkColor */,
                            (Consumer<Intent>) (intent -> {}) /* ClickHandler */);

            // Load the 'UrlIntentFactory' class.
            final Class urlIntentFactoryClass =
                    classLoader.loadClass(
                            managedProvisioningPkgClassPrefix
                                    + ".common.HtmlToSpannedParser$UrlIntentFactory");

            // Create an instance of 'UrlIntentFactory' class.
            final Object urlIntentFactoryInstance =
                    Proxy.newProxyInstance(
                            urlIntentFactoryClass.getClassLoader(),
                            new Class[] {urlIntentFactoryClass},
                            new java.lang.reflect.InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, Method method, Object[] args)
                                        throws Throwable {
                                    if ("create".equals(method.getName())) {
                                        // Bypass the check in the vulnerable function to avoid the
                                        // removal of the URL in the case without the fix.
                                        return null;
                                    }
                                    return method.invoke(proxy, args);
                                }
                            });

            // Load the 'HtmlToSpannedParser' class.
            final Class htmlToSpannedParserClass =
                    classLoader.loadClass(
                            managedProvisioningPkgClassPrefix + ".common.HtmlToSpannedParser");

            // Load the 'HtmlToSpannedParser' constructor.
            final Constructor htmlToSpannedParserConstructor =
                    htmlToSpannedParserClass.getDeclaredConstructor(
                            clickableSpanFactoryClass, urlIntentFactoryClass);
            htmlToSpannedParserConstructor.setAccessible(true);

            // Create an instance of 'HtmlToSpannedParser' class.
            final Object htmlToSpannedParserInstance =
                    htmlToSpannedParserConstructor.newInstance(
                            clickableSpanFactoryInstance, urlIntentFactoryInstance);

            // Fetch the vulnerable method 'parseHtml()'.
            final Method parseHtmlMethod =
                    htmlToSpannedParserClass.getMethod("parseHtml", String.class);
            parseHtmlMethod.setAccessible(true);

            // Create malformed http url and call the vulnerable method.
            final String hrefKey = "cve_2025_26443_url";
            final String hrefValue = "content://" + context.getPackageName();
            final String malformedURI = "<a href=\"" + hrefValue + "\">" + hrefKey + "</a>";
            final SpannableStringBuilder parsedURI =
                    (SpannableStringBuilder)
                            parseHtmlMethod.invoke(htmlToSpannedParserInstance, malformedURI);
            if (parsedURI != null && parsedURI.toString().equals(hrefKey)) {
                URLSpan[] urlSpans =
                        parsedURI.getSpans(
                                0 /* queryStart */,
                                parsedURI.length() /* queryEnd */,
                                URLSpan.class);
                boolean isURLFound = false;
                for (URLSpan urlSpan : urlSpans) {
                    if (urlSpan.getURL().contains(hrefValue)) {
                        isURLFound = true;
                        break;
                    }
                }

                // Without the fix, malformed http url will be parsed.
                assertWithMessage(
                                "Device is vulnerable to b/368319929 !!"
                                        + " Malformed http url can be parsed.")
                        .that(isURLFound)
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
