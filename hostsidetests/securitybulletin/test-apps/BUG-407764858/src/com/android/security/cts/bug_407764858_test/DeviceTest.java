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

package com.android.security.cts.bug_407764858_test;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static final long TIMEOUT_MS = 10_000;

    @Test
    public void testCrossProfileIntentPolicyBypass() {
        Context context = getApplicationContext();
        Instrumentation instrumentation = getInstrumentation();
        UiDevice device = UiDevice.getInstance(instrumentation);

        try {
            ActivityInfo chooserActivityInfo = getChooserActivityInfo(context);
            assertNotNull("Test logic failure: can not resolve Chooser", chooserActivityInfo);
            ComponentName chooserActivity = new ComponentName(
                    chooserActivityInfo.applicationInfo.packageName,
                    chooserActivityInfo.name);
            CharSequence chooserAppLabel = chooserActivityInfo
                    .loadLabel(context.getPackageManager());
            assertNotNull("Test logic failure: can not resolve Chooser label", chooserAppLabel);

            context.startActivity(createChooserIntent(chooserActivity));

            device.waitForIdle(TIMEOUT_MS);

            assumeTrue(
                    "Selecting personal tab",
                    clickUiObject(device, By.text("Personal")));

            device.waitForIdle();

            if (!clickUiObject(device, By.text(chooserAppLabel.toString()))) {
                return;
            }

            device.waitForIdle();
            assumeNotNull(
                    device.wait(
                            Until.findObject(By.pkg(chooserActivity.getPackageName()).depth(0)),
                            TIMEOUT_MS));
            fail("Cross-Profile Intent Filter Bypass detected: Chooser should not be started.");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private static ActivityInfo getChooserActivityInfo(Context context) {
        ResolveInfo chooserResolveInfo = context.getPackageManager()
                .resolveActivity(
                        Intent.createChooser(new Intent(), null),
                        PackageManager.MATCH_DEFAULT_ONLY);
        return chooserResolveInfo == null ? null : chooserResolveInfo.activityInfo;
    }

    private static Intent createChooserIntent(ComponentName chooserActivity) {
        Intent targetIntent = new Intent(Intent.ACTION_SEND);
        targetIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/test.mp3"));
        targetIntent.setType("audio/mpeg");

        Intent evilIntent = new Intent(Intent.ACTION_MAIN);
        evilIntent.setComponent(chooserActivity);
        evilIntent.putExtra(Intent.EXTRA_INTENT, targetIntent);

        Intent chooserIntent = Intent.createChooser(evilIntent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooserIntent;
    }

    // Wait for UiObject to appear and click on the UiObject if it is visible
    private boolean clickUiObject(UiDevice device, BySelector selector) {
        if (!device.wait(Until.hasObject(selector), TIMEOUT_MS)) {
            return false;
        }
        UiObject2 object = device.findObject(selector);
        if (object == null) {
            return false;
        }
        object.click();
        return true;
    }
}
