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
import android.location.Location;
import android.location.LocationResult;
import android.os.Bundle;
import android.os.IRemoteCallback;
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
import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_26458 extends StsExtraBusinessLogicTestCase {
    boolean mIsVulnerable = false;
    boolean mHasExecuted = false;

    @AsbSecurityTest(cveBugId = 388828203)
    @Test
    public void testPocCVE_2025_26458() {
        try {
            // Check if the DUT supports locations service.
            final Context context = getApplicationContext();
            PackageManager pkgManager = context.getPackageManager();
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
            // method "deliverOnLocationChanged".
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

            // Invoke the vulnerable method "deliverOnLocationChanged()".
            // "deliverOnLocationChanged()"
            // internally invokes the "sendIntentSender()" method. Using this method, we capture the
            // ActivityOptions and check if "mPendingIntentBalAllowed" flag is set to false.
            // By design, the "mPendingIntentBalAllowed" flag is set to true by default. With fix,
            // "mPendingIntentBalAllowed" flag gets set to false by calling
            // "setPendingIntentBackgroundActivityLaunchAllowed(false)".
            final Method deliverOnLocationChanged =
                    locationPendingIntentTransportClass.getDeclaredMethod(
                            "deliverOnLocationChanged",
                            LocationResult.class,
                            IRemoteCallback.class);
            deliverOnLocationChanged.setAccessible(true);
            deliverOnLocationChanged.invoke(
                    pendingIntentTransportInstance, createLocationResult(), null);
            assume().withMessage("Could not execute the intended function - sendIntentSender()")
                    .that(mHasExecuted)
                    .isTrue();
            assertWithMessage("Vulnerable to b/388828203!! Background Activity can be launched!!")
                    .that(mIsVulnerable)
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private LocationResult createLocationResult() throws IllegalStateException {
        // Set the required location data.
        final Location location = new Location("cve_2025_26458");
        location.setTime(System.currentTimeMillis());
        location.setLatitude(0.0);
        location.setLongitude(0.0);
        location.setAccuracy(0.0f);

        // Create an ArrayList of locations.
        final ArrayList<Location> locations = new ArrayList<>();
        locations.add(location);
        return LocationResult.create(locations);
    }

    private boolean replacemInstanceWithProxyInstance(Context context) throws Exception {
        // Get the singleton instance of the 'IActivityManager' using reflection.
        final IActivityManager activityManager =
                (IActivityManager)
                        ActivityManager.class.getDeclaredMethod("getService").invoke(null);
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
                                mIsVulnerable =
                                        activityOptions
                                                .isPendingIntentBackgroundActivityLaunchAllowed();
                                if (mIsVulnerable) break;
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
