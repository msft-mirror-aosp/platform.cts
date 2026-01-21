/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.contactspicker.cts;

import android.app.Activity;
import android.content.Intent;

import java.util.concurrent.CountDownLatch;

public class TestActivity extends Activity {
    public int resultCode = Integer.MIN_VALUE;
    public Intent resultData;
    public final CountDownLatch resultLatch = new CountDownLatch(1);

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        this.resultCode = resultCode;
        this.resultData = data;
        resultLatch.countDown();
    }
}
