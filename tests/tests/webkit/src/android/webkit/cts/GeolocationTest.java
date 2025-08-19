/*
 * Copyright (C) 2012 The Android Open Source Project
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


import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.cts.WebViewSyncLoader.WaitForLoadedClient;
import android.webkit.cts.WebViewSyncLoader.WaitForProgressClient;

import androidx.annotation.IntDef;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.LocationUtils;
import com.android.compatibility.common.util.NullWebViewUtils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@AppModeFull(reason = "Instant apps do not have access to location information")
@MediumTest
@RunWith(AndroidJUnit4.class)
public class GeolocationTest {

    // TODO Write additional tests to cover:
    // - test that the errors are correct
    // - test that use of gps and network location is correct

    // The URLs does not matter since the tests will intercept the load, but it has to be a real
    // url, and different domains.
    private static final String URL_1 = "https://www.example.com";
    private static final String URL_2 = "https://www.example.org";
    private static final String URL_INSECURE = "http://www.example.org";

    private static final String JS_INTERFACE_NAME = "Android";
    private static final int LOCATION_THREAD_UPDATE_WAIT_MS = 250;

    // static HTML page always injected instead of the url loaded
    private static final String RAW_HTML =
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "  <head>\n" +
            "    <title>Geolocation</title>\n" +
            "    <script>\n" +
            "      function gotPos(position) {\n" +
            "        " + JS_INTERFACE_NAME + ".gotLocation();\n" +
            "      }\n" +
            "      function initiate_getCurrentPosition() {\n" +
            "        navigator.geolocation.getCurrentPosition(\n" +
            "            gotPos,\n" +
            "            handle_errors,\n" +
            "            {maximumAge:1000});\n" +
            "      }\n" +
            "      function handle_errors(error) {\n" +
            "        switch(error.code) {\n" +
            "          case error.PERMISSION_DENIED:\n" +
            "            " + JS_INTERFACE_NAME + ".errorDenied(); break;\n" +
            "          case error.POSITION_UNAVAILABLE:\n" +
            "            " + JS_INTERFACE_NAME + ".errorUnavailable(); break;\n" +
            "          case error.TIMEOUT:\n" +
            "            " + JS_INTERFACE_NAME + ".errorTimeout(); break;\n" +
            "          default: break;\n" +
            "        }\n" +
            "      }\n" +
            "    </script>\n" +
            "  </head>\n" +
            "  <body onload=\"initiate_getCurrentPosition();\">\n" +
            "  </body>\n" +
            "</html>";

    @Rule
    public ActivityScenarioRule<WebViewCtsActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(WebViewCtsActivity.class);

    private JavascriptStatusReceiver mJavascriptStatusReceiver;
    private LocationManager mLocationManager;
    private WebViewOnUiThread mOnUiThread;
    private Thread mLocationUpdateThread;
    private volatile boolean mLocationUpdateThreadExitRequested;
    private List<String> mProviders;

    // Both this test and WebViewOnUiThread need to override some of the methods on WebViewClient,
    // so this test sublclasses the WebViewClient from WebViewOnUiThread
    private static class InterceptClient extends WaitForLoadedClient {

        public InterceptClient(WebViewOnUiThread webViewOnUiThread) throws Exception {
            super(webViewOnUiThread);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            // Intercept all page loads with the same geolocation enabled page
            try {
                return new WebResourceResponse("text/html", "utf-8",
                    new ByteArrayInputStream(RAW_HTML.getBytes("UTF-8")));
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            WebViewCtsActivity webViewCtsActivity = (WebViewCtsActivity) activity;
            WebView webview = webViewCtsActivity.getWebView();
            if (webview != null) {
                mOnUiThread = new WebViewOnUiThread(webview);
            }
        });
        LocationUtils.registerMockLocationProvider(
                InstrumentationRegistry.getInstrumentation(), true);

        if (mOnUiThread != null) {
            // Set up a WebView with JavaScript and Geolocation enabled
            final String GEO_DIR = "geo_test";
            mOnUiThread.getSettings().setJavaScriptEnabled(true);
            mOnUiThread.getSettings().setGeolocationEnabled(true);
            mOnUiThread.getSettings().setGeolocationDatabasePath(
                    InstrumentationRegistry.getInstrumentation().getContext().getDir(GEO_DIR, 0)
                    .getPath());

            // Add a JsInterface to report back to the test when a location is received
            mJavascriptStatusReceiver = new JavascriptStatusReceiver();
            mOnUiThread.addJavascriptInterface(mJavascriptStatusReceiver, JS_INTERFACE_NAME);

            // Always intercept all loads with the same geolocation test page
            mOnUiThread.setWebViewClient(new InterceptClient(mOnUiThread));
            // Clear all permissions before each test
            GeolocationPermissions.getInstance().clearAll();
            // Cache this mostly because the lookup is two lines of code
            mLocationManager = (LocationManager) InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(Context.LOCATION_SERVICE);
            // Add a test provider before each test to inject a location
            mProviders = mLocationManager.getAllProviders();
            for (String provider : mProviders) {
                // Can't mock passive provider.
                if (provider.equals(LocationManager.PASSIVE_PROVIDER)) {
                    mProviders.remove(provider);
                    break;
                }
            }
            if(mProviders.size() == 0)
            {
                addTestLocationProvider();
                mAddedTestLocationProvider = true;
            }
            mProviders.add(LocationManager.FUSED_PROVIDER);
            addTestProviders();
        }
    }

    @After
    public void tearDown() throws Exception {
        stopUpdateLocationThread();
        if (mProviders != null) {
            // Remove the test provider after each test
            for (String provider : mProviders) {
                try {
                    // Work around b/11446702 by clearing the test provider before removing it
                    mLocationManager.clearTestProviderEnabled(provider);
                    mLocationManager.removeTestProvider(provider);
                } catch (IllegalArgumentException e) {} // Not much to do about this
            }
            if(mAddedTestLocationProvider)
            {
                removeTestLocationProvider();
            }
        }
        LocationUtils.registerMockLocationProvider(
                InstrumentationRegistry.getInstrumentation(), false);

        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    private void addTestProviders() {
        Set<String> unavailableProviders = new HashSet<>();
        for (String providerName : mProviders) {
            LocationProvider provider = mLocationManager.getProvider(providerName);
            if (provider == null) {
                unavailableProviders.add(providerName);
                continue;
            }
            mLocationManager.addTestProvider(provider.getName(),
                    provider.requiresNetwork(), //requiresNetwork,
                    provider.requiresSatellite(), // requiresSatellite,
                    provider.requiresCell(),  // requiresCell,
                    provider.hasMonetaryCost(), // hasMonetaryCost,
                    provider.supportsAltitude(), // supportsAltitude,
                    provider.supportsSpeed(), // supportsSpeed,
                    provider.supportsBearing(), // supportsBearing,
                    provider.getPowerRequirement(), // powerRequirement
                    provider.getAccuracy()); // accuracy
            mLocationManager.setTestProviderEnabled(provider.getName(), true);
        }
        mProviders.removeAll(unavailableProviders);
    }

    private static final String TEST_PROVIDER_NAME = "location_provider_test";
    private boolean mAddedTestLocationProvider = false;

    private void addTestLocationProvider() {
        mLocationManager.addTestProvider(
                TEST_PROVIDER_NAME,
                true,  // requiresNetwork,
                false, // requiresSatellite,
                false, // requiresCell,
                false, // hasMonetaryCost,
                true,  // supportsAltitude,
                false, // supportsSpeed,
                true,  // supportsBearing,
                Criteria.POWER_MEDIUM,   // powerRequirement,
                Criteria.ACCURACY_FINE); // accuracy
        mLocationManager.setTestProviderEnabled(TEST_PROVIDER_NAME, true);
    }

    private void removeTestLocationProvider() {
        mLocationManager.clearTestProviderEnabled(TEST_PROVIDER_NAME);
        mLocationManager.removeTestProvider(TEST_PROVIDER_NAME);
    }

    private void startUpdateLocationThread() {
        // Only start the thread once
        if (mLocationUpdateThread == null) {
            mLocationUpdateThreadExitRequested = false;
            mLocationUpdateThread = new Thread() {
                @Override
                public void run() {
                    while (!mLocationUpdateThreadExitRequested) {
                        try {
                            Thread.sleep(LOCATION_THREAD_UPDATE_WAIT_MS);
                        } catch (Exception e) {
                            // Do nothing, an extra update is no problem
                        }
                        updateLocation();
                    }
                }
            };
            mLocationUpdateThread.start();
        }
    }

    private void stopUpdateLocationThread() {
        // Only stop the thread if it was started
        if (mLocationUpdateThread != null) {
            mLocationUpdateThreadExitRequested = true;
            try {
                mLocationUpdateThread.join();
            } catch (InterruptedException e) {
                // Do nothing
            }
            mLocationUpdateThread = null;
        }
    }

    // Update location with a fixed latitude and longtitude, sets the time to the current time.
    private void updateLocation() {
        for (int i = 0; i < mProviders.size(); i++) {
            Location location = new Location(mProviders.get(i));
            location.setLatitude(40);
            location.setLongitude(40);
            location.setAccuracy(1.0f);
            location.setTime(java.lang.System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            mLocationManager.setTestProviderLocation(mProviders.get(i), location);
        }
    }

    // Need to set the location just after loading the url. Setting it after each load instead of
    // using a maximum age.
    private void loadUrlAndUpdateLocation(String url) {
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        startUpdateLocationThread();
    }

    // WebChromeClient that accepts each location for one load. WebChromeClient is used in
    // WebViewOnUiThread to detect when the page is loaded, so subclassing the one used there.
    private static class TestSimpleGeolocationRequestWebChromeClient
                extends WaitForProgressClient {
        private final boolean mAccept;
        private final boolean mRetain;
        private final BlockingQueue<Boolean> mReceivedRequests = new LinkedBlockingQueue<Boolean>();

        public TestSimpleGeolocationRequestWebChromeClient(
                WebViewOnUiThread webViewOnUiThread, boolean accept, boolean retain) {
            super(webViewOnUiThread);
            this.mAccept = accept;
            this.mRetain = retain;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin, GeolocationPermissions.Callback callback) {
            mReceivedRequests.add(true);
            callback.invoke(origin, mAccept, mRetain);
        }

        boolean hasPrompt() {
            return !mReceivedRequests.isEmpty();
        }

        boolean waitForPrompt() {
            return WebkitUtils.waitForNextQueueElement(mReceivedRequests);
        }
    }

    // Test loading a page and accepting the domain for one load
    @Test
    public void testSimpleGeolocationRequestAcceptOnce() throws Exception {
        final TestSimpleGeolocationRequestWebChromeClient chromeClientAcceptOnce =
                new TestSimpleGeolocationRequestWebChromeClient(mOnUiThread, true, false);
        mOnUiThread.setWebChromeClient(chromeClientAcceptOnce);
        loadUrlAndUpdateLocation(URL_1);
        Assert.assertTrue("Geolocation prompt not called", chromeClientAcceptOnce.waitForPrompt());
        Assert.assertEquals(
                "JS didn't get position",
                JavascriptStatusReceiver.Outcome.GOT_LOCATION,
                mJavascriptStatusReceiver.waitForOutcome());
        // Load URL again, should receive callback again
        loadUrlAndUpdateLocation(URL_1);
        Assert.assertTrue("Geolocation prompt not called", chromeClientAcceptOnce.waitForPrompt());
        Assert.assertEquals(
                "JS didn't get position",
                JavascriptStatusReceiver.Outcome.GOT_LOCATION,
                mJavascriptStatusReceiver.waitForOutcome());
    }

    /**
     * Waits for a callback result and checks if the returned set of strings match the provided set
     * of strings, ignoring any potential trailing {@code "/"} characters.
     */
    private static class OriginCheck implements ValueCallback<Set<String>> {

        private final SettableFuture<Set<String>> mFuture = SettableFuture.create();
        private final Set<String> mExpectedValue;

        OriginCheck(Set<String> val) {
            mExpectedValue = val;
        }

        @Override
        public void onReceiveValue(Set<String> value) {
            mFuture.set(value);
        }

        void run() {
            Set<String> actual = WebkitUtils.waitForFuture(mFuture);
            Assert.assertEquals(mExpectedValue.size(), actual.size());
            boolean equal = true;
            for (String origin : actual) {
                equal &= expectsOrigin(origin);
                if (!equal) break;
            }
            if (!equal) {
                // Use an assertion to take advantage of pretty printing.
                Assert.assertEquals("Failed origin check", mExpectedValue, actual);
            }
        }

        private boolean expectsOrigin(String origin) {
            if (mExpectedValue.contains(origin)) {
                return true;
            }

            // Try removing or adding a "/"
            if (origin.endsWith("/")) {
                if (mExpectedValue.contains(origin.substring(0, origin.length() - 1))) {
                    return true;
                }
            } else {
                if (mExpectedValue.contains(origin + "/")) {
                    return true;
                }
            }
            return false;
        }
    }


    // Test loading a page and retaining the domain forever
    @Test
    public void testSimpleGeolocationRequestAcceptAlways() throws Exception {
        final TestSimpleGeolocationRequestWebChromeClient chromeClientAcceptAlways =
                new TestSimpleGeolocationRequestWebChromeClient(mOnUiThread, true, true);
        mOnUiThread.setWebChromeClient(chromeClientAcceptAlways);
        // Load url once, and the callback should accept the domain for all future loads
        loadUrlAndUpdateLocation(URL_1);
        Assert.assertTrue(
                "Geolocation prompt not called", chromeClientAcceptAlways.waitForPrompt());
        Assert.assertEquals(
                "JS didn't get position",
                JavascriptStatusReceiver.Outcome.GOT_LOCATION,
                mJavascriptStatusReceiver.waitForOutcome());
        mJavascriptStatusReceiver.clearState();
        // Load the same URL again
        loadUrlAndUpdateLocation(URL_1);
        Assert.assertEquals(
                "JS didn't get position",
                JavascriptStatusReceiver.Outcome.GOT_LOCATION,
                mJavascriptStatusReceiver.waitForOutcome());
        // JS already responded with an outcome, so we should not wait for the ChromeClient.
        Assert.assertFalse(
                "Prompt for geolocation permission should not be called the second time",
                chromeClientAcceptAlways.hasPrompt());
        // Check that the permission is in GeolocationPermissions
        SettableFuture<Boolean> allowedFuture = SettableFuture.create();
        GeolocationPermissions.getInstance().getAllowed(URL_1, allowedFuture::set);
        Assert.assertTrue(WebkitUtils.waitForFuture(allowedFuture));

        Set<String> acceptedOrigins = new TreeSet<String>();
        acceptedOrigins.add(URL_1);
        OriginCheck originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();

        // URL_2 should get a prompt
        loadUrlAndUpdateLocation(URL_2);
        // Checking the callback for geolocation permission prompt is called
        Assert.assertTrue(
                "Geolocation prompt not called", chromeClientAcceptAlways.waitForPrompt());
        Assert.assertEquals(
                "JS didn't get position",
                JavascriptStatusReceiver.Outcome.GOT_LOCATION,
                mJavascriptStatusReceiver.waitForOutcome());
        acceptedOrigins.add(URL_2);
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();
        // Remove a domain manually that was added by the callback
        GeolocationPermissions.getInstance().clear(URL_1);
        acceptedOrigins.remove(URL_1);
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();
    }

    // Test the GeolocationPermissions API
    @Test
    public void testGeolocationPermissions() {
        Set<String> acceptedOrigins = new TreeSet<String>();
        SettableFuture<Boolean> initialValueFuture = SettableFuture.create();
        GeolocationPermissions.getInstance().getAllowed(URL_2, initialValueFuture::set);
        Assert.assertFalse(WebkitUtils.waitForFuture(initialValueFuture));

        OriginCheck originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();

        // Remove a domain that has not been allowed
        GeolocationPermissions.getInstance().clear(URL_2);
        acceptedOrigins.remove(URL_2);
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();

        // Add a domain
        acceptedOrigins.add(URL_2);
        GeolocationPermissions.getInstance().allow(URL_2);
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();
        SettableFuture<Boolean> domainAllowedFuture = SettableFuture.create();
        GeolocationPermissions.getInstance().getAllowed(URL_2, domainAllowedFuture::set);
        Assert.assertTrue(WebkitUtils.waitForFuture(domainAllowedFuture));

        // Add a domain
        acceptedOrigins.add(URL_1);
        GeolocationPermissions.getInstance().allow(URL_1);
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();

        // Remove a domain that has been allowed
        GeolocationPermissions.getInstance().clear(URL_2);
        acceptedOrigins.remove(URL_2);
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();
        SettableFuture<Boolean> url2AllowedFuture = SettableFuture.create();
        GeolocationPermissions.getInstance().getAllowed(URL_2, url2AllowedFuture::set);
        Assert.assertFalse(WebkitUtils.waitForFuture(url2AllowedFuture));

        // Try to clear all domains
        GeolocationPermissions.getInstance().clearAll();
        acceptedOrigins.clear();
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();

        // Add a domain
        acceptedOrigins.add(URL_1);
        GeolocationPermissions.getInstance().allow(URL_1);
        originCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(originCheck);
        originCheck.run();
    }

    // Test loading pages and checks rejecting once and rejecting the domain forever
    @Test
    public void testSimpleGeolocationRequestReject() throws Exception {
        final TestSimpleGeolocationRequestWebChromeClient chromeClientRejectOnce =
                new TestSimpleGeolocationRequestWebChromeClient(mOnUiThread, false, false);
        mOnUiThread.setWebChromeClient(chromeClientRejectOnce);
        // Load url once, and the callback should reject it once
        mOnUiThread.loadUrlAndWaitForCompletion(URL_1);
        Assert.assertTrue("Geolocation prompt not called", chromeClientRejectOnce.waitForPrompt());
        Assert.assertEquals(
                "JS got position",
                JavascriptStatusReceiver.Outcome.DENIED,
                mJavascriptStatusReceiver.waitForOutcome());
        // Same result should happen on next run
        mOnUiThread.loadUrlAndWaitForCompletion(URL_1);

        Assert.assertTrue("Geolocation prompt not called", chromeClientRejectOnce.waitForPrompt());
        Assert.assertEquals(
                "JS got position",
                JavascriptStatusReceiver.Outcome.DENIED,
                mJavascriptStatusReceiver.waitForOutcome());

        // Try to reject forever
        final TestSimpleGeolocationRequestWebChromeClient chromeClientRejectAlways =
            new TestSimpleGeolocationRequestWebChromeClient(mOnUiThread, false, true);
        mOnUiThread.setWebChromeClient(chromeClientRejectAlways);
        mOnUiThread.loadUrlAndWaitForCompletion(URL_2);
        Assert.assertTrue(
                "Geolocation prompt not called", chromeClientRejectAlways.waitForPrompt());
        Assert.assertEquals(
                "JS got position",
                JavascriptStatusReceiver.Outcome.DENIED,
                mJavascriptStatusReceiver.waitForOutcome());

        // second load should now not get a prompt
        mOnUiThread.loadUrlAndWaitForCompletion(URL_2);
        Assert.assertEquals(
                "JS got position",
                JavascriptStatusReceiver.Outcome.DENIED,
                mJavascriptStatusReceiver.waitForOutcome());
        // JS responded, so check if we saw a prompt or not.
        Assert.assertFalse("Geolocation prompt was called", chromeClientRejectAlways.hasPrompt());

        // Test if it gets added to origins
        Set<String> acceptedOrigins = new TreeSet<String>();
        acceptedOrigins.add(URL_2);
        OriginCheck domainCheck = new OriginCheck(acceptedOrigins);
        GeolocationPermissions.getInstance().getOrigins(domainCheck);
        domainCheck.run();
        // And now check that getAllowed returns false
        SettableFuture<Boolean> url1AllowedFuture = SettableFuture.create();
        GeolocationPermissions.getInstance().getAllowed(URL_1, url1AllowedFuture::set);
        Assert.assertFalse(WebkitUtils.waitForFuture(url1AllowedFuture));
    }

    // Test deny geolocation on insecure origins
    @Test
    public void testGeolocationRequestDeniedOnInsecureOrigin() throws Exception {
        final TestSimpleGeolocationRequestWebChromeClient chromeClientAcceptAlways =
                new TestSimpleGeolocationRequestWebChromeClient(mOnUiThread, true, true);
        mOnUiThread.setWebChromeClient(chromeClientAcceptAlways);
        loadUrlAndUpdateLocation(URL_INSECURE);
        Assert.assertEquals(
                "JS got position",
                JavascriptStatusReceiver.Outcome.DENIED,
                mJavascriptStatusReceiver.waitForOutcome());
        Assert.assertFalse(
                "The geolocation permission prompt should not be called",
                chromeClientAcceptAlways.hasPrompt());
    }

    // Object added to the page via AddJavascriptInterface() that is used by the test Javascript to
    // notify back to Java when a location or error is received.
    public final static class JavascriptStatusReceiver {
        @IntDef({Outcome.GOT_LOCATION, Outcome.DENIED, Outcome.UNAVAILABLE, Outcome.TIMEOUT})
        @Retention(RetentionPolicy.SOURCE)
        @interface Outcome {
            int GOT_LOCATION = 1;
            int DENIED = 2;
            int UNAVAILABLE = 3;
            int TIMEOUT = 4;
        }

        private final BlockingQueue<Integer> mOutcomes = new LinkedBlockingQueue<Integer>();

        void clearState() {
            mOutcomes.clear();
        }

        @Outcome
        int waitForOutcome() {
            return WebkitUtils.waitForNextQueueElement(mOutcomes);
        }

        @JavascriptInterface
        public void errorDenied() {
            mOutcomes.add(Outcome.DENIED);
        }

        @JavascriptInterface
        public void errorUnavailable() {
            mOutcomes.add(Outcome.UNAVAILABLE);
        }

        @JavascriptInterface
        public void errorTimeout() {
            mOutcomes.add(Outcome.TIMEOUT);
        }

        @JavascriptInterface
        public void gotLocation() {
            mOutcomes.add(Outcome.GOT_LOCATION);
        }
    }
}
