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

package android.companion.cts.core

import android.companion.datatransfer.continuity.RemoteTask
import android.graphics.drawable.Icon
import android.os.Parcel
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [android.companion.datatransfer.continuity.RemoteTask].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:RemoteTaskTest
 */
@ApiTest(
    apis =
        [
            "android.companion.datatransfer.continuity.RemoteTask#getCompanionDeviceAssociationId",
            "android.companion.datatransfer.continuity.RemoteTask#getTaskId",
            "android.companion.datatransfer.continuity.RemoteTask#getLabel",
            "android.companion.datatransfer.continuity.RemoteTask#getIcon",
            "android.companion.datatransfer.continuity.RemoteTask#isHandoffEnabled",
            "android.companion.datatransfer.continuity.RemoteTask#getAssociationDisplayName",
            "android.companion.datatransfer.continuity.RemoteTask#getLastUsedTimestampMillis",
            "android.companion.datatransfer.continuity.RemoteTask#isTaskInForeground",
            "android.companion.datatransfer.continuity.RemoteTask#getPackageName",
            "android.companion.datatransfer.continuity.RemoteTask#writeToParcel",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#build",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#setLabel",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#setIcon",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#setHandoffEnabled",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#setAssociationDisplayName",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#setLastUsedTimestampMillis",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#setTaskInForeground",
            "android.companion.datatransfer.continuity.RemoteTask.Builder#setPackageName",
        ]
)
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class RemoteTaskTest : CoreTestBase() {

    @Test
    fun testBuilder_defaultValues() {
        val remoteTask = RemoteTask.Builder(ASSOCIATION_ID, TASK_ID).build()
        assertEquals(ASSOCIATION_ID, remoteTask.companionDeviceAssociationId)
        assertEquals(TASK_ID, remoteTask.taskId)
        assertEquals(null, remoteTask.label)
        assertEquals(null, remoteTask.icon)
        assertEquals(false, remoteTask.isHandoffEnabled)
        assertEquals(null, remoteTask.associationDisplayName)
        assertEquals(0, remoteTask.lastUsedTimestampMillis)
        assertEquals(null, remoteTask.packageName)
        assertEquals(false, remoteTask.isTaskInForeground)
    }

    @Test
    fun testBuilder_allValues() {
        val icon = Icon.createWithResource(context, android.R.drawable.ic_menu_add)
        val remoteTask =
            RemoteTask.Builder(ASSOCIATION_ID, TASK_ID)
                .setLabel(LABEL)
                .setIcon(icon)
                .setHandoffEnabled(true)
                .setAssociationDisplayName(ASSOCIATION_DISPLAY_NAME)
                .setLastUsedTimestampMillis(LAST_USED_TIMESTAMP_MILLIS)
                .setPackageName(PACKAGE_NAME)
                .setTaskInForeground(true)
                .build()
        assertEquals(ASSOCIATION_ID, remoteTask.companionDeviceAssociationId)
        assertEquals(TASK_ID, remoteTask.taskId)
        assertEquals(LABEL, remoteTask.label)
        assertEquals(icon, remoteTask.icon)
        assertEquals(true, remoteTask.isHandoffEnabled)
        assertEquals(ASSOCIATION_DISPLAY_NAME, remoteTask.associationDisplayName)
        assertEquals(LAST_USED_TIMESTAMP_MILLIS, remoteTask.lastUsedTimestampMillis)
        assertEquals(PACKAGE_NAME, remoteTask.packageName)
        assertEquals(true, remoteTask.isTaskInForeground)
    }

    @Test
    fun testParcelable_defaultValues() {
        val remoteTask = RemoteTask.Builder(ASSOCIATION_ID, TASK_ID).build()
        val parcel = Parcel.obtain()
        remoteTask.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val unparceled = RemoteTask.CREATOR.createFromParcel(parcel)
        assertEquals(remoteTask, unparceled)
    }

    @Test
    fun testParcelable_allValues() {
        val icon = Icon.createWithResource(context, android.R.drawable.ic_menu_add)
        val remoteTask =
            RemoteTask.Builder(ASSOCIATION_ID, TASK_ID)
                .setLabel(LABEL)
                .setIcon(icon)
                .setHandoffEnabled(true)
                .setAssociationDisplayName(ASSOCIATION_DISPLAY_NAME)
                .setLastUsedTimestampMillis(LAST_USED_TIMESTAMP_MILLIS)
                .setTaskInForeground(true)
                .setPackageName(PACKAGE_NAME)
                .build()
        val parcel = Parcel.obtain()
        remoteTask.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val unparceled = RemoteTask.CREATOR.createFromParcel(parcel)
        assertEquals(remoteTask, unparceled)
    }

    private companion object {
        const val TASK_ID = 1
        const val ASSOCIATION_ID = 2
        const val LABEL = "label"
        const val ASSOCIATION_DISPLAY_NAME = "association_display_name"
        const val LAST_USED_TIMESTAMP_MILLIS = 123456789L
        const val PACKAGE_NAME = "package_name"
    }
}
