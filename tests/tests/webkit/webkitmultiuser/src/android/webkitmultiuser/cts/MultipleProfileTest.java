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

package android.webkitmultiuser.cts;

import android.webkit.WebView;
import android.webkit.cts.WebViewOnUiThread;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.MediumTest;

import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser;
import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(BedsteadJUnit4.class)
public class MultipleProfileTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    private WebView mWebView;
    private WebViewOnUiThread mOnUiThread;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        activity -> {
                            WebViewCtsActivity webViewCtsActivity = (WebViewCtsActivity) activity;
                            WebView webview = webViewCtsActivity.getWebView();
                            if (webview != null) {
                                mOnUiThread = new WebViewOnUiThread(webview);
                            }
                        });
    }

    @After
    public void cleanup() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    @Test
    @RequireRunOnSecondaryUser
    public void testSecondaryUser() throws Exception {
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
    }

    @Test
    @RequireRunOnWorkProfile
    public void testManagedUser() throws Exception {
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
    }
}
