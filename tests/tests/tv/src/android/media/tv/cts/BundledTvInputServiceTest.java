/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.cts.util.PollingCheck;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.test.ActivityInstrumentationTestCase2;
import android.util.ArrayMap;

import android.tv.cts.R;

import com.android.cts.util.TimeoutReq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Test {@link android.media.tv.TvView}.
 */
public class BundledTvInputServiceTest
        extends ActivityInstrumentationTestCase2<TvViewStubActivity> {
    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT = 15000L;

    private TvView mTvView;
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private TvInputManager mManager;
    private final List<TvInputInfo> mPassthroughInputList = new ArrayList<>();
    private final MockCallback mCallback = new MockCallback();

    private static class MockCallback extends TvView.TvInputCallback {
        private final Map<String, Integer> mVideoUnavailableReasonMap = new ArrayMap<>();
        private final Object mLock = new Object();
        private final int VIDEO_AVAILABLE = -1;

        public Integer getVideoUnavailableReason(String inputId) {
            return mVideoUnavailableReasonMap.get(inputId);
        }

        @Override
        public void onVideoAvailable(String inputId) {
            synchronized (mLock) {
                mVideoUnavailableReasonMap.put(inputId, VIDEO_AVAILABLE);
            }
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            synchronized (mLock) {
                mVideoUnavailableReasonMap.put(inputId, reason);
            }
        }
    }

    /**
     * Instantiates a new TV view test.
     */
    public BundledTvInputServiceTest() {
        super(TvViewStubActivity.class);
    }

    /**
     * Find the TV view specified by id.
     *
     * @param id the id
     * @return the TV view
     */
    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        if (!Utils.hasTvInputFramework(mActivity)) {
            return;
        }
        mInstrumentation = getInstrumentation();
        mTvView = findTvViewById(R.id.tvview);
        mManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : mManager.getTvInputList()) {
            if (info.isPassthroughInput()) {
                mPassthroughInputList.add(info);
            }
        }
        mTvView.setCallback(mCallback);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            super.tearDown();
            return;
        }
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvView.reset();
                }
            });
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        mInstrumentation.waitForIdleSync();
        super.tearDown();
    }

    public void testTune() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        for (final TvInputInfo info : mPassthroughInputList) {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvView.tune(info.getId(),
                            TvContract.buildChannelUriForPassthroughInput(info.getId()));
                }
            });
            mInstrumentation.waitForIdleSync();
            new PollingCheck(TIME_OUT) {
                @Override
                protected boolean check() {
                    Integer reason = mCallback.getVideoUnavailableReason(info.getId());
                    return reason != null
                            && reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                            && reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING;
                }
            }.run();
        }
    }

    @TimeoutReq(minutes = 5)
    public void testTuneStress() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        // On average, the device is expected to have ~ 5 pass-through inputs (HDMI1-4 and
        // Component) and tuning should be completed within 3 seconds, which gives 15 seconds
        // for an input. Set 5 minutes of timeout for this test case and try 20 iterations.
        final int ITERATIONS = 20;
        Random random = new Random();
        for (int i = 0; i < mPassthroughInputList.size() * ITERATIONS; ++i) {
            final TvInputInfo info =
                    mPassthroughInputList.get(random.nextInt(mPassthroughInputList.size()));
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvView.tune(info.getId(),
                            TvContract.buildChannelUriForPassthroughInput(info.getId()));
                }
            });
            mInstrumentation.waitForIdleSync();
            new PollingCheck(TIME_OUT) {
                @Override
                protected boolean check() {
                    Integer reason = mCallback.getVideoUnavailableReason(info.getId());
                    return reason != null
                            && reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                            && reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING;
                }
            }.run();
        }
    }
}
