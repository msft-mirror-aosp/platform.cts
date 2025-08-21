/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Message;
import android.platform.test.annotations.AppModeFull;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.cts.WebViewSyncLoader.WaitForLoadedClient;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewClientTest extends SharedWebViewTest {
    private static final String TEST_URL = "http://www.example.com/";

    @Rule
    public ActivityScenarioRule<WebViewCtsActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(WebViewCtsActivity.class);

    private WebViewOnUiThread mOnUiThread;
    private SharedSdkWebServer mWebServer;

    private static final String TEST_SAFE_BROWSING_URL_PREFIX =
            "chrome://safe-browsing/match?type=";
    private static final String TEST_SAFE_BROWSING_MALWARE_URL =
            TEST_SAFE_BROWSING_URL_PREFIX + "malware";
    private static final String TEST_SAFE_BROWSING_PHISHING_URL =
            TEST_SAFE_BROWSING_URL_PREFIX + "phishing";
    private static final String TEST_SAFE_BROWSING_UNWANTED_SOFTWARE_URL =
            TEST_SAFE_BROWSING_URL_PREFIX + "unwanted";
    private static final String TEST_SAFE_BROWSING_BILLING_URL =
            TEST_SAFE_BROWSING_URL_PREFIX + "billing";

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        if (mWebServer != null) {
            mWebServer.shutdown();
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
                            WebView webView = ((WebViewCtsActivity) activity).getWebView();

                            builder.setHostAppInvoker(
                                            WebViewTestEnvironment.createHostAppInvoker(activity))
                                    .setContext(activity)
                                    .setWebView(webView)
                                    .setRootLayout(((WebViewCtsActivity) activity).getRootLayout());
                        });

        WebViewTestEnvironment environment = builder.build();

        if (environment.getWebView() != null) {
            WebkitUtils.checkForWindowFocus((Activity) environment.getContext());
        }

        return environment;
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testShouldOverrideUrlLoadingDefault. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    // Verify that the shouldoverrideurlloading is false by default
    @Test
    public void testShouldOverrideUrlLoadingDefault() {
        final WebViewClient webViewClient = new WebViewClient();
        assertFalse(webViewClient.shouldOverrideUrlLoading(mOnUiThread.getWebView(), new String()));
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testShouldOverrideUrlLoading. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    // Verify shouldoverrideurlloading called on top level navigation
    @Test
    public void testShouldOverrideUrlLoading() {
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        String data =
                "<html><body>"
                        + "<a href=\""
                        + TEST_URL
                        + "\" id=\"link\">new page</a>"
                        + "</body></html>";
        mOnUiThread.loadDataAndWaitForCompletion(data, "text/html", null);
        clickOnLinkUsingJs("link", mOnUiThread);
        ShouldOverrideUrlLoadingRequest overrideRequest =
                webViewClient.waitForShouldOverrideUrlLoading();
        assertNotNull(overrideRequest);
        assertEquals(TEST_URL, overrideRequest.mUrl);
        assertNotNull(overrideRequest.mRequest);
        assertTrue(overrideRequest.mRequest.isForMainFrame());
        assertFalse(overrideRequest.mRequest.isRedirect());
        assertFalse(overrideRequest.mRequest.hasGesture());
    }

    // Verify shouldoverrideurlloading called on webview called via onCreateWindow
    // TODO(sgurun) upstream this test to Aw.
    @Test
    public void testShouldOverrideUrlLoadingOnCreateWindow() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        // WebViewClient for main window
        final MockWebViewClient mainWebViewClient = new MockWebViewClient();
        // WebViewClient for child window
        final MockWebViewClient childWebViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(mainWebViewClient);
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        mOnUiThread.getSettings().setSupportMultipleWindows(true);

        final WebView childWebView = mOnUiThread.createWebView();

        try {
            WebViewOnUiThread childWebViewOnUiThread = new WebViewOnUiThread(childWebView);
            mOnUiThread.setWebChromeClient(
                    new WebChromeClient() {
                        @Override
                        public boolean onCreateWindow(
                                WebView view,
                                boolean isDialog,
                                boolean isUserGesture,
                                Message resultMsg) {
                            WebView.WebViewTransport transport =
                                    (WebView.WebViewTransport) resultMsg.obj;
                            childWebView.setWebViewClient(childWebViewClient);
                            childWebView.getSettings().setJavaScriptEnabled(true);
                            transport.setWebView(childWebView);
                            getTestEnvironment()
                                    .addContentView(
                                            childWebView,
                                            new ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.FILL_PARENT,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                            resultMsg.sendToTarget();
                            return true;
                        }
                    });
            {
                mOnUiThread.loadUrl(mWebServer.getAssetUrl(TestHtmlConstants.BLANK_TAG_URL));

                assertNotNull(childWebViewClient.waitForOnPageFinished());
                ShouldOverrideUrlLoadingRequest overrideRequest =
                        childWebViewClient.waitForShouldOverrideUrlLoading();
                assertEquals(
                        mWebServer.getAssetUrl(TestHtmlConstants.PAGE_WITH_LINK_URL),
                        overrideRequest.mUrl);
            }

            clickOnLinkUsingJs("link", childWebViewOnUiThread);
            ShouldOverrideUrlLoadingRequest overrideRequest =
                    childWebViewClient.waitForShouldOverrideUrlLoading();
            assertTrue(mainWebViewClient.isShouldOverrideUrlLoadingQueueEmpty());
            // PAGE_WITH_LINK_URL has a link to BLANK_PAGE_URL (an arbitrary page
            // also controlled by the test server)
            assertEquals(
                    mWebServer.getAssetUrl(TestHtmlConstants.BLANK_PAGE_URL), overrideRequest.mUrl);

        } finally {
            WebkitUtils.onMainThreadSync(
                    () -> {
                        ViewParent parent = childWebView.getParent();
                        if (parent instanceof ViewGroup) {
                            ((ViewGroup) parent).removeView(childWebView);
                        }
                        childWebView.destroy();
                    });
        }
    }

    private void clickOnLinkUsingJs(final String linkId, WebViewOnUiThread webViewOnUiThread) {
        assertEquals(
                "null",
                webViewOnUiThread.evaluateJavascriptSync(
                        "document.getElementById('"
                                + linkId
                                + "').click();"
                                + "console.log('element with id ["
                                + linkId
                                + "] clicked');"));
    }

    @Test
    public void testLoadPage() throws Exception {
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);

        assertFalse(webViewClient.hasOnPageStartedCalled());
        assertFalse(webViewClient.hasOnLoadResourceCalled());
        assertFalse(webViewClient.hasOnPageFinishedCalled());
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        assertNotNull(webViewClient.waitForOnPageStart());
        assertNotNull(webViewClient.waitForOnLoadResource());
        assertNotNull(webViewClient.waitForOnPageFinished());
    }

    @Test
    public void testOnReceivedLoginRequest() throws Exception {
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        // set the url and html
        final String path = "/main";
        final String page = "<head></head><body>test onReceivedLoginRequest</body>";
        final String headerName = "x-auto-login";
        final String headerValue = "realm=com.google&account=foo%40bar.com&args=random_string";
        List<HttpHeader> headers = new ArrayList<HttpHeader>();
        headers.add(HttpHeader.create(headerName, headerValue));

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.setResponse(path, page, headers);
        assertFalse(webViewClient.hasOnReceivedLoginRequest());
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        LoginRequest request = webViewClient.waitForLoginRequest();
        assertEquals("com.google", request.mRealm);
        assertEquals("foo@bar.com", request.mAccount);
        assertEquals("random_string", request.mArgs);
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnReceivedError. Modifications to this test
     * should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnReceivedError() throws Exception {
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        String wrongUri = "invalidscheme://some/resource";
        assertFalse(webViewClient.hasOnReceivedError());
        mOnUiThread.loadUrlAndWaitForCompletion(wrongUri);
        assertEquals(
                Integer.valueOf(WebViewClient.ERROR_UNSUPPORTED_SCHEME),
                webViewClient.waitForOnReceivedError());
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnReceivedErrorForSubresource. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnReceivedErrorForSubresource() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);

        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        assertFalse(webViewClient.hasOnReceivedResourceError());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.BAD_IMAGE_PAGE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        WebResourceError error = webViewClient.waitForOnReceivedResourceError();
        assertNotNull(error);
        assertEquals(WebViewClient.ERROR_UNSUPPORTED_SCHEME, error.getErrorCode());
    }

    @Test
    public void testOnReceivedHttpError() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        assertFalse(webViewClient.hasOnReceivedHttpError());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.NON_EXISTENT_PAGE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        WebResourceResponse errorResponse = webViewClient.waitForOnReceivedHttpError();
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.getStatusCode());
    }

    @Test
    public void testOnFormResubmission() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);

        assertFalse(webViewClient.hasOnFormResubmissionCalled());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.JS_FORM_URL);
        // this loads a form, which automatically posts itself
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // wait for JavaScript to post the form
        mOnUiThread.waitForLoadCompletion();
        assertNotEquals(
                "The URL should have changed when the form was posted", url, mOnUiThread.getUrl());
        // reloading the current URL should trigger the callback
        mOnUiThread.reload();
        assertNotNull(webViewClient.waitForOnFormResubmission());
    }

    @Test
    public void testDoUpdateVisitedHistory() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        assertFalse(webViewClient.hasDoUpdateVisitedHistoryCalled());
        String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        String url2 = mWebServer.getAssetUrl(TestHtmlConstants.BR_TAG_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url1);
        mOnUiThread.loadUrlAndWaitForCompletion(url2);
        assertNotNull(webViewClient.waitForDoUpdateVisitedHistory());
    }

    @Test
    public void testOnReceivedHttpAuthRequest() throws Exception {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        assertFalse(webViewClient.hasOnReceivedHttpAuthRequestCalled());
        String url = mWebServer.getAuthAssetUrl(TestHtmlConstants.EMBEDDED_IMG_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertNotNull(webViewClient.waitForOnReceivedHttpAuthRequest());
    }

    @Test
    public void testShouldOverrideKeyEvent() {
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        assertFalse(webViewClient.shouldOverrideKeyEvent(mOnUiThread.getWebView(), null));
    }

    @Test
    public void testOnUnhandledKeyEvent() throws Throwable {
        requireLoadedPage();
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        mOnUiThread.requestFocus();
        getTestEnvironment().waitForIdleSync();

        assertFalse(webViewClient.hasOnUnhandledKeyEventCalled());
        getTestEnvironment().sendKeyDownUpSync(KeyEvent.KEYCODE_1);

        assertNotNull(webViewClient.waitForOnUnhandledKeyEvent());
    }

    @Test
    public void testOnScaleChanged() throws Throwable {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        assertFalse(webViewClient.hasOnScaleChangedCalled());
        String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url1);

        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.canZoomIn();
            }
        }.run();

        assertTrue(mOnUiThread.zoomIn());
        assertNotNull(webViewClient.waitForOnScaleChanged());
    }

    // Test that shouldInterceptRequest is called with the correct parameters
    @Test
    public void testShouldInterceptRequestParams() throws Throwable {
        final String mainPath = "/main";
        final String mainPage = "<head></head><body>test page</body>";
        final String headerName = "x-test-header-name";
        final String headerValue = "testheadervalue";
        HashMap<String, String> headers = new HashMap<String, String>(1);
        headers.put(headerName, headerValue);

        // A client which saves the WebResourceRequest as interceptRequest
        final class TestClient extends WaitForLoadedClient {
            TestClient() {
                super(mOnUiThread);
            }

            private final SettableFuture<WebResourceRequest> mRequestFuture =
                    SettableFuture.create();

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                assertNotNull(view);
                assertNotNull(request);

                assertEquals(view, mOnUiThread.getWebView());

                // Save the main page request; discard any other requests (e.g. for favicon.ico)
                if (request.getUrl().getPath().equals(mainPath)) {
                    mRequestFuture.set(request);
                }

                return null;
            }
        }

        TestClient client = new TestClient();
        mOnUiThread.setWebViewClient(client);

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String mainUrl = mWebServer.setResponse(mainPath, mainPage, null);
        mOnUiThread.loadUrlAndWaitForCompletion(mainUrl, headers);
        // Inspect the fields of the saved WebResourceRequest
        WebResourceRequest interceptRequest = WebkitUtils.waitForFuture(client.mRequestFuture);
        assertNotNull(interceptRequest);
        assertEquals(mainUrl, interceptRequest.getUrl().toString());
        assertTrue(interceptRequest.isForMainFrame());
        assertEquals(mWebServer.getLastRequest(mainPath).getMethod(), interceptRequest.getMethod());
        // Web request headers are case-insensitive. We provided lower-case headerName and
        // headerValue. This will pass implementations which either do not mangle case,
        // convert to lowercase, or convert to uppercase but return a case-insensitive map.
        Map<String, String> interceptHeaders = interceptRequest.getRequestHeaders();
        assertTrue(interceptHeaders.containsKey(headerName));
        assertEquals(headerValue, interceptHeaders.get(headerName));
    }

    // Test that the WebResourceResponse returned by shouldInterceptRequest is handled correctly
    @Test
    public void testShouldInterceptRequestResponse() throws Throwable {
        final String mainPath = "/main";
        final String mainPage = "<head></head><body>test page</body>";
        final String interceptPath = "/intercept_me";

        // A client which responds to requests for interceptPath with a saved interceptResponse
        final class TestClient extends WaitForLoadedClient {
            public TestClient() {
                super(mOnUiThread);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().contains(interceptPath)) {
                    assertNotNull(mInterceptresponse);
                    return mInterceptresponse;
                }

                return null;
            }

            volatile WebResourceResponse mInterceptresponse;
        }

        mOnUiThread.getSettings().setJavaScriptEnabled(true);

        TestClient client = new TestClient();
        mOnUiThread.setWebViewClient(client);

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String interceptUrl = mWebServer.getAbsoluteUrl(interceptPath);
        // JavaScript which makes a synchronous AJAX request and logs and returns the status
        String js =
                "(function() {"
                        + "  var xhr = new XMLHttpRequest();"
                        + "  xhr.open('GET', '"
                        + interceptUrl
                        + "', false);"
                        + "  xhr.send(null);"
                        + "  console.info('xhr.status = ' + xhr.status);"
                        + "  console.info('xhr.statusText = ' + xhr.statusText);"
                        + "  return '[' + xhr.status + '][' + xhr.statusText + ']';"
                        + "})();";
        String mainUrl = mWebServer.setResponse(mainPath, mainPage, null);
        mOnUiThread.loadUrlAndWaitForCompletion(mainUrl, null);
        // Test a nonexistent page
        client.mInterceptresponse = new WebResourceResponse("text/html", "UTF-8", null);
        assertEquals("\"[404][Not Found]\"", mOnUiThread.evaluateJavascriptSync(js));
        // Test an empty page
        client.mInterceptresponse =
                new WebResourceResponse(
                        "text/html", "UTF-8", new ByteArrayInputStream(new byte[0]));
        assertEquals("\"[200][OK]\"", mOnUiThread.evaluateJavascriptSync(js));
        // Test a nonempty page with unusual response code/text
        client.mInterceptresponse =
                new WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        123,
                        "unusual",
                        null,
                        new ByteArrayInputStream("nonempty page".getBytes(StandardCharsets.UTF_8)));
        assertEquals("\"[123][unusual]\"", mOnUiThread.evaluateJavascriptSync(js));
    }

    // Verify that OnRenderProcessGone returns false by default
    @Test
    public void testOnRenderProcessGoneDefault() throws Throwable {
        final WebViewClient webViewClient = new WebViewClient();
        assertFalse(webViewClient.onRenderProcessGone(mOnUiThread.getWebView(), null));
    }

    @Test
    public void testOnRenderProcessGone() throws Throwable {
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.loadUrl("chrome://kill");
        RenderProcessGoneDetail detail = webViewClient.waitForOnRenderProcessGone();
        assertNotNull(detail);
        assertFalse(detail.didCrash()); // Render process does not crash on chrome://kill
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnSafeBrowsingHitBackToSafety. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    @Ignore("b/40196091")
    public void testOnSafeBrowsingHitBackToSafety() throws Throwable {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        final String ORIGINAL_URL = mOnUiThread.getUrl();

        final SafeBrowsingBackToSafetyClient backToSafetyWebViewClient =
                new SafeBrowsingBackToSafetyClient();
        mOnUiThread.setWebViewClient(backToSafetyWebViewClient);
        mOnUiThread.getSettings().setSafeBrowsingEnabled(true);

        // Note: Safe Browsing is enabled by default, and will work on chrome://safe-browsing/ URLs
        // regardless of user opt-in or GMS state (because this URL will never be sent to GMS Core).
        assertFalse(backToSafetyWebViewClient.hasOnReceivedError());
        mOnUiThread.loadUrlAndWaitForCompletion(TEST_SAFE_BROWSING_MALWARE_URL);

        assertEquals(
                TEST_SAFE_BROWSING_MALWARE_URL,
                backToSafetyWebViewClient.getOnSafeBrowsingHitRequest().getUrl().toString());
        assertTrue(backToSafetyWebViewClient.getOnSafeBrowsingHitRequest().isForMainFrame());
        assertEquals(
                "Back to safety should produce a network error",
                Integer.valueOf(WebViewClient.ERROR_UNSAFE_RESOURCE),
                backToSafetyWebViewClient.waitForOnReceivedError());
        assertEquals("Back to safety should navigate backward", ORIGINAL_URL, mOnUiThread.getUrl());
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnSafeBrowsingHitProceed. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    @Ignore("b/40196091")
    public void testOnSafeBrowsingHitProceed() throws Throwable {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        final String ORIGINAL_URL = mOnUiThread.getUrl();

        final SafeBrowsingProceedClient proceedWebViewClient = new SafeBrowsingProceedClient();
        mOnUiThread.setWebViewClient(proceedWebViewClient);

        // Note: Safe Browsing is enabled by default, and will work on chrome://safe-browsing/ URLs
        // regardless of user opt-in or GMS state (because this URL will never be sent to GMS Core).
        assertFalse(proceedWebViewClient.hasOnReceivedError());
        mOnUiThread.loadUrlAndWaitForCompletion(TEST_SAFE_BROWSING_MALWARE_URL);

        assertEquals(
                TEST_SAFE_BROWSING_MALWARE_URL,
                proceedWebViewClient.getOnSafeBrowsingHitRequest().getUrl().toString());
        assertTrue(proceedWebViewClient.getOnSafeBrowsingHitRequest().isForMainFrame());

        assertEquals(
                "Proceed button should navigate to the page",
                TEST_SAFE_BROWSING_MALWARE_URL,
                mOnUiThread.getUrl());
    }

    private void testOnSafeBrowsingCode(String expectedUrl, int expectedThreatType)
            throws Throwable {
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        final SafeBrowsingBackToSafetyClient backToSafetyWebViewClient =
                new SafeBrowsingBackToSafetyClient();
        mOnUiThread.setWebViewClient(backToSafetyWebViewClient);

        // Note: Safe Browsing is enabled by default, and will work on chrome://safe-browsing/ URLs
        // regardless of user opt-in or GMS state (because this URL will never be sent to GMS Core).
        mOnUiThread.loadUrlAndWaitForCompletion(expectedUrl);

        assertEquals(
                "Safe Browsing hit is for unexpected URL",
                expectedUrl,
                backToSafetyWebViewClient.getOnSafeBrowsingHitRequest().getUrl().toString());

        assertEquals(
                "Safe Browsing hit has unexpected threat type",
                expectedThreatType,
                backToSafetyWebViewClient.getOnSafeBrowsingHitThreatType());
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnSafeBrowsingMalwareCode. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    @Ignore("b/40196091")
    public void testOnSafeBrowsingMalwareCode() throws Throwable {
        testOnSafeBrowsingCode(
                TEST_SAFE_BROWSING_MALWARE_URL, WebViewClient.SAFE_BROWSING_THREAT_MALWARE);
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnSafeBrowsingPhishingCode. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    @Ignore("b/40196091")
    public void testOnSafeBrowsingPhishingCode() throws Throwable {
        testOnSafeBrowsingCode(
                TEST_SAFE_BROWSING_PHISHING_URL, WebViewClient.SAFE_BROWSING_THREAT_PHISHING);
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnSafeBrowsingUnwantedSoftwareCode. Modifications
     * to this test should be reflected in that test as necessary. See
     * http://go/modifying-webview-cts.
     */
    @Test
    @Ignore("b/40196091")
    public void testOnSafeBrowsingUnwantedSoftwareCode() throws Throwable {
        testOnSafeBrowsingCode(
                TEST_SAFE_BROWSING_UNWANTED_SOFTWARE_URL,
                WebViewClient.SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE);
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnSafeBrowsingBillingCode. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    @Ignore("b/40196091")
    public void testOnSafeBrowsingBillingCode() throws Throwable {
        testOnSafeBrowsingCode(
                TEST_SAFE_BROWSING_BILLING_URL, WebViewClient.SAFE_BROWSING_THREAT_BILLING);
    }

    private void requireLoadedPage() throws Throwable {
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
    }

    /**
     * This should remain functionally equivalent to
     * androidx.webkit.WebViewClientCompatTest#testOnPageCommitVisibleCalled. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnPageCommitVisibleCalled() throws Exception {
        // Check that the onPageCommitVisible callback is called
        // correctly.
        final SettableFuture<String> pageCommitVisibleFuture = SettableFuture.create();
        mOnUiThread.setWebViewClient(
                new WebViewClient() {
                    public void onPageCommitVisible(WebView view, String url) {
                        pageCommitVisibleFuture.set(url);
                    }
                });

        final String url = "about:blank";
        mOnUiThread.loadUrl(url);
        assertEquals(url, WebkitUtils.waitForFuture(pageCommitVisibleFuture));
    }

    private static class ShouldOverrideUrlLoadingRequest {
        final String mUrl;
        final WebResourceRequest mRequest;

        ShouldOverrideUrlLoadingRequest(String url, WebResourceRequest request) {
            mUrl = url;
            mRequest = request;
        }
    }

    private static class LoginRequest {
        final String mRealm;
        final String mAccount;
        final String mArgs;

        LoginRequest(String realm, String account, String args) {
            mRealm = realm;
            mAccount = account;
            mArgs = args;
        }
    }

    private class MockWebViewClient extends WaitForLoadedClient {
        private final BlockingQueue<Integer> mOnReceivedErrorQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<WebResourceError> mOnReceivedResourceErrorQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<WebResourceResponse> mOnReceivedHttpErrorQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<ShouldOverrideUrlLoadingRequest>
                mShouldOverrideUrlLoadingQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnPageFinishedQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnPageStartQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnLoadResourceQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<LoginRequest> mOnLoginRequestQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mDoUpdateVisitedHistoryQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnReceivedHttpAuthRequestQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnUnhandledKeyEventQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnScaleChangedQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnFormResubmissionQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<RenderProcessGoneDetail> mOnRenderProcessGoneQueue =
                new LinkedBlockingQueue<>();

        MockWebViewClient() {
            super(mOnUiThread);
        }

        boolean hasOnPageStartedCalled() {
            return !mOnPageStartQueue.isEmpty();
        }

        Boolean waitForOnPageStart() {
            return WebkitUtils.waitForNextQueueElement(mOnPageStartQueue);
        }

        boolean hasOnPageFinishedCalled() {
            return !mOnPageFinishedQueue.isEmpty();
        }

        Boolean waitForOnPageFinished() {
            return WebkitUtils.waitForNextQueueElement(mOnPageFinishedQueue);
        }

        boolean hasOnLoadResourceCalled() {
            return !mOnLoadResourceQueue.isEmpty();
        }

        Boolean waitForOnLoadResource() {
            return WebkitUtils.waitForNextQueueElement(mOnLoadResourceQueue);
        }

        boolean hasOnReceivedError() {
            return !mOnReceivedErrorQueue.isEmpty();
        }

        Integer waitForOnReceivedError() {
            return WebkitUtils.waitForNextQueueElement(mOnReceivedErrorQueue);
        }

        boolean hasOnReceivedLoginRequest() {
            return !mOnLoginRequestQueue.isEmpty();
        }

        LoginRequest waitForLoginRequest() {
            return WebkitUtils.waitForNextQueueElement(mOnLoginRequestQueue);
        }

        boolean hasOnReceivedResourceError() {
            return !mOnReceivedResourceErrorQueue.isEmpty();
        }

        WebResourceError waitForOnReceivedResourceError() {
            return WebkitUtils.waitForNextQueueElement(mOnReceivedResourceErrorQueue);
        }

        boolean hasOnReceivedHttpError() {
            return !mOnReceivedHttpErrorQueue.isEmpty();
        }

        WebResourceResponse waitForOnReceivedHttpError() {
            return WebkitUtils.waitForNextQueueElement(mOnReceivedHttpErrorQueue);
        }

        boolean hasOnFormResubmissionCalled() {
            return !mOnFormResubmissionQueue.isEmpty();
        }

        Boolean waitForOnFormResubmission() {
            return WebkitUtils.waitForNextQueueElement(mOnFormResubmissionQueue);
        }

        boolean hasDoUpdateVisitedHistoryCalled() {
            return !mDoUpdateVisitedHistoryQueue.isEmpty();
        }

        Boolean waitForDoUpdateVisitedHistory() {
            return WebkitUtils.waitForNextQueueElement(mDoUpdateVisitedHistoryQueue);
        }

        boolean hasOnReceivedHttpAuthRequestCalled() {
            return !mOnReceivedHttpAuthRequestQueue.isEmpty();
        }

        Boolean waitForOnReceivedHttpAuthRequest() {
            return WebkitUtils.waitForNextQueueElement(mOnReceivedHttpAuthRequestQueue);
        }

        boolean hasOnUnhandledKeyEventCalled() {
            return !mOnUnhandledKeyEventQueue.isEmpty();
        }

        Boolean waitForOnUnhandledKeyEvent() {
            return WebkitUtils.waitForNextQueueElement(mOnUnhandledKeyEventQueue);
        }

        boolean hasOnScaleChangedCalled() {
            return !mOnScaleChangedQueue.isEmpty();
        }

        Boolean waitForOnScaleChanged() {
            return WebkitUtils.waitForNextQueueElement(mOnScaleChangedQueue);
        }

        ShouldOverrideUrlLoadingRequest waitForShouldOverrideUrlLoading() {
            return WebkitUtils.waitForNextQueueElement(mShouldOverrideUrlLoadingQueue);
        }

        boolean isShouldOverrideUrlLoadingQueueEmpty() {
            return mShouldOverrideUrlLoadingQueue.isEmpty();
        }

        RenderProcessGoneDetail waitForOnRenderProcessGone() {
            return WebkitUtils.waitForNextQueueElement(mOnRenderProcessGoneQueue);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mOnPageStartQueue.add(true);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // TODO(ntfschr): propagate these exceptions to the instrumentation thread.
            assertTrue(
                    "Expected onPageStarted to be called before onPageFinished",
                    hasOnPageStartedCalled());
            assertTrue(
                    "Expected onLoadResource or onReceivedError to be called before onPageFinished",
                    hasOnLoadResourceCalled() || hasOnReceivedError());
            mOnPageFinishedQueue.add(true);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            mOnLoadResourceQueue.add(true);
        }

        @Override
        public void onReceivedError(
                WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            mOnReceivedErrorQueue.add(errorCode);
        }

        @Override
        public void onReceivedError(
                WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            mOnReceivedResourceErrorQueue.add(error);
        }

        @Override
        public void onReceivedHttpError(
                WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            mOnReceivedHttpErrorQueue.add(errorResponse);
        }

        @Override
        public void onReceivedLoginRequest(
                WebView view, String realm, String account, String args) {
            super.onReceivedLoginRequest(view, realm, account, args);
            mOnLoginRequestQueue.add(new LoginRequest(realm, account, args));
        }

        @Override
        public void onFormResubmission(WebView view, Message dontResend, Message resend) {
            mOnFormResubmissionQueue.add(true);
            dontResend.sendToTarget();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);
            mDoUpdateVisitedHistoryQueue.add(true);
        }

        @Override
        public void onReceivedHttpAuthRequest(
                WebView view, HttpAuthHandler handler, String host, String realm) {
            super.onReceivedHttpAuthRequest(view, handler, host, realm);
            mOnReceivedHttpAuthRequestQueue.add(true);
        }

        @Override
        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
            super.onUnhandledKeyEvent(view, event);
            mOnUnhandledKeyEventQueue.add(true);
        }

        @Override
        public void onScaleChanged(WebView view, float oldScale, float newScale) {
            super.onScaleChanged(view, oldScale, newScale);
            mOnScaleChangedQueue.add(true);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            mShouldOverrideUrlLoadingQueue.add(new ShouldOverrideUrlLoadingRequest(url, null));
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            mShouldOverrideUrlLoadingQueue.add(
                    new ShouldOverrideUrlLoadingRequest(request.getUrl().toString(), request));
            return false;
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            mOnRenderProcessGoneQueue.add(detail);
            return true;
        }
    }

    private class SafeBrowsingBackToSafetyClient extends MockWebViewClient {
        private final SettableFuture<WebResourceRequest> mOnSafeBrowsingHitRequestFuture =
                SettableFuture.create();
        private volatile int mOnSafeBrowsingHitThreatType;

        WebResourceRequest getOnSafeBrowsingHitRequest() {
            return WebkitUtils.waitForFuture(mOnSafeBrowsingHitRequestFuture);
        }

        int getOnSafeBrowsingHitThreatType() {
            return mOnSafeBrowsingHitThreatType;
        }

        @Override
        public void onSafeBrowsingHit(
                WebView view,
                WebResourceRequest request,
                int threatType,
                SafeBrowsingResponse response) {
            // Immediately go back to safety to return the network error code
            mOnSafeBrowsingHitRequestFuture.set(request);
            mOnSafeBrowsingHitThreatType = threatType;
            response.backToSafety(/* report */ true);
        }
    }

    private class SafeBrowsingProceedClient extends MockWebViewClient {
        private final SettableFuture<WebResourceRequest> mOnSafeBrowsingHitRequestFuture =
                SettableFuture.create();

        WebResourceRequest getOnSafeBrowsingHitRequest() {
            return WebkitUtils.waitForFuture(mOnSafeBrowsingHitRequestFuture);
        }

        @Override
        public void onSafeBrowsingHit(
                WebView view,
                WebResourceRequest request,
                int threatType,
                SafeBrowsingResponse response) {
            // Proceed through Safe Browsing warnings
            mOnSafeBrowsingHitRequestFuture.set(request);
            response.proceed(/* report */ true);
        }
    }
}
