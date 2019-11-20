/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts;

import java.util.HashMap;
import java.util.Map;

public enum CecMessage {
    FEATURE_ABORT(0x00),
    TEXT_VIEW_ON(0x0d),
    STANDBY(0x36),
    ACTIVE_SOURCE(0x82),
    GIVE_PHYSICAL_ADDRESS(0x83),
    REPORT_PHYSICAL_ADDRESS(0x84),
    REQUEST_ACTIVE_SOURCE(0x85),
    GIVE_POWER_STATUS(0x8f),
    REPORT_POWER_STATUS(0x90),
    GET_MENU_LANGUAGE(0x91),
    INACTIVE_SOURCE(0x9d),
    CEC_VERSION(0x9e),
    GET_CEC_VERSION(0x9f),
    ABORT(0xff);

    private final int messageId;
    private static Map messageMap = new HashMap<>();

    static {
        for (CecMessage message : CecMessage.values()) {
            messageMap.put(message.messageId, message);
        }
    }

    public static CecMessage getMessage(int messageId) {
        return (CecMessage) messageMap.get(messageId);
    }

    @Override
    public String toString() {
        return String.format("%02x", messageId);
    }

    private CecMessage(int messageId) {
        this.messageId = messageId;
    }
}
