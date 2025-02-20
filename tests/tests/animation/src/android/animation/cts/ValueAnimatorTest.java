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
package android.animation.cts;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.test.ActivityInstrumentationTestCase2;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ValueAnimatorTest extends
        ActivityInstrumentationTestCase2<AnimationActivity> {
    private AnimationActivity mActivity;
    private ValueAnimator mValueAnimator;
    private long mDuration = 2000;

    public ValueAnimatorTest() {
        super(AnimationActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        mActivity = getActivity();
        mValueAnimator = mActivity.createAnimatorWithDuration(mDuration);
    }

    public void testDuration() throws Throwable {
        final long duration = 2000;
        ValueAnimator valueAnimatorLocal = mActivity.createAnimatorWithDuration(duration);
        startAnimation(valueAnimatorLocal);
        assertEquals(duration, valueAnimatorLocal.getDuration());
    }

    public void testIsRunning() throws Throwable {
        assertFalse(mValueAnimator.isRunning());
        startAnimation(mValueAnimator);
        ValueAnimator valueAnimatorReturned = mActivity.view.bounceYAnimator;
        assertTrue(valueAnimatorReturned.isRunning());
    }

    public void testIsStarted() throws Throwable {
        assertFalse(mValueAnimator.isRunning());
        assertFalse(mValueAnimator.isStarted());
        long startDelay = 10000;
        mValueAnimator.setStartDelay(startDelay);
        startAnimation(mValueAnimator);
        assertFalse(mValueAnimator.isRunning());
        assertTrue(mValueAnimator.isStarted());
    }

    public void testRepeatMode() throws Throwable {
        ValueAnimator mValueAnimator = mActivity.createAnimatorWithRepeatMode(
            ValueAnimator.RESTART);
        startAnimation(mValueAnimator);
        assertEquals(ValueAnimator.RESTART, mValueAnimator.getRepeatMode());
    }

    public void testRepeatCount() throws Throwable {
        int repeatCount = 2;
        ValueAnimator mValueAnimator = mActivity.createAnimatorWithRepeatCount(repeatCount);
        startAnimation(mValueAnimator);
        assertEquals(repeatCount, mValueAnimator.getRepeatCount());
    }

    public void testStartDelay() {
        long startDelay = 1000;
        mValueAnimator.setStartDelay(startDelay);
        assertEquals(startDelay, mValueAnimator.getStartDelay());
    }

    public void testGetCurrentPlayTime() throws Throwable {
        startAnimation(mValueAnimator);
        Thread.sleep(100);
        long currentPlayTime = mValueAnimator.getCurrentPlayTime();
        assertTrue(currentPlayTime  >  0);
    }

    /**
     * Test for equality within some epsilon. This accounts for minor differences
     * due to floating-point accuracy.
     */
    private void assertRoughlyEqual(float expected, float actual) {
        final float epsilon = .001f;
        assertTrue(actual > (expected - epsilon) && actual < (expected + epsilon));
    }

    public void testSetCurrentPlayTime() throws Throwable {
        final ValueAnimator anim = ValueAnimator.ofFloat(0, 100).setDuration(mDuration);
        final ValueAnimator delayedAnim = ValueAnimator.ofFloat(0, 100).setDuration(mDuration);
        delayedAnim.setStartDelay(mDuration);
        final long proposedCurrentPlayTime = mDuration / 2;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                anim.setCurrentPlayTime(mDuration / 2);
                long currentPlayTime = anim.getCurrentPlayTime();
                float currentFraction = anim.getAnimatedFraction();
                float currentValue = (Float) anim.getAnimatedValue();
                assertEquals(proposedCurrentPlayTime, currentPlayTime);
                assertRoughlyEqual(.5f, currentFraction);
                assertRoughlyEqual(50, currentValue);

                delayedAnim.setCurrentPlayTime(mDuration / 2);
                currentPlayTime = delayedAnim.getCurrentPlayTime();
                currentFraction = delayedAnim.getAnimatedFraction();
                currentValue = (Float) delayedAnim.getAnimatedValue();
                assertEquals(proposedCurrentPlayTime, currentPlayTime);
                assertRoughlyEqual(.5f, currentFraction);
                assertRoughlyEqual(50, currentValue);
            }
        });
        // Now make sure that it's still true a little later, to test that we're
        // getting a result based on the seek time, not the wall clock time
        Thread.sleep(100);
        long currentPlayTime = anim.getCurrentPlayTime();
        float currentFraction = anim.getAnimatedFraction();
        float currentValue = (Float) anim.getAnimatedValue();
        assertEquals(proposedCurrentPlayTime, currentPlayTime);
        assertRoughlyEqual(.5f, currentFraction);
        assertRoughlyEqual(50, currentValue);

        currentPlayTime = delayedAnim.getCurrentPlayTime();
        currentFraction = delayedAnim.getAnimatedFraction();
        currentValue = (Float) delayedAnim.getAnimatedValue();
        assertEquals(proposedCurrentPlayTime, currentPlayTime);
        assertRoughlyEqual(.5f, currentFraction);
        assertRoughlyEqual(50, currentValue);

        // Finally, start() the delayed animation and check that the play time was
        // not affected by playing during the delay
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                delayedAnim.start();
                long currentPlayTime = delayedAnim.getCurrentPlayTime();
                float currentFraction = delayedAnim.getAnimatedFraction();
                float currentValue = (Float) delayedAnim.getAnimatedValue();
                assertEquals(proposedCurrentPlayTime, currentPlayTime);
                assertRoughlyEqual(.5f, currentFraction);
                assertRoughlyEqual(50, currentValue);
            }
        });

        Thread.sleep(100);
        currentPlayTime = delayedAnim.getCurrentPlayTime();
        currentFraction = delayedAnim.getAnimatedFraction();
        currentValue = (Float) delayedAnim.getAnimatedValue();
        assertEquals(proposedCurrentPlayTime, currentPlayTime);
        assertRoughlyEqual(.5f, currentFraction);
        assertRoughlyEqual(50, currentValue);

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                delayedAnim.cancel();
            }
        });
    }

    public void testSetCurrentFraction() throws Throwable {
        final ValueAnimator anim = ValueAnimator.ofFloat(0, 100).setDuration(mDuration);
        final long proposedCurrentPlayTime = mDuration / 2;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                anim.setCurrentFraction(.5f);
                long currentPlayTime = anim.getCurrentPlayTime();
                float currentFraction = anim.getAnimatedFraction();
                float currentValue = (Float) anim.getAnimatedValue();
                assertEquals(proposedCurrentPlayTime, currentPlayTime);
                assertRoughlyEqual(.5f, currentFraction);
                assertRoughlyEqual(50, currentValue);
            }
        });
        // Now make sure that it's still true a little later, to test that we're
        // getting a result based on the seek time, not the wall clock time
        Thread.sleep(100);
        long currentPlayTime = anim.getCurrentPlayTime();
        float currentFraction = anim.getAnimatedFraction();
        float currentValue = (Float) anim.getAnimatedValue();
        assertEquals(proposedCurrentPlayTime, currentPlayTime);
        assertRoughlyEqual(.5f, currentFraction);
        assertRoughlyEqual(50, currentValue);
    }

    public void testGetFrameDelay() throws Throwable {
        final long frameDelay = 10;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mValueAnimator.setFrameDelay(frameDelay);
            }
        });
        startAnimation(mValueAnimator);
        Thread.sleep(100);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                long actualFrameDelay = mValueAnimator.getFrameDelay();
                assertEquals(frameDelay, actualFrameDelay);
            }
        });
    }

    public void testSetInterpolator() throws Throwable {
        AccelerateInterpolator interpolator = new AccelerateInterpolator();
        ValueAnimator mValueAnimator = mActivity.createAnimatorWithInterpolator(interpolator);
        startAnimation(mValueAnimator);
        assertTrue(interpolator.equals(mValueAnimator.getInterpolator()));
    }

    public void testCancel() throws Throwable {
        startAnimation(mValueAnimator);
        Thread.sleep(100);
        cancelAnimation(mValueAnimator);
        assertFalse(mValueAnimator.isRunning());
    }

    public void testEnd() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        ObjectAnimator objAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        startAnimation(objAnimator);
        Thread.sleep(100);
        endAnimation(objAnimator);
        float y = mActivity.view.newBall.getY();
        assertEquals(y, endY);
    }

    public void testGetAnimatedFraction() throws Throwable {
        ValueAnimator objAnimator = getAnimator();
        startAnimation(objAnimator);
        assertNotNull(objAnimator);
        float[] fractions = getValue(objAnimator, 10, "getAnimatedFraction()", 200l, null);
        for(int j = 0; j < 9; j++){
            assertTrue(fractions[j] >= 0.0);
            assertTrue(fractions[j] <= 1.0);
            assertTrue(errorMessage(fractions), fractions[j + 1] >= fractions[j]);
        }
    }

    public void testGetAnimatedValue() throws Throwable {
        ValueAnimator objAnimator = getAnimator();
        startAnimation(objAnimator);
        assertNotNull(objAnimator);
        float[] animatedValues = getValue(objAnimator, 10, "getAnimatedValue()", 200l, null);

        for(int j = 0; j < 9; j++){
            assertTrue(errorMessage(animatedValues), animatedValues[j + 1] >= animatedValues[j]);
        }
    }
    public void testGetAnimatedValue_PropertyName() throws Throwable {
        String property = "y";

        ValueAnimator objAnimator = getAnimator();
        startAnimation(objAnimator);
        assertNotNull(objAnimator);
        float[] animatedValues = getValue(objAnimator, 10, "getAnimatedValue(property)", 200l,
            property);
        for(int j = 0; j < 9; j++){
            assertTrue(errorMessage(animatedValues), animatedValues[j + 1] >= animatedValues[j]);
        }
    }

    public void testOfFloat() throws Throwable {
        float start = 0.0f;
        float end = 1.0f;
        float[] values = {start, end};
        final ValueAnimator valueAnimatorLocal = ValueAnimator.ofFloat(values);
        valueAnimatorLocal.setDuration(mDuration);
        valueAnimatorLocal.setRepeatCount(ValueAnimator.INFINITE);
        valueAnimatorLocal.setInterpolator(new AccelerateInterpolator());
        valueAnimatorLocal.setRepeatMode(ValueAnimator.RESTART);

        this.runTestOnUiThread(new Runnable(){
            public void run() {
                valueAnimatorLocal.start();
            }
        });
        Thread.sleep(100);
        boolean isRunning = valueAnimatorLocal.isRunning();
        assertTrue(isRunning);

        Float animatedValue = (Float) valueAnimatorLocal.getAnimatedValue();
        assertTrue(animatedValue >= start);
        assertTrue(animatedValue <= end);
    }

    public void testOfInt() throws Throwable {
        int start = 0;
        int end = 10;
        int[] values = {start, end};
        final ValueAnimator valueAnimatorLocal = ValueAnimator.ofInt(values);
        valueAnimatorLocal.setDuration(mDuration);
        valueAnimatorLocal.setRepeatCount(ValueAnimator.INFINITE);
        valueAnimatorLocal.setInterpolator(new AccelerateInterpolator());
        valueAnimatorLocal.setRepeatMode(ValueAnimator.RESTART);

        this.runTestOnUiThread(new Runnable(){
            public void run() {
                valueAnimatorLocal.start();
            }
        });
        Thread.sleep(100);
        boolean isRunning = valueAnimatorLocal.isRunning();
        assertTrue(isRunning);

        Integer animatedValue = (Integer) valueAnimatorLocal.getAnimatedValue();
        assertTrue(animatedValue >= start);
        assertTrue(animatedValue <= end);
    }

    public void testNoDelayOnSeekAnimation() throws Throwable {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setInterpolator(new LinearInterpolator());
        animator.setStartDelay(1000);
        animator.setDuration(300);
        animator.setCurrentPlayTime(150);
        EventWatcher watcher = new EventWatcher();
        animator.addListener(watcher);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                animator.start();
            }
        });
        assertTrue(watcher.start.await(0, TimeUnit.MILLISECONDS));
        assertTrue(((Float)animator.getAnimatedValue()) >= 0.5f);
        assertTrue(animator.getAnimatedFraction() >= 0.5f);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                animator.cancel();
            }
        });
    }

    private ValueAnimator getAnimator() {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        ValueAnimator objAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        return objAnimator;
    }

    private float[] getValue(ValueAnimator animator, int n, String methodName,
            long sleepTime, String property) throws InterruptedException {
        float[] values = new float[n];
        for(int i = 0; i < n; i++){
            Thread.sleep(sleepTime);
            float value = 0.0f;
            if(methodName.equals("getAnimatedFraction()")) {
                value = animator.getAnimatedFraction();
            }else if(methodName.equals("getAnimatedValue()")) {
              value = ((Float)animator.getAnimatedValue()).floatValue();
            }else if(methodName.equals("getAnimatedValue(property)")) {
              value = ((Float)animator.getAnimatedValue(property)).floatValue();
            }
            values[i] = value;
        }
        return values;
    }

    private void startAnimation(final ValueAnimator animator) throws Throwable {
        this.runTestOnUiThread(new Runnable() {
            public void run() {
                mActivity.startAnimation(animator);
            }
        });
    }

    private void endAnimation(final ValueAnimator animator) throws Throwable {
        this.runTestOnUiThread(new Runnable() {
            public void run() {
                animator.end();
            }
        });
    }

    private void cancelAnimation(final ValueAnimator animator) throws Throwable {
        this.runTestOnUiThread(new Runnable() {
            public void run() {
                animator.cancel();
            }
        });
    }

    private String errorMessage(float[] values) {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            message.append(values[i]).append(" ");
        }
        return message.toString();
    }

    class EventWatcher implements Animator.AnimatorListener {
        public CountDownLatch start = new CountDownLatch(1);
        public CountDownLatch end = new CountDownLatch(1);
        public CountDownLatch cancel = new CountDownLatch(1);
        public CountDownLatch repeat = new CountDownLatch(1);

        public void onAnimationCancel(Animator animation) {
            cancel.countDown();
        }

        public void onAnimationEnd(Animator animation) {
            end.countDown();
        }

        public void onAnimationRepeat(Animator animation) {
            repeat.countDown();
        }

        public void onAnimationStart(Animator animation) {
            start.countDown();
        }
    }
}
