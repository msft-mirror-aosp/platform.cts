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

package com.android.cts.pcc.checkingvisibilityapp;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;

public class VisibilityCheckService extends Service {

    private final IVisibilityCheckService.Stub mBinder =
            new IVisibilityCheckService.Stub() {
                @Override
                public boolean canSeeCallingPackage() {
                    int callingUid = Binder.getCallingUid();
                    PackageManager pm = getPackageManager();
                    String[] packages = pm.getPackagesForUid(callingUid);
                    if (packages == null || packages.length == 0) {
                        return false;
                    }
                    try {
                        pm.getPackageInfo(packages[0], 0);
                        return true;
                    } catch (PackageManager.NameNotFoundException e) {
                        return false;
                    }
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
