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
package android.server.wm;

import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/** An insets animation callback which can be used to wait for animations to complete. */
public class WindowInsetsAnimationWaiter extends WindowInsetsAnimation.Callback {
    private static final String TAG = WindowInsetsAnimationWaiter.class.getSimpleName();

    private static final long ANIMATION_TIMEOUT_MILLIS = 5000;

    private @Nullable WindowInsetsAnimation mAnimation;
    private boolean mFinished = false;

    public WindowInsetsAnimationWaiter() {
        super(DISPATCH_MODE_CONTINUE_ON_SUBTREE);
    }

    @Override
    public void onPrepare(@NonNull WindowInsetsAnimation animation) {
        Log.d(TAG, "onPrepare animation=" + toString(animation), new Throwable());
        mAnimation = animation;
    }

    @NonNull
    @Override
    public WindowInsets onProgress(
            @NonNull WindowInsets insets, @NonNull List<WindowInsetsAnimation> runningAnimations) {
        return insets;
    }

    @Override
    public void onEnd(@NonNull WindowInsetsAnimation animation) {
        synchronized (this) {
            Log.d(TAG, "onEnd animation=" + toString(animation), new Throwable());
            if (mAnimation == animation) {
                mFinished = true;
                notify();
            }
        }
    }

    /**
     * Waits for an insets animation to finish.
     *
     * <p>If no insets animation is in progress, it will wait for one to start, then wait for it to
     * finish. After it returns, subsequent calls will return immediately until {@link #reset()} is
     * called.
     */
    public void waitForFinishing() throws InterruptedException {
        synchronized (this) {
            if (!mFinished) {
                wait(ANIMATION_TIMEOUT_MILLIS);
            }
        }
    }

    /** Resets the waiter so that it can wait for another animation. */
    public void reset() {
        synchronized (this) {
            mAnimation = null;
            mFinished = false;
        }
    }

    private static String toString(@NonNull WindowInsetsAnimation animation) {
        return animation
                + "{insetsTypes=" + WindowInsets.Type.toString(animation.getTypeMask())
                + " fraction=" + animation.getFraction()
                + " alpha=" + animation.getAlpha()
                + " duration=" + animation.getDurationMillis()
                + "}";
    }
}
