/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.ad.cts;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.ad.TvAdManager;
import android.media.tv.ad.TvAdServiceInfo;
import android.media.tv.ad.TvAdView;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.tv.cts.R;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Test {@link android.media.tv.ad.TvAdService}
 */
@RunWith(AndroidJUnit4.class)
public class TvAdServiceTest {
    private static final long TIME_OUT_MS = 20000L;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvAdStubActivity> mActivityScenario;
    private TvAdStubActivity mActivity;
    private TvAdView mTvAdView;
    private TvView mTvView;
    private TvAdManager mManager;
    private TvInputManager mTvInputManager;
    private TvAdServiceInfo mStubInfo;
    private TvInputInfo mTvInputInfo;
    private StubTvAdService.StubSessionImpl mSession;
    private TvAdView.OnUnhandledInputEventListener mOnUnhandledInputEventListener;

    private final MockCallback mCallback = new MockCallback();
    private final MockTvAdServiceCallBack mMockTvAdServiceCallBack = new MockTvAdServiceCallBack();

    public static class MockCallback extends TvAdView.TvAdCallback {

        private void resetValues() {
        }

    }

    public static class MockTvAdServiceCallBack extends TvAdManager.TvAdServiceCallback {

        private void resetValues() {
        }

    }

    @Before
    public void setUp() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(mInstrumentation.getTargetContext(), TvAdStubActivity.class);

        // DO NOT use ActivityScenario.launch(Class), which can cause ActivityNotFoundException
        // related to BootstrapActivity.
        mActivityScenario = ActivityScenario.launch(intent);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(TIME_OUT_MS);

        assertNotNull("Failed to acquire activity reference.", mActivity);
        mTvAdView = findTvAdViewById(R.id.tvadview);
        assertNotNull("Failed to find TvAdView.", mTvAdView);
        mTvView = findTvViewById(R.id.tvad_tvview);
        assertNotNull("Failed to find TvView.", mTvView);

        mManager = (TvAdManager) mActivity.getSystemService(Context.TV_AD_SERVICE);
        assertNotNull("Failed to get TvAdManager.", mManager);

        for (TvAdServiceInfo info : mManager.getTvAdServiceList()) {
            if (info.getServiceInfo().name.equals(StubTvAdService.class.getName())) {
                mStubInfo = info;
            }
        }
        assertNotNull(mStubInfo);
        mTvAdView.setCallback(getExecutor(), mCallback);
        mManager.registerCallback(getExecutor(), mMockTvAdServiceCallBack);
        mTvAdView.setOnUnhandledInputEventListener(
                new TvAdView.OnUnhandledInputEventListener() {
                    @Override
                    public boolean onUnhandledInputEvent(InputEvent event) {
                        return true;
                    }
                });
        mTvAdView.prepareAdService(mStubInfo.getId(), "linear");
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvAdView.getAdSession() != null);
        mSession = StubTvAdService.sSession;

        mTvInputManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        assertNotNull("Failed to get TvInputManager.", mTvInputManager);

        for (TvInputInfo info : mTvInputManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(StubTvInputService2.class.getName())) {
                mTvInputInfo = info;
            }
        }
        assertNotNull(mTvInputInfo);
    }

    @After
    public void tearDown() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mTvAdView.reset();
                mTvAdView.clearCallback();
                mTvAdView.clearOnUnhandledInputEventListener();
                mTvView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
        mActivity = null;
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
        mManager.unregisterCallback(mMockTvAdServiceCallBack);
    }

    @Test
    public void testGetOnUnhandledInputEventListener() {
        mOnUnhandledInputEventListener = new TvAdView.OnUnhandledInputEventListener() {
            @Override
            public boolean onUnhandledInputEvent(InputEvent event) {
                return true;
            }
        };
        mTvAdView.setOnUnhandledInputEventListener(
                mOnUnhandledInputEventListener);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mTvAdView.getOnUnhandledInputEventListener()
                        == mOnUnhandledInputEventListener;
            }
        }.run();
    }

    @Test
    public void testSendCurrentTvInputId() {
        assertNotNull(mSession);
        mSession.resetValues();
        String inputId = "inputId";
        mTvAdView.sendCurrentTvInputId(inputId);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mCurrentTvInputIdCount > 0);
        assertThat(mSession.mCurrentTvInputIdCount).isEqualTo(1);
        assertThat(mSession.mCurrentTvInputId).isEqualTo(inputId);
    }

    @Test
    public void testSendCurrentVideoBounds() {
        assertNotNull(mSession);
        mSession.resetValues();
        Rect rect = new Rect(1, 2, 6, 7);
        mTvAdView.sendCurrentVideoBounds(rect);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mCurrentVideoBoundsCount > 0);
        assertThat(mSession.mCurrentVideoBoundsCount).isEqualTo(1);
        assertThat(mSession.mCurrentVideoBounds).isEqualTo(rect);
    }

    @Test
    public void testSendCurrentChannelUri() {
        assertNotNull(mSession);
        mSession.resetValues();
        Uri testUri = createTestUri();
        mTvAdView.sendCurrentChannelUri(testUri);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mCurrentChannelUriCount > 0);
        assertThat(mSession.mCurrentChannelUriCount).isEqualTo(1);
        assertThat(mSession.mCurrentChannelUri).isEqualTo(testUri);
    }

    @Test
    public void testSendSigningResult() {
        assertNotNull(mSession);
        mSession.resetValues();
        String resultId = "id";
        byte[] resultByte = new byte[1];
        mTvAdView.sendSigningResult(resultId, resultByte);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mSigningResultCount > 0);
        assertThat(mSession.mSigningResultCount).isEqualTo(1);
        assertThat(mSession.mSigningResultId).isEqualTo(resultId);
        assertArrayEquals(mSession.mSigningResultByte, resultByte);
    }

    @Test
    public void testSendTrackInfoList() {
        assertNotNull(mSession);
        mSession.resetValues();
        TvTrackInfo testTvTrack =
                new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, "testTrackId")
                        .setVideoWidth(1920)
                        .setVideoHeight(1080)
                        .setLanguage("und")
                        .build();
        ArrayList<TvTrackInfo> info = new ArrayList<>();
        info.add(testTvTrack);
        mTvAdView.sendTrackInfoList(info);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTrackInfoListCount > 0);
        assertThat(mSession.mTrackInfoListCount).isEqualTo(1);
        assertThat(mSession.mTvTrackInfo).isEqualTo(info);
    }

    @Test
    public void testDispatchUnhandledInputEvent() {
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_I);
        assertTrue(mTvAdView.dispatchUnhandledInputEvent(event));
    }

    @Test
    public void testOnMeasure() {
        mTvAdView.onMeasure(5, 10);
    }

    @Test
    public void testOnUnhandledInputEvent() {
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q);
        mTvAdView.onUnhandledInputEvent(event);
    }

    @Test
    public void testOnLayout() {
        mTvAdView.onLayout(true, 1, 10, 5, 20);
    }

    @Test
    public void testOnVisibilityChanged() {
        mTvAdView.onVisibilityChanged(mTvAdView, View.VISIBLE);
    }

    @Test
    public void testNotifyTvMessage() {
        Bundle testBundle = createTestBundle();
        assertNotNull(mSession);
        mSession.resetValues();
        mTvAdView.notifyTvMessage(TvInputManager.TV_MESSAGE_TYPE_WATERMARK, testBundle);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTvMessageCount > 0);
        assertThat(mSession.mTvMessageCount).isEqualTo(1);
        assertThat(mSession.mTvMessageType).isEqualTo(TvInputManager.TV_MESSAGE_TYPE_WATERMARK);
        assertBundlesAreEqual(mSession.mTvMessageData, testBundle);
    }

    @Test
    public void testResetAdService() {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvAdView.resetAdService();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mResetAdServiceCount > 0);
        assertThat(mSession.mResetAdServiceCount).isEqualTo(1);
    }

    @Test
    public void testOnDetachedFromWindow() {
        mTvAdView.onDetachedFromWindow();
    }

    @Test
    public void testNotifyError() {
        assertNotNull(mSession);
        mSession.resetValues();

        String errorMessage = "msg";
        Bundle params = new Bundle();
        mTvAdView.notifyError(errorMessage, params);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mErrorCount > 0);

        assertThat(mSession.mErrorCount).isEqualTo(1);
        assertThat(mSession.mErrMessage).isEqualTo(errorMessage);
        assertBundlesAreEqual(mSession.mErrBundle, params);
    }

    @Test
    public void testStartAdService() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvAdView.startAdService();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStartAdServiceCount > 0);
        assertThat(mSession.mStartAdServiceCount).isEqualTo(1);

        assertNotNull(mSession);
        mSession.resetValues();
        mTvAdView.stopAdService();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStopAdServiceCount > 0);
        assertThat(mSession.mStopAdServiceCount).isEqualTo(1);
    }

    private TvAdView findTvAdViewById(int id) {
        return (TvAdView) mActivity.findViewById(id);
    }

    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private void runTestOnUiThread(final Runnable r) throws Throwable {
        final Throwable[] exceptions = new Throwable[1];
        mInstrumentation.runOnMainSync(new Runnable() {
            public void run() {
                try {
                    r.run();
                } catch (Throwable throwable) {
                    exceptions[0] = throwable;
                }
            }
        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    private static Uri createTestUri() {
        return createTestUri("content://com.example/");
    }

    private static Uri createTestUri(String uriString) {
        return Uri.parse(uriString);
    }

    private static Bundle createTestBundle() {
        Bundle b = new Bundle();
        b.putString("stringKey", new String("Test String"));
        return b;
    }

    private static void assertBundlesAreEqual(Bundle actual, Bundle expected) {
        if (expected != null && actual != null) {
            assertThat(actual.keySet()).isEqualTo(expected.keySet());
            for (String key : expected.keySet()) {
                assertThat(actual.get(key)).isEqualTo(expected.get(key));
            }
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }
}
