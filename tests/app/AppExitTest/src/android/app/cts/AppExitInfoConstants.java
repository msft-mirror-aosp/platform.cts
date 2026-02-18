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
package android.app.cts;

/**
 * Shared constants between the host-side AppExitHostTest and the device-side
 * ActivityManagerAppExitInfoTest.
 */
public final class AppExitInfoConstants {
    public static final String TEST_PKG = "android.app.cts.appexit";
    public static final String TEST_APK = "CtsAppExitTestCases.apk";
    public static final String TEST_CLASS = "android.app.cts.ActivityManagerAppExitInfoTest";

    public static final String HELPER_PKG1 = "android.externalservice.service";
    public static final String HELPER_APK1 = "CtsExternalServiceService.apk";

    public static final String STUB_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    public static final String HELPER_APK2 = "CtsSimpleApp.apk";
    public static final String PERM_PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS";
    public static final String PERM_READ_LOGS = "android.permission.READ_LOGS";
    public static final int EXIT_CODE = 123;
}
