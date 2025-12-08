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

package com.android.cts.pcc.featuretests.services;

import android.content.Context;
import android.os.ServiceSpecificException;

import java.io.IOException;

public class StorageTestServiceStub extends IStorageTestService.Stub {

    public static final int ERROR_WRITE_FAILED = 1;

    private final Context mContext;

    StorageTestServiceStub(Context context) {
        mContext = context;
    }

    @Override
    public String getFilesDirString() {
        return mContext.getFilesDir().getAbsolutePath();
    }

    @Override
    public String canWriteToFile(String absolutePath) {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(absolutePath)) {
            fos.write("hello world".getBytes());
            return absolutePath;
        } catch (IOException | SecurityException e) {
            throw new ServiceSpecificException(ERROR_WRITE_FAILED, e.getMessage());
        }
    }
}
