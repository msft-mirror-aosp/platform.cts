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
package android.app.appfunctions.testutils

import android.app.appfunctions.AppFunctionName
import android.os.Parcel
import android.os.Parcelable

class TestObserverHistory(
  val changedPackageNameHistory: List<Set<String>>,
  val changedFunctionNamesHistory: List<Set<AppFunctionName>>,
): Parcelable {
  private constructor(parcel: Parcel) : this(
    changedPackageNameHistory = buildList {
      val size = parcel.readInt()
      repeat(size) {
        add(parcel.createStringArrayList()?.toSet() ?: emptySet())
      }
    },
    changedFunctionNamesHistory = buildList {
      val size = parcel.readInt()
      repeat(size) {
        add(parcel.createTypedArrayList(AppFunctionName.CREATOR)?.toSet() ?: emptySet())
      }
    }
  )

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeInt(changedPackageNameHistory.size)
    for (set in changedPackageNameHistory) {
      parcel.writeStringList(set.toList())
    }
    parcel.writeInt(changedFunctionNamesHistory.size)
    for (set in changedFunctionNamesHistory) {
      parcel.writeTypedList(set.toList())
    }
  }

  override fun describeContents(): Int = 0

  companion object {
    @JvmField val CREATOR: Parcelable.Creator<TestObserverHistory> =
      object : Parcelable.Creator<TestObserverHistory> {
        override fun createFromParcel(parcel: Parcel): TestObserverHistory =
          TestObserverHistory(parcel)

        override fun newArray(size: Int): Array<TestObserverHistory?> =
          arrayOfNulls(size)
      }
  }
}
