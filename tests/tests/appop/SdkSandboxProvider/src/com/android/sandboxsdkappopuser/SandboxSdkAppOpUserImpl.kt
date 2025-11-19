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
package com.android.sandboxsdkappopuser

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

class SandboxSdkAppOpUserImpl internal constructor(private val mContext: Context) :
    ISandboxSdkAppOpUser.Stub() {
    override fun noteProxyOpWithRootAsProxied(op: String, attributionTag: String?) {
        mContext.getSystemService<AppOpsManager?>(AppOpsManager::class.java)
            .noteProxyOp(op, "root", Process.ROOT_UID, attributionTag, null)
    }
}
