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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.util.Singleton;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.PathClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_21088 extends StsExtraBusinessLogicTestCase {
    boolean mIsPendingIntentBackgroundActivityLaunchAllowed = false;
    boolean mHasExecuted = false;

    @Test
    @AsbSecurityTest(cveBugId = 235823542)
    public void testPocCVE_2023_21088() {
        try {
            // Check if the DUT supports locations service.
            final Context context = getApplicationContext();
            final PackageManager pkgManager = context.getPackageManager();
            assume().withMessage("DUT does not support location service!!")
                    .that(
                            pkgManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
                                    && pkgManager.hasSystemFeature(
                                            PackageManager.FEATURE_LOCATION_GPS))
                    .isTrue();

            // Create a proxy instance to intercept "sendIntentSender" function.
            assume().withMessage("Could not replace mInstance successfully!!")
                    .that(replacemInstanceWithProxyInstance(context))
                    .isTrue();

            // Load the "LocationPendingIntentTransport" subclass to invoke the vulnerable
            // method "deliverOnFlushComplete()".
            final PathClassLoader classLoader =
                    new PathClassLoader(
                            "/system/framework/services.jar", ClassLoader.getSystemClassLoader());
            final Class locationPendingIntentTransportClass =
                    classLoader.loadClass(
                            "com.android.server.location.provider.LocationProviderManager"
                                    + "$LocationPendingIntentTransport");
            final Object pendingIntentTransportInstance =
                    locationPendingIntentTransportClass
                            .getDeclaredConstructor(Context.class, PendingIntent.class)
                            .newInstance(
                                    context,
                                    PendingIntent.getActivity(
                                            context,
                                            0 /* Id */,
                                            new Intent(Settings.ACTION_SETTINGS),
                                            PendingIntent.FLAG_IMMUTABLE));

        // Invoke the vulnerable method "deliverOnFlushComplete()". "deliverOnFlushComplete()"
        // internally invokes the "sendIntentSender()" method. Using this method, we capture the
        // ActivityOptions and check if "mPendingIntentBalAllowed" flag is set to false.
        // By design, the "mPendingIntentBalAllowed" flag is set to true by default. With fix,
        // "mPendingIntentBalAllowed" flag gets set to false by calling
        // "setPendingIntentBackgroundActivityLaunchAllowed(false)".
            final Method deliverOnFlushComplete =
                    locationPendingIntentTransportClass.getDeclaredMethod(
                            "deliverOnFlushComplete", int.class);
            deliverOnFlushComplete.setAccessible(true);
            deliverOnFlushComplete.invoke(pendingIntentTransportInstance, 1 /* requestCode */);
            assume().withMessage("Could not execute the intended function - sendIntentSender()")
                    .that(mHasExecuted)
                    .isTrue();
            assertWithMessage(
                            "Vulnerable to b/235823542!! Foreground Activity can be launched from"
                                    + " Background !!")
                    .that(mIsPendingIntentBackgroundActivityLaunchAllowed)
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private boolean replacemInstanceWithProxyInstance(Context context) throws Exception {
        // Get the singleton instance of the 'IActivityManager' using reflection.
        final IActivityManager activityManager =
                (IActivityManager)
                        ActivityManager.class.getDeclaredMethod("getService", null).invoke(null);
        final InvocationHandler handler =
                (object, method, args) -> {
                    if (method.getName().contains("sendIntentSender")) {
                        mHasExecuted = true;

                        // Check if argument type is Bundle to retrieve 'ActivityOptions'.
                        for (Object arg : args) {
                            if (arg != null && arg.getClass().equals(Bundle.class)) {
                                Bundle activityOptionsBundle = (Bundle) arg;
                                ActivityOptions activityOptions =
                                        new ActivityOptions(activityOptionsBundle);
                                mIsPendingIntentBackgroundActivityLaunchAllowed =
                                        activityOptions
                                                .isPendingIntentBackgroundActivityLaunchAllowed();
                                if (mIsPendingIntentBackgroundActivityLaunchAllowed) break;
                                return 0;
                            }
                        }
                    }
                    return method.invoke(activityManager, args);
                };

        // Create a proxy instance to override 'sendIntentSender' method.
        final IActivityManager proxyActivityManager =
                (IActivityManager)
                        Proxy.newProxyInstance(
                                IActivityManager.class.getClassLoader(),
                                new Class[] {IActivityManager.class},
                                handler);

        // Replace the original 'mInstance' of 'IActivityManagerSingleton' with
        // 'proxyActivityManager' created above.
        for (Field activityManagerField : ActivityManager.class.getDeclaredFields()) {
            if (activityManagerField.getName().contains("IActivityManagerSingleton")) {
                activityManagerField.setAccessible(true);
                final Field mInstance =
                        context.getClassLoader()
                                .loadClass(Singleton.class.getName())
                                .getDeclaredField("mInstance");
                mInstance.setAccessible(true);
                mInstance.set(activityManagerField.get(null), proxyActivityManager);
                return true;
            }
        }
        return false;
    }
}
