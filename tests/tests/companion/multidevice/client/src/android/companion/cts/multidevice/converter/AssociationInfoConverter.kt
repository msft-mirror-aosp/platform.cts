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

package android.companion.cts.multidevice.converter

import android.companion.AssociationInfo
import android.os.PersistableBundle
import com.google.android.mobly.snippet.SnippetObjectConverter
import java.lang.reflect.Type
import org.json.JSONObject

class AssociationInfoConverter : SnippetObjectConverter {
    override fun serialize(request: Any?): JSONObject? {
        if (request !is AssociationInfo) {
            return null
        }
        // TODO: Serialize rest of AssociationInfo.
        return JSONObject().apply {
            put("id", request.id)
            put("metadata", serializeBundle(request.metadata))
        }
    }

    override fun deserialize(jsonObject: JSONObject, type: Type): AssociationInfo? {
        // TODO: Implement deserialization.
        return null
    }

    private fun serializeBundle(bundle: PersistableBundle): JSONObject {
        val jsonObject = JSONObject()
        for (key in bundle.keySet()) {
            val value = bundle.get(key)
            if (value is PersistableBundle) {
                jsonObject.put(key, serializeBundle(value))
            } else {
                jsonObject.put(key, value)
            }
        }
        return jsonObject
    }
}
