/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.cts.backportedfixes.resolver

import java.util.BitSet

internal const val ALIAS_BITSET_PROP_NAME = "ro.build.backported_fixes.alias_bitset.long_list"

/**
 * Resolves the status of a known issue alias using
 * `ro.build.backported_fixes.alias_bitset.long_list` system property.
 */
internal class SystemPropertyResolver : StatusResolver{

    val aliases: Set<Int> by lazy { initAliases() }

    override fun getBackportedFixStatus(id: Long): Status {
        return if (id in 1..1023 && aliases.contains(id.toInt())) {
            Status.Fixed
        } else {
            Status.Unknown
        }
    }

    private fun initAliases(): Set<Int> {
        // java.util.BitSet are not thread safe, so extract the aliases here.
        val bs = BitSet.valueOf(parseLongListString(getAliasBitsetString()))
        // bs.stream is not available until SDK 23 so extract aliases by hand.
        val size = bs.size()
        if (size == 0) {
            return emptySet()
        }
        val result =
            buildSet(size) {
                var next = 0
                while (next >= 0) {
                    if (bs.get(next)) {
                        add(next)
                    }
                    if (next == Integer.MAX_VALUE) {
                        break
                    }
                    next = bs.nextSetBit(next + 1)
                }
            }
        return result
    }

    private fun parseLongListString(s: String): LongArray {
        val list = buildList {
            for (x in s.split(',')) {
                try {
                    val l = x.toLong()
                    add(l)
                } catch (e: NumberFormatException) {
                    // Since the order matters, stop and just return what we have.
                    break
                }
            }
        }
        return list.toLongArray()
    }

    private fun getAliasBitsetString(): String {
        // TODO b/381267367 - add sdk check when Build.getBackportedFixStatus is available.
        try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java, String::class.java)

            return get.invoke(c, ALIAS_BITSET_PROP_NAME, "") as String
        } catch (e: Exception) {
            return ""
        }
    }
}
