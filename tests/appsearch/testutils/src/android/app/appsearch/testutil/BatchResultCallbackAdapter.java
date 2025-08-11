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

package android.app.appsearch.testutil;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.BatchResultCallback;
import android.os.UserHandle;

import com.google.common.util.concurrent.SettableFuture;

import java.util.Objects;

final class BatchResultCallbackAdapter<K, V> implements BatchResultCallback<K, V> {

    private static final String TAG = BatchResultCallbackAdapter.class.getSimpleName();

    private final UserAwareLogger mLogger;
    private final String mId; // Used for debugging purposes
    private final SettableFuture<AppSearchBatchResult<K, V>> mFuture;

    BatchResultCallbackAdapter(String id, SettableFuture<AppSearchBatchResult<K, V>> future) {
        mLogger = new UserAwareLogger(TAG, UserHandle.myUserId());
        mId = Objects.requireNonNull(id, "id cannot be null");
        mFuture = Objects.requireNonNull(future, "future cannot be null");
    }

    @Override
    public void onResult(AppSearchBatchResult<K, V> result) {
        mFuture.set(result);
        if (result.isSuccess()) {
            mLogger.logD("onResult(id=%s): %s", mId, result);
        } else {
            mLogger.logE("onResult(id=%s): %s", mId, result);
        }
    }

    @Override
    public void onSystemError(Throwable t) {
        mFuture.setException(t);
        mLogger.logE("onSystemError(id=%s): %s", mId, t);
    }
}
