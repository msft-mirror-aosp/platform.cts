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

package android.server.biometrics.util;

/**
 * Collection of constants used by the wallet test helper app.
 *
 * <p>This class defines package names, activity names, and intent extras used to communicate
 * results and trigger actions within the test helper components, including {@link
 * android.server.biometrics.wallet.TestHelperActivity} and {@link
 * android.server.biometrics.wallet.TestHelperBroadcastReceiver}.
 */
public final class WalletTestHelperConstants {
    // Constants related to the wallet test helper app's `TestHelperActivity`.
    public static final String PACKAGE_NAME = "android.server.biometrics.wallet";
    public static final String ACTIVITY_NAME = PACKAGE_NAME + ".TestHelperActivity";
    public static final String INTENT_RESULT = PACKAGE_NAME + ".INTENT_RESULT";
    public static final String INTENT_EXTRA_MODALITIES = PACKAGE_NAME + ".INTENT_EXTRA_MODALITIES";
    public static final String INTENT_EXTRA_STRENGTHS = PACKAGE_NAME + ".INTENT_EXTRA_STRENGTHS";
    public static final String INTENT_EXTRA_EXCEPTION = PACKAGE_NAME + ".INTENT_EXTRA_EXCEPTION";

    // Constants related to the wallet test helper app's `TestHelperActivity` with missing
    // permissions.
    public static final String NO_API_PERMISSION_PACKAGE_NAME =
            "android.server.biometrics.wallet.noapipermission";
    public static final String NO_BIO_PERMISSION_PACKAGE_NAME =
            "android.server.biometrics.wallet.nobiopermission";

    // Constants related to the wallet test helper app's `TestHelperBroadcastReceiver`.
    public static final String BACKGROUND_INTENT_TRIGGER =
            PACKAGE_NAME + ".BACKGROUND_INTENT_TRIGGER";
    public static final String BACKGROUND_INTENT_RESULT =
            PACKAGE_NAME + ".BACKGROUND_INTENT_RESULT";
    public static final String BACKGROUND_INTENT_EXTRA_EXCEPTION =
            PACKAGE_NAME + ".BACKGROUND_INTENT_EXTRA_EXCEPTION";
}
