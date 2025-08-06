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

package android.sharesheet.cts

import android.graphics.Insets
import android.graphics.Rect
import android.os.Binder
import android.service.chooser.ChooserSession

internal abstract class InteractiveTestActivityControllerCallback : Binder() {
    abstract fun setTestActivityController(controller: InteractiveTestActivityController)
}

internal interface InteractiveTestActivityController {
    fun getReport(): InteractiveTestActivityReport
}

internal data class InteractiveTestActivityReport(
    val hasActiveSession: Boolean,
    val chooserSessionState: Int,
    val chooserBounds: Rect?,
    val stateUpdateHistory: List<Int>,
    val boundsUpdateHistory: List<Rect>,
    val windowHeight: Int,
    val windowInsets: Insets?,
)

internal class InteractiveTestActivityReportBuilder {
    private val reportedStates = ArrayList<Int>()
    private val reportedBounds = ArrayList<Rect>()
    private var session: ChooserSession? = null
    private var windowInsets: Insets? = null
    private var windowHeight: Int = -1

    @Synchronized
    fun addReportedState(state: Int) {
        reportedStates.add(state)
    }

    @Synchronized
    fun addReportedBound(bound: Rect) {
        reportedBounds.add(bound)
    }

    @Synchronized
    fun setSession(session: ChooserSession?) {
        this.session = session
    }

    @Synchronized
    fun setWindowInsets(insets: Insets?) {
        windowInsets = insets
    }

    @Synchronized
    fun setWindowHeight(height: Int) {
        windowHeight = height
    }

    fun build(): InteractiveTestActivityReport = InteractiveTestActivityReport(
        hasActiveSession = session != null,
        chooserSessionState = session?.state ?: -1,
        chooserBounds = session?.bounds,
        stateUpdateHistory = ArrayList(reportedStates),
        boundsUpdateHistory = ArrayList(reportedBounds),
        windowHeight = windowHeight,
        windowInsets = windowInsets,
    )
}
