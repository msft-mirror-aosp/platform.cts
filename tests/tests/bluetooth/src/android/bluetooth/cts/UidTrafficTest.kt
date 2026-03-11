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

package android.bluetooth.cts

import android.bluetooth.UidTraffic
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class UidTrafficTest {

    private lateinit var uidTraffic: UidTraffic

    @Before
    fun setUp() {
        val uidTrafficParcel = Parcel.obtain()
        uidTrafficParcel.writeInt(1000)
        uidTrafficParcel.writeLong(2000)
        uidTrafficParcel.writeLong(3000)
        uidTrafficParcel.setDataPosition(0)
        uidTraffic = UidTraffic.CREATOR.createFromParcel(uidTrafficParcel)
        assertThat(uidTraffic).isNotNull()
        uidTrafficParcel.recycle()
    }

    @Test
    fun cloneMethod() {
        val clonedUidTraffic = uidTraffic.clone()
        assertThat(clonedUidTraffic).isNotNull()
        assertThat(clonedUidTraffic.uid).isEqualTo(uidTraffic.uid)
        assertThat(clonedUidTraffic.rxBytes).isEqualTo(uidTraffic.rxBytes)
        assertThat(clonedUidTraffic.txBytes).isEqualTo(uidTraffic.txBytes)
    }

    @Test
    fun getMethod() {
        assertThat(uidTraffic.uid).isEqualTo(1000)
        assertThat(uidTraffic.rxBytes).isEqualTo(2000)
        assertThat(uidTraffic.txBytes).isEqualTo(3000)
    }
}
