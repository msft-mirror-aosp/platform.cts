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

package android.server.wm.backnavigation;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;

public class BackNavigationActivity extends Activity {

    boolean mOnBackPressedCalled;
    boolean mOnUserInteractionCalled;
    CountDownLatch mReceiveMotionCancel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mReceiveMotionCancel = new CountDownLatch(1);
    }

    @Override
    public void onBackPressed() {
        mOnBackPressedCalled = true;
        super.onBackPressed();
    }

    @Override
    public void onUserInteraction() {
        mOnUserInteractionCalled = true;
        super.onUserInteraction();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            mReceiveMotionCancel.countDown();
        }
        return super.onTouchEvent(event);
    }
}
