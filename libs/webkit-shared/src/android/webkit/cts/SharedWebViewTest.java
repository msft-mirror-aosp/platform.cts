/*
 * Copyright (C) 2022 The Android Open Source Project
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

/**
 * Extending this class indicates that some of the test methods are compatible with being executed
 * in both SDK Runtime and Activity based environments.
 *
 * <p>If a test class extends SharedWebViewTest, it will be expected to provide its own {@link
 * WebViewTestEnvironment} implementation to be used only during the normal (Activity based) test
 * run. The SdkSandbox variant of the test class will use the standard WebViewTestEnvironment
 * provided by {@link WebViewSandboxTestSdk}.
 *
 * <p>A SharedWebViewTest subclass can define @Before or @After steps. These will also run in the
 * SdkSandbox variant before and after each test case, so the implementation of these steps needs to
 * be compatible with both runtimes.
 */
public abstract class SharedWebViewTest {
    public static final String WEB_VIEW_TEST_CLASS_NAME = "WEB_VIEW_TEST_CLASS_NAME";

    private WebViewTestEnvironment mEnvironment;

    /**
     * Subclasses must override this to provide a suitable test environment object to later be used
     * inside the test methods. Note that this method is only called in the "normal" CTS test
     * module, not in the Sandbox module. The Sandbox module sets its own test environment
     * separately.
     *
     * @return the WebViewTestEnvironment.
     */
    protected abstract WebViewTestEnvironment createTestEnvironment();

    /**
     * Provide a WebViewTestEnvironment to use for a test run. This can be invoked from the test
     * runner.
     */
    public void setTestEnvironment(WebViewTestEnvironment sharedWebViewTestEnvironment) {
        mEnvironment = sharedWebViewTestEnvironment;
    }

    /**
     * Retrieves the appropriate test environment during the test run. This can be called to access
     * common resources, such as the WebView instance or the Context, which are appropriate for the
     * current test invocation (e.g., whether we are in the "normal" CTS test suite or the SDK
     * sandbox suite).
     *
     * @return the WebViewTestEnvironment.
     */
    public WebViewTestEnvironment getTestEnvironment() {
        if (mEnvironment == null) {
            mEnvironment = createTestEnvironment();
        }
        return mEnvironment;
    }
}
