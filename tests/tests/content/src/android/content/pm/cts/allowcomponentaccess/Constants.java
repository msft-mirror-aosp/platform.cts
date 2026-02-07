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

package android.content.pm.cts.allowcomponentaccess;

public class Constants {
    // --- Package Names ---
    public static final String PKG_TARGET_NO_TAG =
            "com.android.cts.allowcomponentaccess.target_notag";
    public static final String PKG_TARGET_ALLOW =
            "com.android.cts.allowcomponentaccess.target_allow";
    public static final String PKG_TARGET_BLOCK =
            "com.android.cts.allowcomponentaccess.target_block";

    public static final String PKG_SOURCE_NO_TAG =
            "com.android.cts.allowcomponentaccess.source_notag";
    public static final String PKG_SOURCE_ALLOW =
            "com.android.cts.allowcomponentaccess.source_allow";
    public static final String PKG_SOURCE_BLOCK =
            "com.android.cts.allowcomponentaccess.source_block";
    public static final String PKG_SOURCE_ALLOW_CERT_PRIMARY =
            "com.android.cts.allowcomponentaccess.source_allow_cert_primary";
    public static final String PKG_SOURCE_ALLOW_CERT_ADDITIONAL =
            "com.android.cts.allowcomponentaccess.source_allow_cert_additional";
    public static final String PKG_SOURCE_BLOCK_CERT_WRONG =
            "com.android.cts.allowcomponentaccess.source_block_cert_wrong";

    // --- Actions ---
    public static final String ACTION_RELAY = "com.android.cts.allowcomponentaccess.ACTION_RELAY";
    public static final String ACTION_PING = "com.android.cts.allowcomponentaccess.PING";

    // --- Action Types (Values) ---
    public static final String ACTION_TYPE_BIND = "BIND";
    public static final String ACTION_TYPE_BROADCAST = "BROADCAST";

    // --- Bundle Keys ---
    public static final String TARGET_PKG = "target_pkg";
    public static final String TEST_ACTION = "test_action";
    public static final String CALLBACK_BINDER = "callback_binder";

    // --- Component Class Names ---
    public static final String RELAY_RECEIVER_CLASS =
            "com.android.cts.allowcomponentaccess.source.ActionRelayReceiver";
    public static final String TARGET_SERVICE_CLASS =
            "com.android.cts.allowcomponentaccess.target.TargetService";
    public static final String TARGET_RECEIVER_CLASS =
            "com.android.cts.allowcomponentaccess.target.TargetReceiver";
}
