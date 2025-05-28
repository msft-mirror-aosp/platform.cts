/*
 * Copyright 2025 The Android Open Source Project
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

package android.accessibilityservice.cts.utils;

import android.app.Instrumentation;
import android.content.Context;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Test rule that save and restore the animation scales, also have a convenient method to change the
 * animation scales.
 */
public class AnimationTestRule implements TestRule {
    private static final String WINDOW_ANIMATION_SCALE = Settings.Global.WINDOW_ANIMATION_SCALE;
    private static final String TRANSITION_ANIMATION_SCALE =
            Settings.Global.TRANSITION_ANIMATION_SCALE;
    private static final String ANIMATOR_DURATION_SCALE = Settings.Global.ANIMATOR_DURATION_SCALE;

    private float mInitialWindowAnimationScale;
    private float mInitialTransitionAnimationScale;
    private float mInitialAnimatorDurationScale;

    private final Instrumentation mInstrumentation;

    public AnimationTestRule() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                saveAnimationScales();
                try {
                    base.evaluate();
                } finally {
                    restoreAnimationScales();
                }
            }
        };
    }

    /** Saves the current animation scale values from the device's global settings. */
    private void saveAnimationScales() {
        Context context = mInstrumentation.getContext();
        mInitialWindowAnimationScale = getGlobalFloat(context, WINDOW_ANIMATION_SCALE);
        mInitialTransitionAnimationScale = getGlobalFloat(context, TRANSITION_ANIMATION_SCALE);
        mInitialAnimatorDurationScale = getGlobalFloat(context, ANIMATOR_DURATION_SCALE);
    }

    /** Set the animation scale values */
    public void setAnimationScale(float scale) {
        Context context = mInstrumentation.getContext();
        setGlobalFloat(context, WINDOW_ANIMATION_SCALE, scale);
        setGlobalFloat(context, TRANSITION_ANIMATION_SCALE, scale);
        setGlobalFloat(context, ANIMATOR_DURATION_SCALE, scale);
    }

    /** Restores the animation scale values to what they were before the test started. */
    private void restoreAnimationScales() {
        Context context = mInstrumentation.getContext();
        setGlobalFloat(context, WINDOW_ANIMATION_SCALE, mInitialWindowAnimationScale);
        setGlobalFloat(context, TRANSITION_ANIMATION_SCALE, mInitialTransitionAnimationScale);
        setGlobalFloat(context, ANIMATOR_DURATION_SCALE, mInitialAnimatorDurationScale);
    }

    private float getGlobalFloat(Context context, String constantName) {
        return Settings.Global.getFloat(context.getContentResolver(), constantName, 1);
    }

    private void setGlobalFloat(Context context, String constantName, float value) {
        Settings.Global.putFloat(context.getContentResolver(), constantName, value);
    }
}
