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
package com.android.cts.appbinding.supervision;

import android.app.supervision.SupervisionAppService;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/* This class emulates a service in a supervision app that implements the SupervisionAppService */
public class MySupervisionService extends SupervisionAppService {

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {

        if (args.length > 0 && "crash".equals(args[0])) {
            // Trigger app crash
            writer.println("Crashing...");
            (new Thread(
                            () -> {
                                throw new RuntimeException();
                            }))
                    .start();
            return;
        }
        writer.print(String.format("Package=[%s]", getPackageName()));
        writer.println(String.format(" Class=[%s]", this.getClass().getName()));
    }
}
