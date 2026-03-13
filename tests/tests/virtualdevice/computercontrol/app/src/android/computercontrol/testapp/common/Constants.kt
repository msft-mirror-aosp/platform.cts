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

package android.computercontrol.testapp.common

import android.content.ComponentName

object Constants {
    const val TAG = "ComputerControlTestApp"
    const val KEY_INTERACTION = "interaction"
    const val EXTRA_REMOTE_MESSENGER = "remote_messenger"
    const val EXTRA_REMOTE_CALLBACK_TOKEN = "remote_callback_token"
    const val ACTION_REQUEST_FOCUS = "android.computercontrol.testapp.REQUEST_FOCUS"
    const val EXTRA_FOCUS_TEXT_FIELD_ID = "text_field_id"
    const val TEXT_FIELD_1 = "textField1"
    const val TEXT_FIELD_2 = "textField2"
    const val TEST_APP_PACKAGE = "android.computercontrol.testapp"
    const val ACTION_SET_REMOTE_CALLBACK = "${TEST_APP_PACKAGE}.action.SET_REMOTE_CALLBACK"
    const val ACTION_REMOVE_REMOTE_CALLBACK = "${TEST_APP_PACKAGE}.action.REMOVE_REMOTE_CALLBACK"
    const val RECEIVER_CLASS = "${TEST_APP_PACKAGE}.app.InteractionReceiverBinder"
    val RECEIVER_COMPONENT = ComponentName(TEST_APP_PACKAGE, RECEIVER_CLASS)
}
