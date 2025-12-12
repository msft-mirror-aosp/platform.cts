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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface Action : Parcelable {
    @Parcelize
    data class Tap(var x: Int = 0, var y: Int = 0) : Action {
        override fun toString(): String {
            return "Tap(x=$x, y=$y)"
        }
    }

    @Parcelize
    data class LongPress(var x: Int = 0, var y: Int = 0) : Action {
        override fun toString(): String {
            return "LongPress(x=$x, y=$y)"
        }
    }

    @Parcelize
    data class Swipe(var x1: Int = 0, var y1: Int = 0, var x2: Int = 0, var y2: Int = 0) : Action {
        override fun toString(): String {
            return "Swipe(from=($x1, $y1), to=($x2, $y2))"
        }
    }

    @Parcelize
    data object GoBack : Action {
        override fun toString(): String {
            return "GoBack"
        }
    }

    @Parcelize
    data class TextFieldValueChange(
            val textFieldId: String,
            val text: String,
            val uncommittedText: String?
    ) : Action {
        override fun toString(): String {
            return "TextFieldValueChange(id=$textFieldId, value=$text, uncommittedText=$uncommittedText)"
        }
    }
}

@Parcelize
data class Interaction(
    var action: Action? = null,
    var timestamp: Long = System.currentTimeMillis(),
) : Parcelable {
    override fun toString(): String {
        val actionString = action?.toString() ?: "null"
        return "{action = $actionString, timestamp= $timestamp}"
    }
}
