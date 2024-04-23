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

package com.android.bedstead.nene.flags;

/**
 * Feature flags and namespaces.
 */
public final class CommonFlags {
    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA}
     */
    public static final int KEYGUARD_DISABLE_SECURE_CAMERA = 1 << 1;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS}
     */
    public static final int KEYGUARD_DISABLE_SECURE_NOTIFICATIONS = 1 << 2;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS}
     */
    public static final int KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS = 1 << 3;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS}
     */
    public static final int KEYGUARD_DISABLE_TRUST_AGENTS = 1 << 4;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT}
     */
    public static final int KEYGUARD_DISABLE_FINGERPRINT = 1 << 5;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_REMOTE_INPUT}
     */
    public static final int KEYGUARD_DISABLE_REMOTE_INPUT = 1 << 6;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_FACE}
     */
    public static final int KEYGUARD_DISABLE_FACE = 1 << 7;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_IRIS}
     */
    public static final int KEYGUARD_DISABLE_IRIS = 1 << 8;

}
