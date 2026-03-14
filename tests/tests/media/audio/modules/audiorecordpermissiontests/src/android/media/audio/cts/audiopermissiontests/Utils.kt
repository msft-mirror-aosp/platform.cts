/*
 * Copyright 2025 The Android Open Source Project
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

package android.media.audio.cts.audiopermissiontests

import android.media.audio.cts.audiopermissiontests.common.*

import android.app.BroadcastOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerExemptionManager.REASON_UNKNOWN;
import android.os.PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED;

import com.android.compatibility.common.util.SystemUtil


fun getExemptionBundle() = BroadcastOptions.makeBasic().apply {
        setTemporaryAppAllowlist(
                        /* durationMs= */ 10_000,
                        TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                        REASON_UNKNOWN,
                        "");
}.toBundle()

fun bounceService(context: Context, pkg: String, shouldForeground: Boolean, serviceName: String) {
    Intent(pkg + ACTION_BOUNCE_SERVICE).apply {
        setComponent(ComponentName(pkg, pkg + ".TrampolineReceiver"))
        putExtra(EXTRA_SHOULD_FOREGROUND, true)
        putExtra(EXTRA_SERVICE_NAME, serviceName)
    }.let {
        SystemUtil.runWithShellPermissionIdentity {
            context.sendBroadcast(it, /* perm= */ null, getExemptionBundle())
        }
    }
    SystemUtil.runShellCommand("am wait-for-broadcast-barrier");
    SystemUtil.runShellCommand("am unfreeze --sticky " + pkg);

}

