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
 * limitations under the License.
 */

package android.contentcaptureservice.cts;

import android.content.Context;
import android.contentcaptureservice.cts.common.Visitor;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewStructure;
import android.view.contentcapture.ContentCaptureSession;

import androidx.annotation.NonNull;

/**
 * A view that can be used to emulate custom behavior (like virtual children)
 */
public class CustomView extends View {

    private static final String TAG = CustomView.class.getSimpleName();

    private Visitor<ViewStructure> mDelegate;

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void onProvideContentCaptureStructure(ViewStructure structure) {
        if (mDelegate != null) {
            Log.d(TAG, "onProvideContentCaptureStructure(): delegating");
            structure.setClassName(getAccessibilityClassName().toString());
            mDelegate.visit(structure);
            Log.d(TAG, "onProvideContentCaptureStructure(): delegated");
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            final ContentCaptureSession session = getContentCaptureSession();
            final ViewStructure structure = session.newViewStructure(this);
            onProvideContentCaptureStructure(structure);
            session.notifyViewAppeared(structure);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return CustomView.class.getName();
    }

    void setContentCaptureDelegate(@NonNull Visitor<ViewStructure> delegate) {
        mDelegate = delegate;
    }
}
