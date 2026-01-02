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

package com.android.bedstead.testapp;

import android.app.supervision.Policy;
import com.android.eventlib.premade.EventLibSupervisionAppService;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/* This class emulates a service in a supervision app that implements the SupervisionAppService */
public class BaseTestSupervisionAppService extends EventLibSupervisionAppService {

    @Override
    public void onSupervisionEnabled() {
        super.onSupervisionEnabled();
    }

    @Override
    public void onSupervisionDisabled() {
        super.onSupervisionDisabled();
    }

    @Override
    public void onPolicyChanged(Policy policy) {
        super.onPolicyChanged(policy);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
    }
}
