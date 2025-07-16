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

package android.supervision.cts

import android.app.supervision.SupervisionRecoveryInfo
import android.app.supervision.flags.Flags
import android.os.Parcel
import android.os.PersistableBundle
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SupervisionRecoveryInfo]. */
@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
class SupervisionRecoveryInfoTest : BaseSupervisionTest() {
    private companion object {
        const val TEST_ACCOUNT_TYPE = "test"
        const val TEST_ACCOUNT_NAME = "test.account@example-domain.com"
        val TEST_ACCOUNT_DATA =
            PersistableBundle().apply {
                putString("key1", "value1")
                putString("key2", "value2")
            }
        const val TEST_STATE_VERIFIED = SupervisionRecoveryInfo.STATE_VERIFIED
        const val TEST_STATE_PENDING = SupervisionRecoveryInfo.STATE_PENDING
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.SupervisionRecoveryInfo#getAccountData",
                "android.app.supervision.SupervisionRecoveryInfo#getAccountName",
                "android.app.supervision.SupervisionRecoveryInfo#getAccountType",
                "android.app.supervision.SupervisionRecoveryInfo#getState",
            ]
    )
    fun constructorAndGetters() {
        val info =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )

        assertThat(info.accountName).isEqualTo(TEST_ACCOUNT_NAME)
        assertThat(info.accountType).isEqualTo(TEST_ACCOUNT_TYPE)
        assertThat(info.state).isEqualTo(TEST_STATE_VERIFIED)
        assertThat(info.accountData).isEqualTo(TEST_ACCOUNT_DATA)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.SupervisionRecoveryInfo#getAccountData",
                "android.app.supervision.SupervisionRecoveryInfo#getAccountName",
                "android.app.supervision.SupervisionRecoveryInfo#getAccountType",
                "android.app.supervision.SupervisionRecoveryInfo#getState",
            ]
    )
    fun constructorAndGetters_nullAccountData() {
        val info =
            SupervisionRecoveryInfo(TEST_ACCOUNT_NAME, TEST_ACCOUNT_TYPE, TEST_STATE_PENDING, null)

        assertThat(info.accountName).isEqualTo(TEST_ACCOUNT_NAME)
        assertThat(info.accountType).isEqualTo(TEST_ACCOUNT_TYPE)
        assertThat(info.state).isEqualTo(TEST_STATE_PENDING)
        assertThat(info.accountData.toString()).isEqualTo(PersistableBundle.EMPTY.toString())
    }

    @Test
    fun parcel() {
        val parcel = Parcel.obtain()
        try {
            val parcelable =
                SupervisionRecoveryInfo(
                    TEST_ACCOUNT_NAME,
                    TEST_ACCOUNT_TYPE,
                    TEST_STATE_VERIFIED,
                    TEST_ACCOUNT_DATA,
                )
            parcelable.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val createdFromParcel = SupervisionRecoveryInfo.CREATOR.createFromParcel(parcel)
            assertValues(
                createdFromParcel,
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun parcel_nullAccountData() {
        val parcel = Parcel.obtain()
        try {
            val parcelable =
                SupervisionRecoveryInfo(
                    TEST_ACCOUNT_NAME,
                    TEST_ACCOUNT_TYPE,
                    TEST_STATE_PENDING,
                    null,
                )
            parcelable.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val createdFromParcel = SupervisionRecoveryInfo.CREATOR.createFromParcel(parcel)
            assertValues(
                createdFromParcel,
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_PENDING,
                null,
            )
        } finally {
            parcel.recycle()
        }
    }

    private fun assertValues(
        info: SupervisionRecoveryInfo,
        expectedAccountName: String?,
        expectedAccountType: String?,
        expectedState: Int,
        expectedAccountData: PersistableBundle?,
    ) {
        assertThat(info.accountName).isEqualTo(expectedAccountName)
        assertThat(info.accountType).isEqualTo(expectedAccountType)
        assertThat(info.state).isEqualTo(expectedState)
        if (expectedAccountData != null) {
            assertThat(info.accountData.getString("key1"))
                .isEqualTo(expectedAccountData.getString("key1"))
            assertThat(info.accountData.getString("key2"))
                .isEqualTo(expectedAccountData.getString("key2"))
        } else {
            assertThat(info.accountData.toString()).isEqualTo(PersistableBundle.EMPTY.toString())
        }
    }

    @Test
    fun testEquals() {
        val info1 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        val info2 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        val info3 =
            SupervisionRecoveryInfo(
                "different_account_name",
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        val info4 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                "different_account_type",
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        val info5 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                PersistableBundle().apply { putString("key1", "different_value") },
            )
        val info6 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_PENDING,
                TEST_ACCOUNT_DATA,
            )

        assertThat(info1).isEqualTo(info2)
        assertThat(info1).isNotEqualTo(info3)
        assertThat(info1).isNotEqualTo(info4)
        assertThat(info1).isNotEqualTo(info5)
        assertThat(info1).isNotEqualTo(info6)
    }

    @Test
    fun testHashCode() {
        val info1 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        val info2 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        val info3 =
            SupervisionRecoveryInfo(
                "different_account_name",
                TEST_ACCOUNT_TYPE,
                TEST_STATE_VERIFIED,
                TEST_ACCOUNT_DATA,
            )
        val info4 =
            SupervisionRecoveryInfo(
                TEST_ACCOUNT_NAME,
                TEST_ACCOUNT_TYPE,
                TEST_STATE_PENDING,
                TEST_ACCOUNT_DATA,
            )

        assertThat(info1.hashCode()).isEqualTo(info2.hashCode())
        assertThat(info1.hashCode()).isNotEqualTo(info3.hashCode())
        assertThat(info1.hashCode()).isNotEqualTo(info4.hashCode())
    }
}
