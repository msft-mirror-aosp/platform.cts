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

package android.security.cts.bug_435188844.test;

import static android.security.cts.bug_435188844.UriUtils.addProfileId;
import static android.security.cts.bug_435188844.UriUtils.makeExtraContentUri;
import static android.security.cts.bug_435188844.UriUtils.makeImageUri;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.security.cts.bug_435188844.LoggingContentProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static final String EXTERNAL_EXTRA_CONTENT_PROVIDER_URI =
            "content://android.security.cts.bug_435188844.provider_extra";
    private static final String PRIVATE_EXTRA_CONTENT_PROVIDER_URI =
            "content://android.security.cts.bug_435188844.test_extra";
    private static final long WAIT_AND_ASSERT_FOUND_TIMEOUT_MS = 5_000;

    private int mTargetUser = -1;
    private String mChooserPackage = null;

    @Before
    public void setup() {
        Bundle args = InstrumentationRegistry.getArguments();

        mTargetUser = Integer.parseInt(args.getString("target_user", "-1"));
        assumeTrue("Could not find target user", mTargetUser != -1);
        mChooserPackage = resolveChooserPackage();
    }

    @After
    public void tearDown() {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
    }

    @Test
    public void testCrossProfileExtraContentProvider() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Uri extraContentUri =
                makeExtraContentUri(
                        addProfileId(Uri.parse(EXTERNAL_EXTRA_CONTENT_PROVIDER_URI), mTargetUser),
                        1,
                        mTargetUser);
        Intent chooserIntent = createChooser(extraContentUri);

        context.startActivity(chooserIntent);
        waitForChooser();

        verifyNoContentProviderAccess(extraContentUri);
    }

    @Test
    public void testCrossProfileItemsFromExtraContentProvider() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Uri extraContentUri =
                makeExtraContentUri(Uri.parse(PRIVATE_EXTRA_CONTENT_PROVIDER_URI), 2, mTargetUser);
        Intent chooserIntent = createChooser(extraContentUri);

        context.startActivity(chooserIntent);
        waitForChooser();

        verifyNoContentProviderAccess(makeImageUri(0, mTargetUser));
    }

    private Intent createChooser(Uri extraContentUri) {
        Intent targetIntent = new Intent(Intent.ACTION_SEND);
        targetIntent.setType("image/*");
        Uri imageUri = makeImageUri(0);
        targetIntent.putExtra(Intent.EXTRA_STREAM, imageUri);

        Intent chooserIntent = Intent.createChooser(targetIntent, "Test");
        chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        chooserIntent.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        chooserIntent.putExtra(Intent.EXTRA_CHOOSER_ADDITIONAL_CONTENT_URI, extraContentUri);
        chooserIntent.putExtra(Intent.EXTRA_CHOOSER_FOCUSED_ITEM_POSITION, 0);
        return chooserIntent;
    }

    private void waitForChooser() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UiDevice device = UiDevice.getInstance(instrumentation);
        device.waitForIdle();
        waitForPackageVisible(device, mChooserPackage);
    }

    private void verifyNoContentProviderAccess(Uri extraContentProviderUri) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getContext();
        UiAutomation automation = instrumentation.getUiAutomation();
        automation.adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS");
        Map<String, ArrayList<String>> invokedMethods;
        try {
            invokedMethods =
                    LoggingContentProvider.getInvokedMethods(context, extraContentProviderUri);
        } finally {
            automation.dropShellPermissionIdentity();
        }
        assertTrue(
                "Cross user URI reads detected. Invoked methods: " + invokedMethods,
                invokedMethods.isEmpty());
    }

    private boolean waitForPackageVisible(UiDevice device, String pkg) {
        return device.wait(Until.findObject(By.pkg(pkg).depth(0)), WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)
                != null;
    }

    private String resolveChooserPackage() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = context.getPackageManager();
        Intent shareIntent = Intent.createChooser(new Intent(), null);
        ResolveInfo chooser = pm.resolveActivity(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(chooser);
        assertNotNull(chooser.activityInfo);
        return chooser.activityInfo.packageName;
    }
}
