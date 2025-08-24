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
package com.android.bedstead.dpmwrapper

import android.content.Intent
import android.os.Bundle
import android.os.CpuUsageInfo
import android.os.Parcelable
import android.util.ArraySet
import android.util.Log
import java.io.Serializable
import java.security.PrivateKey
import java.security.cert.Certificate

internal class DataFormatter private constructor() {
    init {
        throw UnsupportedOperationException("contains only static methods")
    }

    companion object {
        private val TAG: String = DataFormatter::class.java.getSimpleName()

        // NOTE: Bundle has a putObject() method that would make it much easier to marshal the args,
        // but unfortunately there is no Intent.putObjectExtra() method (and intent.getBundle()
        // returns
        // a copy, so we need to explicitly marshal any supported type).
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_INT = "int"
        private const val TYPE_LONG = "long"
        private const val TYPE_BYTE_ARRAY = "byte_array"
        private const val TYPE_FLOAT_ARRAY = "float_array"
        private const val TYPE_STRING_OR_CHAR_SEQUENCE = "string"
        private const val TYPE_PARCELABLE = "parcelable"
        private const val TYPE_SERIALIZABLE = "serializable"
        private const val TYPE_ARRAY_LIST_STRING = "array_list_string"
        private const val TYPE_ARRAY_LIST_PARCELABLE = "array_list_parcelable"

        // NOTE: the value of a TYPE_ARRAY_LIST_BYTE_ARRAY is its length - the individual elements
        // are contained on separate extras, one per index, whose name is defined by
        // getExtraNameForArrayListElement()
        private const val TYPE_ARRAY_LIST_BYTE_ARRAY = "array_list_byte_array"
        private const val TYPE_SET_STRING = "set_string"

        // Must handle each array of parcelable subclass , as they need to be explicitly converted
        private const val TYPE_CPU_USAGE_INFO_ARRAY = "cpu_usage_info_array"
        private const val TYPE_CERTIFICATE = "certificate"
        private const val TYPE_PRIVATE_KEY = "private_key"

        // Used when a method is called passing a null argument - the proper method will have to be
        // inferred using findMethod()
        private const val TYPE_NULL = "null"

        @JvmStatic
        fun addArg(intent: Intent, args: Array<Any?>, index: Int) {
            val value = args[index]
            val extraTypeName: String = getArgExtraTypeName(index)
            val extraValueName: String = getArgExtraValueName(index)
            if (Utils.Companion.VERBOSE) {
                Log.v(
                    TAG,
                    ("addArg(" +
                        index +
                        "): typeName= " +
                        extraTypeName +
                        ", valueName= " +
                        extraValueName),
                )
            }
            if (value == null) {
                logMarshalling(
                    "Adding Null",
                    index,
                    extraTypeName,
                    TYPE_NULL,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_NULL)
                return
            }
            if ((value is Boolean)) {
                logMarshalling(
                    "Adding Boolean",
                    index,
                    extraTypeName,
                    TYPE_BOOLEAN,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_BOOLEAN)
                intent.putExtra(extraValueName, value)
                return
            }
            if ((value is Int)) {
                logMarshalling(
                    "Adding Integer",
                    index,
                    extraTypeName,
                    TYPE_INT,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_INT)
                intent.putExtra(extraValueName, value)
                return
            }
            if ((value is Long)) {
                logMarshalling(
                    "Adding Long",
                    index,
                    extraTypeName,
                    TYPE_LONG,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_LONG)
                intent.putExtra(extraValueName, value)
                return
            }
            if ((value is ByteArray)) {
                logMarshalling(
                    "Adding byte[]",
                    index,
                    extraTypeName,
                    TYPE_BYTE_ARRAY,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_BYTE_ARRAY)
                intent.putExtra(extraValueName, value)
                return
            }
            if ((value is FloatArray)) {
                logMarshalling(
                    "Adding float[]",
                    index,
                    extraTypeName,
                    TYPE_FLOAT_ARRAY,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_FLOAT_ARRAY)
                intent.putExtra(extraValueName, value)
                return
            }
            if ((value is Array<*> && value.isArrayOf<CpuUsageInfo>())) {
                logMarshalling(
                    "Adding CpuUsageInfo[]",
                    index,
                    extraTypeName,
                    TYPE_CPU_USAGE_INFO_ARRAY,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_CPU_USAGE_INFO_ARRAY)
                @Suppress("UNCHECKED_CAST")
                intent.putExtra(extraValueName, value as Array<CpuUsageInfo?>)
                return
            }
            if ((value is CharSequence)) {
                logMarshalling(
                    "Adding CharSequence",
                    index,
                    extraTypeName,
                    TYPE_STRING_OR_CHAR_SEQUENCE,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_STRING_OR_CHAR_SEQUENCE)
                intent.putExtra(extraValueName, value)
                return
            }
            if (value is PrivateKey) {
                logMarshalling(
                    "Adding PrivateKey",
                    index,
                    extraTypeName,
                    TYPE_PRIVATE_KEY,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_PRIVATE_KEY)
                intent.putExtra(extraValueName, value as Serializable)
                return
            }
            if ((value is Parcelable)) {
                logMarshalling(
                    "Adding Parcelable",
                    index,
                    extraTypeName,
                    TYPE_PARCELABLE,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_PARCELABLE)
                intent.putExtra(extraValueName, value)
                return
            }

            if (value is Certificate) {
                logMarshalling(
                    "Adding Certificate",
                    index,
                    extraTypeName,
                    TYPE_CERTIFICATE,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_CERTIFICATE)
                intent.putExtra(extraValueName, value as Serializable)
                return
            }

            if ((value is MutableList<*>)) {
                val list = value

                var type: String? = null
                if (list.isEmpty()) {
                    Log.w(TAG, "Empty list at index $index; assuming it's List<String>")
                    type = TYPE_ARRAY_LIST_STRING
                } else {
                    val firstItem: Any? = list.get(0)
                    if (firstItem is String) {
                        type = TYPE_ARRAY_LIST_STRING
                    } else if (firstItem is Parcelable) {
                        type = TYPE_ARRAY_LIST_PARCELABLE
                    } else if (firstItem is ByteArray) {
                        type = TYPE_ARRAY_LIST_BYTE_ARRAY
                    } else {
                        throw IllegalArgumentException(
                            ("Unsupported List type at index $index: $firstItem")
                        )
                    }
                }

                logMarshalling("Adding $type", index, extraTypeName, type, extraValueName, value)
                intent.putExtra(extraTypeName, type)
                when (type) {
                    TYPE_ARRAY_LIST_STRING -> {
                        val arrayListString =
                            if (value is ArrayList<*>) {
                                @Suppress("UNCHECKED_CAST")
                                list as ArrayList<String?>
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                ArrayList<String?>(list as MutableList<String?>)
                            }
                        intent.putStringArrayListExtra(extraValueName, arrayListString)
                    }
                    TYPE_ARRAY_LIST_PARCELABLE -> {
                        val arrayListParcelable =
                            if (value is ArrayList<*>) {
                                @Suppress("UNCHECKED_CAST")
                                list as ArrayList<Parcelable?>
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                ArrayList<Parcelable?>(list as MutableList<Parcelable?>)
                            }
                        intent.putParcelableArrayListExtra(extraValueName, arrayListParcelable)
                    }
                    TYPE_ARRAY_LIST_BYTE_ARRAY -> {
                        val arrayListByteArray =
                            if (value is ArrayList<*>) {
                                @Suppress("UNCHECKED_CAST")
                                list as ArrayList<ByteArray?>
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                ArrayList<ByteArray?>(list as MutableList<ByteArray?>)
                            }
                        val listSize = arrayListByteArray.size
                        intent.putExtra(extraValueName, listSize)
                        var i = 0
                        while (i < listSize) {
                            intent.putExtra(
                                getExtraNameForArrayListElement(extraValueName, i),
                                arrayListByteArray.get(i),
                            )
                            i++
                        }
                    }
                    else -> // should never happen because type is checked above
                    throw AssertionError("invalid type conversion: $type")
                }
                return
            }

            // TODO(b/176993670): ArraySet<> is encapsulate as ArrayList<>, so most of the code
            // below
            // could be reused (right now it was copy-and-paste from ArrayList<>, minus the
            // Parcelable
            // part.
            if ((value is MutableSet<*>)) {
                val set = value

                var type: String? = null
                if (set.isEmpty()) {
                    Log.w(TAG, "Empty set at index $index; assuming it's Set<String>")
                    type = TYPE_SET_STRING
                } else {
                    val firstItem: Any? = set.iterator().next()
                    if (firstItem is String) {
                        type = TYPE_SET_STRING
                    } else {
                        throw IllegalArgumentException(
                            ("Unsupported Set type at index $index: $firstItem")
                        )
                    }
                }

                logMarshalling("Adding $type", index, extraTypeName, type, extraValueName, value)
                intent.putExtra(extraTypeName, type)
                when (type) {
                    TYPE_SET_STRING -> {
                        @Suppress("UNCHECKED_CAST") val stringSet = value as MutableSet<String?>
                        intent.putStringArrayListExtra(
                            extraValueName,
                            ArrayList<String?>(stringSet),
                        )
                    }
                    else -> // should never happen because type is checked above
                    throw AssertionError("invalid type conversion: $type")
                }
                return
            }

            if ((value is Serializable)) {
                logMarshalling(
                    "Adding Serializable",
                    index,
                    extraTypeName,
                    TYPE_SERIALIZABLE,
                    extraValueName,
                    value,
                )
                intent.putExtra(extraTypeName, TYPE_SERIALIZABLE)
                intent.putExtra(extraValueName, value)
                return
            }

            throw IllegalArgumentException(
                "Unsupported value type at index $index: ${value.javaClass}"
            )
        }

        private fun getExtraNameForArrayListElement(baseExtraName: String?, index: Int): String {
            return baseExtraName + "_" + index
        }

        @JvmStatic
        fun getArg(
            extras: Bundle,
            args: Array<Any?>,
            parameterTypes: Array<Class<*>?>?,
            index: Int,
        ) {
            val extraTypeName: String = getArgExtraTypeName(index)
            val extraValueName: String = getArgExtraValueName(index)
            val type = extras.getString(extraTypeName)
            if (Utils.Companion.VERBOSE) {
                Log.v(
                    TAG,
                    ("getArg(" +
                        index +
                        "): typeName= " +
                        extraTypeName +
                        ", type=" +
                        type +
                        ", valueName= " +
                        extraValueName),
                )
            }
            var value: Any? = null
            when (type) {
                TYPE_NULL ->
                    logMarshalling("Got null", index, extraTypeName, type, extraValueName, value)
                TYPE_SET_STRING -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = extras.get(extraValueName) as ArrayList<String?>?
                    value = ArraySet<String?>(list)
                    logMarshalling(
                        "Got ArraySet<String>",
                        index,
                        extraTypeName,
                        type,
                        extraValueName,
                        value,
                    )
                }
                TYPE_CPU_USAGE_INFO_ARRAY -> {
                    @Suppress("UNCHECKED_CAST")
                    val raw = extras.get(extraValueName) as Array<Parcelable?>?
                    val cast = kotlin.arrayOfNulls<CpuUsageInfo>(raw!!.size)
                    var i = 0
                    while (i < raw!!.size) {
                        cast[i] = raw!![i] as CpuUsageInfo?
                        i++
                    }
                    value = cast
                    logMarshalling(
                        "Got CpuUsageInfo[]",
                        index,
                        extraTypeName,
                        type,
                        extraValueName,
                        value,
                    )
                }
                TYPE_ARRAY_LIST_BYTE_ARRAY -> {
                    val size = extras.getInt(extraValueName)
                    val array = ArrayList<ByteArray?>(size)
                    var i = 0
                    while (i < size) {
                        val extraName: String = getExtraNameForArrayListElement(extraValueName, i)
                        array.add(extras.getByteArray(extraName))
                        i++
                    }
                    value = array
                }
                TYPE_ARRAY_LIST_STRING,
                TYPE_ARRAY_LIST_PARCELABLE,
                TYPE_BYTE_ARRAY,
                TYPE_FLOAT_ARRAY,
                TYPE_BOOLEAN,
                TYPE_INT,
                TYPE_LONG,
                TYPE_STRING_OR_CHAR_SEQUENCE,
                TYPE_PARCELABLE,
                TYPE_SERIALIZABLE,
                TYPE_CERTIFICATE,
                TYPE_PRIVATE_KEY -> {
                    value = extras.get(extraValueName)
                    logMarshalling("Got generic", index, extraTypeName, type, extraValueName, value)
                }
                else ->
                    throw IllegalArgumentException(
                        ("Unsupported value type at index $index: $extraTypeName")
                    )
            }
            if (parameterTypes != null) {
                var parameterType: Class<*>? = null
                // Must convert special types (like primitive to Object, generic list to list,
                // etc...),
                // but not those that can be inferred from getClass() (like String or array)
                when (type) {
                    TYPE_NULL -> {}
                    TYPE_BOOLEAN -> parameterType = Boolean::class.javaPrimitiveType
                    TYPE_INT -> parameterType = Int::class.javaPrimitiveType
                    TYPE_LONG -> parameterType = Long::class.javaPrimitiveType
                    TYPE_STRING_OR_CHAR_SEQUENCE -> // A String is a CharSequence, but most methods
                        // take String, so we're assuming
                        // a string and handle the exceptional cases on findMethod()
                        parameterType = String::class.java
                    TYPE_ARRAY_LIST_STRING -> parameterType = MutableList::class.java
                    TYPE_SET_STRING -> parameterType = MutableSet::class.java
                    TYPE_PRIVATE_KEY -> parameterType = PrivateKey::class.java
                    TYPE_CERTIFICATE -> parameterType = Certificate::class.java
                    else -> parameterType = value!!.javaClass
                }
                parameterTypes[index] = parameterType
            }
            args[index] = value
        }

        fun getArgExtraTypeName(index: Int): String {
            return Utils.Companion.EXTRA_ARG_PREFIX + index + "_type"
        }

        fun getArgExtraValueName(index: Int): String {
            return Utils.Companion.EXTRA_ARG_PREFIX + index + "_value"
        }

        private fun logMarshalling(
            operation: String?,
            index: Int,
            typeName: String?,
            type: String?,
            valueName: String?,
            value: Any?,
        ) {
            if (Utils.Companion.VERBOSE) {
                Log.v(
                    TAG,
                    (operation +
                        " on " +
                        index +
                        ": typeName=" +
                        typeName +
                        ", type=" +
                        type +
                        ", valueName=" +
                        valueName +
                        ", value=" +
                        value),
                )
            }
        }
    }
}
