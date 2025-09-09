/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.cts.WebViewSyncLoader.WaitForLoadedClient;

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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.mockito.internal.matchers.InstanceOf;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ServiceWorkerClientTest extends SharedWebViewTest {

    // The base URL used with these paths does not matter since the tests will
    // intercept the load, but it should be https for the Service Worker
    // registration to succeed.
    // Each test should use a separate base URL to ensure isolation.
    private static final String INDEX_PATH = "/index.html";
    private static final String SW_PATH = "/sw.js";
    private static final String FETCH_PATH = "/fetch.html";

    private static final String JS_INTERFACE_NAME = "instrumentation";

    // static HTML page always injected instead of the url loaded.
    private static final String INDEX_RAW_HTML =
            """
            <!DOCTYPE html>
            <html>
              <body>
                <script>
                  navigator.serviceWorker.register('sw.js').then(function(reg) {
                    instrumentation.registrationSuccess();
                  }).catch(function(err) {
                    instrumentation.registrationError();
                    console.error(err);
                  });
                </script>
              </body>
            </html>
            """;
    private static final String SW_RAW_HTML = "fetch('fetch.html');";
    private static final String SW_UNREGISTER_RAW_JS =
            """
            navigator.serviceWorker.getRegistration().then(function(r) {
              r.unregister().then(function(success) {
                if (success) {
                  instrumentation.unregisterSuccess();
                } else {
                  console.error('unregister() was not successful');
                }
              });
            }).catch(function(err) {
               console.error(err);
            });
            """;

    @Rule
    public ActivityScenarioRule<WebViewCtsActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(WebViewCtsActivity.class);

    private JavascriptStatusReceiver mJavascriptStatusReceiver;
    private WebViewOnUiThread mOnUiThread;

    // Both this test and WebViewOnUiThread need to override some of the methods on WebViewClient,
    // so this test subclasses the WebViewClient from WebViewOnUiThread.
    private static class InterceptClient extends WaitForLoadedClient {

        public InterceptClient(WebViewOnUiThread webViewOnUiThread) throws Exception {
            super(webViewOnUiThread);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(
                WebView view, WebResourceRequest request) {
            // Only return content for INDEX_PATH, deny all other requests.
            if (request.getUrl().toString().endsWith(INDEX_PATH)) {
                return new WebResourceResponse(
                        "text/html",
                        "utf-8",
                        new ByteArrayInputStream(INDEX_RAW_HTML.getBytes(StandardCharsets.UTF_8)));
            }
            return new WebResourceResponse("text/html", "UTF-8", null);
        }
    }

    public static class InterceptServiceWorkerClient extends ServiceWorkerClient {
        private final BlockingQueue<WebResourceRequest> mInterceptedRequests =
                new LinkedBlockingQueue<>();

        @Override
        public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
            // Records intercepted requests and only return content for SW_PATH.
            mInterceptedRequests.add(request);
            if (request.getUrl().toString().endsWith(SW_PATH)) {
                return new WebResourceResponse(
                        "application/javascript",
                        "utf-8",
                        new ByteArrayInputStream(SW_RAW_HTML.getBytes(StandardCharsets.UTF_8)));
            }
            return new WebResourceResponse("text/html", "UTF-8", null);
        }

        List<WebResourceRequest> waitForInterceptedRequests(int elementsToWaitFor) {
            List<WebResourceRequest> requests = new ArrayList<>(elementsToWaitFor);
            for (int i = 0; i < elementsToWaitFor; i++) {
                WebResourceRequest request =
                        WebkitUtils.waitForNextQueueElement(mInterceptedRequests);
                requests.add(request);
            }
            return requests;
        }
    }

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview == null) return;
        mOnUiThread = new WebViewOnUiThread(webview);
        mOnUiThread.getSettings().setJavaScriptEnabled(true);

        mJavascriptStatusReceiver = new JavascriptStatusReceiver();
        mOnUiThread.addJavascriptInterface(mJavascriptStatusReceiver, JS_INTERFACE_NAME);
        mOnUiThread.setWebViewClient(new InterceptClient(mOnUiThread));
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
            ServiceWorkerController.getInstance().setServiceWorkerClient(null);
        }
    }

    @Override
    protected WebViewTestEnvironment createTestEnvironment() {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        WebViewTestEnvironment.Builder builder = new WebViewTestEnvironment.Builder();

        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        activity -> {
                            WebView webView = activity.getWebView();
                            builder.setHostAppInvoker(
                                            WebViewTestEnvironment.createHostAppInvoker(activity))
                                    .setContext(activity)
                                    .setWebView(webView);
                        });

        return builder.build();
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.ServiceWorkerClientCompatTest#testServiceWorkerClientInterceptCallback.
     * Modifications to this test should be reflected in that test as necessary. See
     * http://go/modifying-webview-cts.
     */
    // Test correct invocation of shouldInterceptRequest for Service Workers.
    @Test
    public void testServiceWorkerClientInterceptCallback() throws Exception {
        // This must be different from other tests to ensure that service worker state does not leak
        // between tests.
        final String baseUrl = "https://with-service-worker-client.test";
        final InterceptServiceWorkerClient mInterceptServiceWorkerClient =
                new InterceptServiceWorkerClient();
        ServiceWorkerController swController = ServiceWorkerController.getInstance();
        swController.setServiceWorkerClient(mInterceptServiceWorkerClient);

        mOnUiThread.loadUrlAndWaitForCompletion(baseUrl + INDEX_PATH);

        assertTrue(
                "JS could not register Service Worker",
                mJavascriptStatusReceiver.waitForRegistrationSuccess());

        List<WebResourceRequest> requests =
                mInterceptServiceWorkerClient.waitForInterceptedRequests(2);
        assertEquals(2, requests.size());
        assertEquals(baseUrl + SW_PATH, requests.get(0).getUrl().toString());
        assertEquals(baseUrl + FETCH_PATH, requests.get(1).getUrl().toString());

        // Clean-up, make sure to unregister the Service Worker.
        mOnUiThread.evaluateJavascript(SW_UNREGISTER_RAW_JS, null);
        assertTrue(
                "JS could not unregister Service Worker",
                mJavascriptStatusReceiver.waitForUnregisterSuccess());
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.ServiceWorkerClientCompatTest#testSetNullServiceWorkerClient.
     * Modifications to this test should be reflected in that test as necessary. See
     * http://go/modifying-webview-cts.
     */
    // Test setting a null ServiceWorkerClient.
    @Test
    public void testSetNullServiceWorkerClient() throws Exception {
        // This must be different from other tests to ensure that service worker state does not leak
        // between tests.
        final String baseUrl = "https://without-service-worker-client.test";
        ServiceWorkerController swController = ServiceWorkerController.getInstance();
        swController.setServiceWorkerClient(null);
        mOnUiThread.loadUrlAndWaitForCompletion(baseUrl + INDEX_PATH);

        // With a null ServiceWorkerClient, we won't be intercepting the request for the service
        // worker JS file, so registration should fail.
        assertTrue(
                "JS unexpectedly registered the Service Worker",
                mJavascriptStatusReceiver.waitForRegistrationError());
    }

    // Object added to the page via AddJavascriptInterface() that is used by the test Javascript to
    // notify back to Java if the Service Worker registration was successful.
    public static final class JavascriptStatusReceiver {
        private final BlockingQueue<Boolean> mRegistrationSuccessQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mUnregisterSuccessQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mRegistrationErrorQueue = new LinkedBlockingQueue<>();

        Boolean waitForRegistrationSuccess() {
            return WebkitUtils.waitForNextQueueElement(mRegistrationSuccessQueue);
        }

        Boolean waitForRegistrationError() {
            return WebkitUtils.waitForNextQueueElement(mRegistrationErrorQueue);
        }

        Boolean waitForUnregisterSuccess() {
            return WebkitUtils.waitForNextQueueElement(mUnregisterSuccessQueue);
        }

        /** Called by test JavaScript when service worker registration succeeds. */
        @JavascriptInterface
        public void registrationSuccess() {
            mRegistrationSuccessQueue.add(true);
        }

        /** Called by test JavaScript when service worker registration fails. */
        @JavascriptInterface
        public void registrationError() {
            mRegistrationErrorQueue.add(true);
        }

        /** Called by test JavaScript when service worker unregistration succeeds. */
        @JavascriptInterface
        public void unregisterSuccess() {
            mUnregisterSuccessQueue.add(true);
        }
    }
}
