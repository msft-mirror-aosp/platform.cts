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
package android.app.appsearch.cts.isolated_storage_service;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import com.android.appsearch.flags.Flags;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import java.util.List;
import java.util.Objects;

@AppModeFull(reason = "Can't bind to isolated storage service from instant mode")
public class AppSearchIsolatedStorageServiceCtsTest {
    private static final String ISOLATED_STORAGE_SERVICE =
            "com.android.appsearch.ISOLATED_STORAGE_SERVICE";
    private static final String ISOLATED_STORAGE_SERVICE_CLASS_NAME =
            "com.android.server.appsearch.isolated_storage_service.IsolatedStorageService";

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ISOLATED_STORAGE)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testUnauthorizedBinding_permissionDenied() {
        Context context = ApplicationProvider.getApplicationContext();
        String packageName = maybeGetPackageName(context);
        assumeNotNull(packageName);

        Intent intent = new Intent();
        intent.setClassName(packageName, ISOLATED_STORAGE_SERVICE_CLASS_NAME);
        assertThrows(
                SecurityException.class,
                () ->
                        context.bindServiceAsUser(
                                intent,
                                new ServiceConnection() {
                                    @Override
                                    public void onServiceConnected(
                                            ComponentName name, IBinder service) {}

                                    @Override
                                    public void onServiceDisconnected(ComponentName name) {}
                                },
                                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                                UserHandle.SYSTEM));
    }

    private static @Nullable String maybeGetPackageName(@NonNull Context context) {
        Objects.requireNonNull(context);

        PackageManager pm = context.getPackageManager();
        Intent serviceIntent = new Intent(ISOLATED_STORAGE_SERVICE);
        List<ResolveInfo> resolveInfos =
                pm.queryIntentServices(
                        serviceIntent,
                        // Matches services from system applications that are direct boot aware
                        // or unaware.
                        PackageManager.GET_SERVICES
                                | PackageManager.MATCH_SYSTEM_ONLY
                                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        assumeFalse(resolveInfos.isEmpty());
        return resolveInfos.get(0).serviceInfo.packageName;
    }
}
