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

package android.webkit.cts;

import static org.junit.Assert.*;

import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains the tests for android.webkit APIs which were removed from the SDK long ago
 * and so references to them will not compile without using `platform_apis: true`.
 */
// TODO(b/435479667): find a proper way to test removed APIs.
@MediumTest
@RunWith(AndroidJUnit4.class)
public class RemovedApisTest {
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
                            WebView webview = ((WebViewCtsActivity) activity).getWebView();
                            if (webview != null) {
                                mWebView = webview;
                                mOnUiThread = new WebViewOnUiThread(webview);
                            }
                        });
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    @Test
    public void testWebSettings_AccessPluginsPath() {
        WebSettings settings = mOnUiThread.getSettings();
        assertEquals("Plugin path always empty", "", settings.getPluginsPath());

        String pluginPath = "pluginPath";
        settings.setPluginsPath(pluginPath);
        assertEquals("Plugin path always empty", "", settings.getPluginsPath());
    }

    @Test
    public void testWebSettings_AccessUseDoubleTree() {
        WebSettings settings = mOnUiThread.getSettings();
        assertFalse(settings.getUseDoubleTree());

        settings.setUseDoubleTree(true);
        assertFalse("setUseDoubleTree should be a no-op", settings.getUseDoubleTree());
    }

    @Test
    public void testWebSettings_AppCacheEnabled() {
        // Just test that calling the method doesn't crash. AppCache has been
        // removed from Chromium and this is now a no-op.
        WebSettings settings = mOnUiThread.getSettings();
        settings.setAppCacheEnabled(true);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testWebView_GetVisibleTitleHeight() throws Exception {
        WebkitUtils.onMainThreadSync(
                () -> {
                    mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
                    assertEquals(0, mWebView.getVisibleTitleHeight());
                });
    }

    @Test
    public void testWebView_PlatformNotifications() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    WebView.enablePlatformNotifications();
                    WebView.disablePlatformNotifications();
                });
    }

    @Test
    public void testWebView_AccessPluginList() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    assertNotNull(WebView.getPluginList());
                    mWebView.refreshPlugins(false);
                });
    }

    @Test
    public void testWebView_DebugDump() {
        WebkitUtils.onMainThreadSync(
                () -> {
                    mWebView.debugDump();
                });
    }

    @Test
    public void testGetZoomControls() {
        WebSettings settings = mOnUiThread.getSettings();
        assertTrue(settings.supportZoom());
        assertNotNull(
                "Should be able to get zoom controls when zoom is enabled",
                WebkitUtils.onMainThreadSync(
                        () -> {
                            return mWebView.getZoomControls();
                        }));

        // disable zoom support
        settings.setSupportZoom(false);
        assertFalse(settings.supportZoom());
        assertNull(
                "Should not be able to get zoom controls when zoom is disabled",
                WebkitUtils.onMainThreadSync(
                        () -> {
                            return mWebView.getZoomControls();
                        }));
    }
}
