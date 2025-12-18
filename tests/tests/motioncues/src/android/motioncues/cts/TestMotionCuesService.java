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

package android.motioncues.cts;

import android.app.motioncues.MotionCuesService;
import android.util.Log;

import java.util.concurrent.CompletableFuture;

public class TestMotionCuesService extends MotionCuesService {
    private static final String TAG = "TestMotionCuesService";

    public static CompletableFuture<Boolean> sDisconnectedFuture = new CompletableFuture<>();
    public static CompletableFuture<TestMotionCuesService> sInstanceFuture = new CompletableFuture<>();
    private static final int TIMEOUT_MS = 5000;

    public static TestMotionCuesService awaitInstance() throws Exception {
        return sInstanceFuture.get(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static void awaitDisconnected() throws Exception {
        if(isConnected()) {
            sDisconnectedFuture.get(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    public static boolean isConnected() {
        return sInstanceFuture.isDone();
    }

    @Override
    public void onClientConnected() {
        Log.d(TAG, "onClientConnected");

        sInstanceFuture.complete(this);
        sDisconnectedFuture = new CompletableFuture<>();
    }

    @Override
    public void onClientDisconnected() {
        Log.d(TAG, "onClientDisconnected");
        sInstanceFuture = new CompletableFuture<>();
        sDisconnectedFuture.complete(true);
    }

    public static void reset() {
        sDisconnectedFuture = new CompletableFuture<>();
        sInstanceFuture = new CompletableFuture<>();
    }
}
