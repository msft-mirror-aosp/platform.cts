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

final class HdmiCecConstants {

    private HdmiCecConstants() {}

    static final int REBOOT_TIMEOUT = 60000;

    static final int PHYSICAL_ADDRESS = 0x1000;
    static final int PHYSICAL_ADDRESS_LENGTH = 4; /* Num nibbles in CEC message */

    static final int PLAYBACK_DEVICE_TYPE = 0x04;

    static final int CEC_CONTROL_SELECT = 0x0;
    static final int CEC_CONTROL_UP = 0x1;
    static final int CEC_CONTROL_DOWN = 0x2;
    static final int CEC_CONTROL_LEFT = 0x3;
    static final int CEC_CONTROL_RIGHT = 0x4;
    static final int CEC_CONTROL_BACK = 0xd;
    static final int CEC_CONTROL_VOLUME_UP = 0x41;
    static final int CEC_CONTROL_VOLUME_DOWN = 0x42;
    static final int CEC_CONTROL_MUTE = 0x43;

    static final int UNRECOGNIZED_OPCODE = 0x0;

    static final int CEC_DEVICE_TYPE_TV = 0;
    static final int CEC_DEVICE_TYPE_RECORDING_DEVICE = 1;
    static final int CEC_DEVICE_TYPE_RESERVED = 2;
    static final int CEC_DEVICE_TYPE_TUNER = 3;
    static final int CEC_DEVICE_TYPE_PLAYBACK_DEVICE = 4;
    static final int CEC_DEVICE_TYPE_AUDIO_SYSTEM = 5;

}
