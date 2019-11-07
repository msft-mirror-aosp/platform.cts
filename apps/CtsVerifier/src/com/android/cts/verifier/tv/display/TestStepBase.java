/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.verifier.tv.display;

import android.view.View;

import com.android.cts.verifier.tv.TvAppVerifierActivity;

/**
 * Encapsulates the logic of a test step, which displays a human instructions and a button to start
 * the test.
 */
public abstract class TestStepBase {
    final protected TvAppVerifierActivity mContext;
    final private String mInstructionText;
    final private int mButtonTextId;
    private View mViewItem;
    private boolean mHasPassed;
    private Runnable mOnDoneListener;

    /**
     * Constructs a test step containing instruction to the user and a button.
     *
     * @param context The test activity which this test step is part of.
     * @param instructionText Text of the test instruction visible to the user.
     * @param buttonTextId Id of a string resource containing the text of the button.
     */
    public TestStepBase(TvAppVerifierActivity context, String instructionText, int buttonTextId) {
        this.mContext = context;
        this.mInstructionText = instructionText;
        this.mButtonTextId = buttonTextId;
    }

    /**
     * Constructs a test step containing instruction to the user and a button.
     *
     * @param context The test activity which this test step is part of.
     * @param instructionTextId Id of a string resource with test instructions visible to the user.
     * @param buttonTextId Id of a string resource containing the text of the button.
     */
    public TestStepBase(TvAppVerifierActivity context, int instructionTextId, int buttonTextId) {
        this.mContext = context;
        this.mInstructionText = context.getResources().getString(instructionTextId);
        this.mButtonTextId = buttonTextId;
    }

    public boolean hasPassed() {
        return mHasPassed;
    }

    /**
     * Creates the View for this test step in the context {@link TvAppVerifierActivity}.
     */
    public void createUiElements() {
        mViewItem = mContext.createUserItem(mInstructionText, mButtonTextId,
                (View view) -> onButtonClickRunTest());
    }

    /**
     * Enables the button of this test step.
     */
    public void enableButton() {
        TvAppVerifierActivity.setButtonEnabled(mViewItem, true);
    }

    /**
     * Disables the button of this test step.
     */
    public void disableButton() {
        TvAppVerifierActivity.setButtonEnabled(mViewItem, false);
    }

    public void setOnDoneListener(Runnable listener) {
        mOnDoneListener = listener;
    }

    protected abstract void onButtonClickRunTest();

    protected void doneWithPassingState(boolean state) {
        mHasPassed = state;
        TvAppVerifierActivity.setPassState(mViewItem, state);

        if (mOnDoneListener != null) {
            mOnDoneListener.run();
        }
    }
}
