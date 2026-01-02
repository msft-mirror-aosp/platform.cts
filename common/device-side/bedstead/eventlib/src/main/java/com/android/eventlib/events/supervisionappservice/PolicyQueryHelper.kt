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

package com.android.eventlib.events.supervisionappservice

import android.app.supervision.Policy
import android.os.Parcel
import android.os.Parcelable
import com.android.queryable.Queryable
import com.android.queryable.QueryableBaseWithMatch
import com.android.queryable.util.SerializableParcelWrapper
import java.io.Serializable
import java.util.Objects

public final class PolicyQueryHelper<E : Queryable> : PolicyQuery<E>, Serializable, Parcelable {

    @Transient
    private val mQuery: E?
    private var mEqualsValue: SerializableParcelWrapper<Policy>? = null

    public class PolicyQueryBase :
        QueryableBaseWithMatch<Policy, PolicyQueryHelper<PolicyQueryBase>> {
        constructor() : super() {
            setQuery(PolicyQueryHelper(this))
        }

        internal constructor(inParcel: Parcel) : super(inParcel)

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
        }

        public companion object CREATOR : Parcelable.Creator<PolicyQueryBase> {
            override fun createFromParcel(parcel: Parcel): PolicyQueryBase {
                return PolicyQueryBase(parcel)
            }

            override fun newArray(size: Int): Array<PolicyQueryBase?> {
                return arrayOfNulls(size)
            }
        }
    }

    public constructor(query: E) {
        mQuery = query
    }

    private constructor(inParcel: Parcel) {
        mQuery = null
        if (inParcel.readByte().toInt() == 1) {
            mEqualsValue = inParcel.readParcelable(PolicyQueryHelper::class.java.classLoader)
        }
    }

    override fun isEqualTo(policy: Policy): E {
        mEqualsValue = SerializableParcelWrapper(policy)
        return mQuery!!
    }

    override fun isEmptyQuery(): Boolean {
        return mEqualsValue == null
    }

    override fun matches(value: Policy): Boolean {
        if (mEqualsValue != null && mEqualsValue != SerializableParcelWrapper(value)) {
            return false
        }
        return true
    }

    public fun matches(value: SerializableParcelWrapper<Policy>): Boolean {
        if (value.get() == null) {
            return mEqualsValue == null
        }
        return matches(value.get()!!)
    }

    override fun describeQuery(fieldName: String): String {
        val queryStrings = mutableListOf<String>()
        if (mEqualsValue != null) {
            queryStrings.add("$fieldName must equal ${mEqualsValue!!.get()}")
        }
        return Queryable.joinQueryStrings(queryStrings)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if (mEqualsValue == null) {
            dest.writeByte(0.toByte())
        } else {
            dest.writeByte(1.toByte())
            dest.writeParcelable(mEqualsValue, flags)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PolicyQueryHelper<*>) return false
        return mEqualsValue == other.mEqualsValue
    }

    override fun hashCode(): Int {
        return Objects.hash(mEqualsValue)
    }

    public companion object CREATOR : Parcelable.Creator<PolicyQueryHelper<*>> {
        override fun createFromParcel(parcel: Parcel): PolicyQueryHelper<*> {
            return PolicyQueryHelper<Queryable>(parcel)
        }

        override fun newArray(size: Int): Array<PolicyQueryHelper<*>?> {
            return arrayOfNulls(size)
        }
    }
}
