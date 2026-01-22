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
package android.sharesheet.cts;

import android.service.chooser.TapToShareService;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CtsSharesheetTapToShareService extends TapToShareService {
    private static final String TAG = CtsSharesheetTapToShareService.class.getSimpleName();
    private static volatile CtsSharesheetTapToShareService sInstance;
    private static volatile CountDownLatch sInstanceLatch;

    private static volatile CountDownLatch mSessionStartLatch;
    private static volatile CountDownLatch mSessionEndLatch;
    private static volatile CountDownLatch mDestroyLatch;

    public static CtsSharesheetTapToShareService getInstance() {
        return sInstance;
    }

    public static CtsSharesheetTapToShareService awaitService(long timeoutMs)
            throws InterruptedException {
        if (sInstanceLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            return sInstance;
        }
        return null;
    }

    public static void reset() {
        sInstance = null;
        sInstanceLatch = new CountDownLatch(1);
        mSessionStartLatch = new CountDownLatch(1);
        mSessionEndLatch = new CountDownLatch(1);
        mDestroyLatch = new CountDownLatch(1);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        sInstanceLatch.countDown();
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        if (mDestroyLatch != null) {
            mDestroyLatch.countDown();
        }
        super.onDestroy();
    }

    @Override
    public void onSessionStart() {
        Log.d(TAG, "onSessionStart called");
        if (mSessionStartLatch != null) {
            mSessionStartLatch.countDown();
        }
    }

    @Override
    public void onSessionEnd() {
        Log.d(TAG, "onSessionEnd called");
        if (mSessionEndLatch != null) {
            mSessionEndLatch.countDown();
        }
    }

    public boolean awaitSessionStart(long timeoutMs) throws InterruptedException {
        return mSessionStartLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public boolean awaitSessionEnd(long timeoutMs) throws InterruptedException {
        return mSessionEndLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public boolean awaitDestroy(long timeoutMs) throws InterruptedException {
        return mDestroyLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
