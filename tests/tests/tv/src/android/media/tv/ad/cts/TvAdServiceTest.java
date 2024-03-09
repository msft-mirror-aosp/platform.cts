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

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.media.tv.ad.TvAdManager;
import android.media.tv.ad.TvAdServiceInfo;
import android.media.tv.ad.TvAdView;
import android.os.ConditionVariable;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.tv.cts.R;
import android.view.InputEvent;

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

    private final MockCallback mCallback = new MockCallback();

    public static class MockCallback extends TvAdView.TvAdCallback {

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
        mTvAdView.setOnUnhandledInputEventListener(getExecutor(),
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
                mTvView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
        mActivity = null;
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
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
}
