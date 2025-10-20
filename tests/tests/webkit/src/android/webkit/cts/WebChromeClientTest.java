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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Base64;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.ConsoleMessage;
import android.webkit.Flags;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebIconDatabase;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.cts.WebViewSyncLoader.WaitForLoadedClient;
import android.webkit.cts.WebViewSyncLoader.WaitForProgressClient;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebChromeClientTest extends SharedWebViewTest {
    @Rule
    // Passing null because this code is reused in the SDK sandbox test, where it's run as an SDK
    // instead of using instrumentation and so cannot get a UiAutomation.
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(null);

    private static final String JAVASCRIPT_UNLOAD = "javascript unload";
    private static final String LISTENER_ADDED = "listener added";
    private static final String TOUCH_RECEIVED = "touch received";

    @Rule
    public ActivityScenarioRule<WebViewCtsActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(WebViewCtsActivity.class);

    private SharedSdkWebServer mWebServer;
    private WebIconDatabase mIconDb;
    private WebViewOnUiThread mOnUiThread;
    private boolean mBlockWindowCreationSync;
    private boolean mBlockWindowCreationAsync;

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
        }
        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        if (mWebServer != null) {
            mWebServer.shutdown();
        }
        if (mIconDb != null) {
            mIconDb.removeAllIcons();
            mIconDb.close();
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
        return environment;
    }


    @Test
    public void testOnProgressChanged() {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        assertFalse(webChromeClient.hasOnProgressChanged());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertNotNull(webChromeClient.waitForOnProgressChanged());
    }

    @Test
    public void testOnReceivedTitle() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        assertFalse(webChromeClient.hasOnReceivedTitle());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, webChromeClient.waitForOnReceivedTitle());
    }

    @Test
    public void testOnReceivedIcon() throws Throwable {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        WebkitUtils.onMainThreadSync(() -> {
            // getInstance must run on the UI thread
            mIconDb = WebIconDatabase.getInstance();
            String dbPath = getTestEnvironment().getContext().getFilesDir().toString() + "/icons";
            mIconDb.open(dbPath);
        });
        getTestEnvironment().waitForIdleSync();
        Thread.sleep(100); // Wait for open to be received on the icon db thread.

        assertFalse(webChromeClient.hasOnReceivedIcon());
        assertNull(mOnUiThread.getFavicon());

        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertNotNull(webChromeClient.waitForOnReceivedIcon());
        assertNotNull(mOnUiThread.getFavicon());
    }

    public void runWindowTest(boolean expectWindowClose) throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        assertFalse(webChromeClient.hasOnCreateWindow());

        // Load a page that opens a child window and sets a timeout after which the child
        // will be closed.
        mOnUiThread.loadUrlAndWaitForCompletion(mWebServer.
                getAssetUrl(TestHtmlConstants.JS_WINDOW_URL));
        assertTrue(webChromeClient.waitForOnCreateWindow());

        if (expectWindowClose) {
            assertTrue(webChromeClient.waitForOnCloseWindow());
        }
    }
    @Test
    public void testWindows() throws Exception {
        runWindowTest(true);
    }

    @Test
    public void testBlockWindowsSync() throws Exception {
        mBlockWindowCreationSync = true;
        runWindowTest(false);
    }

    @Test
    public void testBlockWindowsAsync() throws Exception {
        mBlockWindowCreationAsync = true;
        runWindowTest(false);
    }

    // Note that test is still a little flaky. See b/119468441.
    @Test
    public void testOnJsBeforeUnloadIsCalled() throws Exception {
        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        mOnUiThread.loadUrlAndWaitForCompletion(
            mWebServer.getAssetUrl(TestHtmlConstants.JS_UNLOAD_URL));

        assertEquals(JAVASCRIPT_UNLOAD, webChromeClient.waitForOnReceivedTitle());
        assertEquals(LISTENER_ADDED, webChromeClient.waitForOnReceivedTitle());
        // Send a user gesture, required for unload to execute since WebView version 60.
        tapWebView();
        assertEquals(TOUCH_RECEIVED, webChromeClient.waitForOnReceivedTitle());

        // unload should trigger when we try to navigate away
        mOnUiThread.loadUrlAndWaitForCompletion(
            mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));

        assertNotNull(webChromeClient.waitForOnJsBeforeUnload());
    }

    @Test
    public void testOnJsAlert() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        assertFalse(webChromeClient.hasOnJsAlert());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.JS_ALERT_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("testOnJsAlert", webChromeClient.waitForOnJsAlert());
    }

    @Test
    public void testOnJsConfirm() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        assertFalse(webChromeClient.hasOnJsConfirm());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.JS_CONFIRM_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("testOnJsConfirm", webChromeClient.waitForOnJsConfirm());
    }

    @Test
    public void testOnJsPrompt() throws Exception {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);

        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        assertFalse(webChromeClient.hasOnJsPrompt());

        final String promptResult = "CTS";
        webChromeClient.setPromptResult(promptResult);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.JS_PROMPT_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        assertEquals("testOnJsPrompt", webChromeClient.waitForOnJsPrompt());
        // the result returned by the client gets set as the page title
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals(promptResult);
            }
        }.run();
    }

    @Test
    public void testOnConsoleMessage() throws Exception {
        int numConsoleMessages = 4;
        final BlockingQueue<ConsoleMessage> consoleMessageQueue =
                new ArrayBlockingQueue<>(numConsoleMessages);
        final MockWebChromeClient webChromeClient = new MockWebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                consoleMessageQueue.add(message);
                // return false for default handling; i.e. printing the message.
                return false;
            }
        };
        mOnUiThread.setWebChromeClient(webChromeClient);

        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        // Note: we assert line numbers, which are relative to the line in the HTML file. So, "\n"
        // is significant in this test, and make sure to update consoleLineNumberOffset when
        // editing the HTML.
        final int consoleLineNumberOffset = 3;
        final String unencodedHtml = "<html>\n"
                + "<script>\n"
                + "  console.log('message0');\n"
                + "  console.warn('message1');\n"
                + "  console.error('message2');\n"
                + "  console.info('message3');\n"
                + "</script>\n"
                + "</html>\n";
        final String mimeType = null;
        final String encoding = "base64";
        String encodedHtml = Base64.encodeToString(unencodedHtml.getBytes(), Base64.NO_PADDING);
        mOnUiThread.loadDataAndWaitForCompletion(encodedHtml, mimeType, encoding);

        // Expected message levels correspond to the order of the console messages defined above.
        ConsoleMessage.MessageLevel[] expectedMessageLevels = {
            ConsoleMessage.MessageLevel.LOG,
            ConsoleMessage.MessageLevel.WARNING,
            ConsoleMessage.MessageLevel.ERROR,
            ConsoleMessage.MessageLevel.LOG,
        };
        for (int k = 0; k < numConsoleMessages; k++) {
            final ConsoleMessage consoleMessage =
                    WebkitUtils.waitForNextQueueElement(consoleMessageQueue);
            final ConsoleMessage.MessageLevel expectedMessageLevel = expectedMessageLevels[k];
            assertEquals("message " + k + " had wrong level",
                    expectedMessageLevel,
                    consoleMessage.messageLevel());
            final String expectedMessage = "message" + k;
            assertEquals("message " + k + " had wrong message",
                    expectedMessage,
                    consoleMessage.message());
            final int expectedLineNumber = k + consoleLineNumberOffset;
            assertEquals(
                    "message " + k + " had wrong line number",
                    expectedLineNumber,
                    consoleMessage.lineNumber());
        }
    }

    @Test
    public void testOnShowFileChooserInputFile() {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);
        final SettableFuture<String> pageCommitVisibleFuture = SettableFuture.create();
        mOnUiThread.setWebViewClient(
                new WaitForLoadedClient(mOnUiThread) {
                    public void onPageCommitVisible(WebView view, String url) {
                        pageCommitVisibleFuture.set(url);
                    }
                });

        assertFalse(webChromeClient.hasOnShowFileChooser());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.INPUT_FILE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        WebkitUtils.waitForFuture(pageCommitVisibleFuture);
        tapWebView();

        FileChooserParams params = webChromeClient.waitForOnShowFileChooser();
        assertNotNull(params);
        assertEquals(params.getMode(), FileChooserParams.MODE_OPEN);
        if (Flags.fileSystemAccess()) {
            assertEquals(params.getPermissionMode(), FileChooserParams.PERMISSION_MODE_READ);
        }
    }

    @Test
    public void testOnShowFileChooserInputFileMultiple() {
        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);
        final SettableFuture<String> pageCommitVisibleFuture = SettableFuture.create();
        mOnUiThread.setWebViewClient(
                new WaitForLoadedClient(mOnUiThread) {
                    public void onPageCommitVisible(WebView view, String url) {
                        pageCommitVisibleFuture.set(url);
                    }
                });

        assertFalse(webChromeClient.hasOnShowFileChooser());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.INPUT_FILE_MULTIPLE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        WebkitUtils.waitForFuture(pageCommitVisibleFuture);
        tapWebView();

        FileChooserParams params = webChromeClient.waitForOnShowFileChooser();
        assertNotNull(params);
        assertEquals(params.getMode(), FileChooserParams.MODE_OPEN_MULTIPLE);
        if (Flags.fileSystemAccess()) {
            assertEquals(params.getPermissionMode(), FileChooserParams.PERMISSION_MODE_READ);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FILE_SYSTEM_ACCESS)
    public void testOnShowFileChooserOpenReadWrite() {
        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);

        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);
        final SettableFuture<String> pageCommitVisibleFuture = SettableFuture.create();
        mOnUiThread.setWebViewClient(
                new WaitForLoadedClient(mOnUiThread) {
                    public void onPageCommitVisible(WebView view, String url) {
                        pageCommitVisibleFuture.set(url);
                    }
                });

        assertFalse(webChromeClient.hasOnShowFileChooser());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.SHOW_OPEN_FILE_PICKER_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        WebkitUtils.waitForFuture(pageCommitVisibleFuture);
        tapWebView();

        FileChooserParams params = webChromeClient.waitForOnShowFileChooser();
        assertNotNull(params);
        assertEquals(params.getMode(), FileChooserParams.MODE_OPEN);
        assertEquals(params.getPermissionMode(), FileChooserParams.PERMISSION_MODE_READ_WRITE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FILE_SYSTEM_ACCESS)
    public void testOnShowFileChooserDirectory() {
        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);

        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);
        final SettableFuture<String> pageCommitVisibleFuture = SettableFuture.create();
        mOnUiThread.setWebViewClient(
                new WaitForLoadedClient(mOnUiThread) {
                    public void onPageCommitVisible(WebView view, String url) {
                        pageCommitVisibleFuture.set(url);
                    }
                });

        assertFalse(webChromeClient.hasOnShowFileChooser());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.SHOW_DIRECTORY_PICKER_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        WebkitUtils.waitForFuture(pageCommitVisibleFuture);
        tapWebView();

        FileChooserParams params = webChromeClient.waitForOnShowFileChooser();
        assertNotNull(params);
        assertEquals(params.getMode(), FileChooserParams.MODE_OPEN_FOLDER);
        assertEquals(params.getPermissionMode(), FileChooserParams.PERMISSION_MODE_READ_WRITE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FILE_SYSTEM_ACCESS)
    public void testOnShowFileChooserSave() {
        final WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);

        final MockWebChromeClient webChromeClient = new MockWebChromeClient();
        mOnUiThread.setWebChromeClient(webChromeClient);
        final SettableFuture<String> pageCommitVisibleFuture = SettableFuture.create();
        mOnUiThread.setWebViewClient(
                new WaitForLoadedClient(mOnUiThread) {
                    public void onPageCommitVisible(WebView view, String url) {
                        pageCommitVisibleFuture.set(url);
                    }
                });

        assertFalse(webChromeClient.hasOnShowFileChooser());
        String url = mWebServer.getAssetUrl(TestHtmlConstants.SHOW_SAVE_FILE_PICKER_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        WebkitUtils.waitForFuture(pageCommitVisibleFuture);
        tapWebView();

        FileChooserParams params = webChromeClient.waitForOnShowFileChooser();
        assertNotNull(params);
        assertEquals(params.getMode(), FileChooserParams.MODE_SAVE);
        assertEquals(params.getPermissionMode(), FileChooserParams.PERMISSION_MODE_READ_WRITE);
    }

    /** Taps in the center of a webview. */
    private void tapWebView() {
        int[] location = mOnUiThread.getLocationOnScreen();
        int middleX = location[0] + mOnUiThread.getWebView().getWidth() / 2;
        int middleY = location[1] + mOnUiThread.getWebView().getHeight() / 2;
        getTestEnvironment().sendTapSync(middleX, middleY);

        // Wait for the system to process all events in the queue
        getTestEnvironment().waitForIdleSync();
    }

    private class MockWebChromeClient extends WaitForProgressClient {
        private WebView mChildWebView;

        private String mPromptResult;

        private final BlockingQueue<Integer> mOnProgressChangedQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> mOnReceivedTitleQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Bitmap> mOnReceivedIconQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnCreateWindowQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> mOnCloseWindowQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> mOnJsAlertQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> mOnJsConfirmQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> mOnJsPromptQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<FileChooserParams> mOnShowFileChooserQueue =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<String> mOnJsBeforeUnloadQueue = new LinkedBlockingQueue<>();

        MockWebChromeClient() {
            super(mOnUiThread);
        }

        void setPromptResult(String promptResult) {
            mPromptResult = promptResult;
        }

        boolean hasOnProgressChanged() {
            return !mOnProgressChangedQueue.isEmpty();
        }

        Integer waitForOnProgressChanged() {
            return WebkitUtils.waitForNextQueueElement(mOnProgressChangedQueue);
        }

        boolean hasOnReceivedTitle() {
            return !mOnReceivedTitleQueue.isEmpty();
        }

        String waitForOnReceivedTitle() {
            return WebkitUtils.waitForNextQueueElement(mOnReceivedTitleQueue);
        }

        boolean hasOnJsAlert() {
            return !mOnJsAlertQueue.isEmpty();
        }

        String waitForOnJsAlert() {
            return WebkitUtils.waitForNextQueueElement(mOnJsAlertQueue);
        }

        boolean hasOnJsConfirm() {
            return !mOnJsConfirmQueue.isEmpty();
        }

        String waitForOnJsConfirm() {
            return WebkitUtils.waitForNextQueueElement(mOnJsConfirmQueue);
        }

        boolean hasOnJsPrompt() {
            return !mOnJsPromptQueue.isEmpty();
        }

        String waitForOnJsPrompt() {
            return WebkitUtils.waitForNextQueueElement(mOnJsPromptQueue);
        }

        boolean hasOnCreateWindow() {
            return !mOnCreateWindowQueue.isEmpty();
        }

        Boolean waitForOnCreateWindow() {
            return WebkitUtils.waitForNextQueueElement(mOnCreateWindowQueue);
        }

        Boolean waitForOnCloseWindow() {
            return WebkitUtils.waitForNextQueueElement(mOnCloseWindowQueue);
        }

        boolean hasOnReceivedIcon() {
            return !mOnReceivedIconQueue.isEmpty();
        }

        Bitmap waitForOnReceivedIcon() {
            return WebkitUtils.waitForNextQueueElement(mOnReceivedIconQueue);
        }

        boolean hasOnShowFileChooser() {
            return !mOnShowFileChooserQueue.isEmpty();
        }

        FileChooserParams waitForOnShowFileChooser() {
            return WebkitUtils.waitForNextQueueElement(mOnShowFileChooserQueue);
        }

        String waitForOnJsBeforeUnload() {
            return WebkitUtils.waitForNextQueueElement(mOnJsBeforeUnloadQueue);
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            mOnProgressChangedQueue.add(newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            mOnReceivedTitleQueue.add(title);
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            super.onJsAlert(view, url, message, result);
            mOnJsAlertQueue.add(message);
            result.confirm();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            super.onJsConfirm(view, url, message, result);
            mOnJsConfirmQueue.add(message);
            result.confirm();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message,
                String defaultValue, JsPromptResult result) {
            super.onJsPrompt(view, url, message, defaultValue, result);
            mOnJsPromptQueue.add(message);
            result.confirm(mPromptResult);
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            super.onJsBeforeUnload(view, url, message, result);
            mOnJsBeforeUnloadQueue.add(message);
            result.confirm();
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            mOnCloseWindowQueue.add(true);
            super.onCloseWindow(window);

            if (mChildWebView != null) {
                ViewParent parent =  mChildWebView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(mChildWebView);
                }
                mChildWebView.destroy();
            }

        }

        @Override
        public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture,
                Message resultMsg) {
            mOnCreateWindowQueue.add(true);
            if (mBlockWindowCreationSync) {
                return false;
            }
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            if (mBlockWindowCreationAsync) {
                transport.setWebView(null);
            } else {
                mChildWebView = new WebView(getTestEnvironment().getContext());
                final WebSettings settings = mChildWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                mChildWebView.setWebChromeClient(this);
                transport.setWebView(mChildWebView);
                getTestEnvironment().addContentView(mChildWebView, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            mOnReceivedIconQueue.add(icon);
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams) {
            mOnShowFileChooserQueue.add(fileChooserParams);
            return false;
        }
    }
}
