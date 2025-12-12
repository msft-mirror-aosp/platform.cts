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
package com.android.cts.verifierusbcompanion;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class AoaKey {

    /** 0x00 usage key which releases pressed keys. */
    static final AoaKey NOOP = new AoaKey(0x00);

    /** Modifiers to apply to a key. */
    public enum Modifier {
        CTRL(0b0001),
        SHIFT(0b0010),
        ALT(0b0100),
        GUI(0b1000);

        private final int mValue;

        Modifier(int value) {
            this.mValue = value;
        }
    }

    private final int mUsage;
    private final Set<Modifier> mModifiers;

    public AoaKey(int usage, Modifier... modifiers) {
        mUsage = usage;
        mModifiers = new HashSet<>(Arrays.asList(modifiers));
    }

    /**
     * @return 16-bit HID data to send according to {@link AoaHID}
     */
    byte[] toHidData() {
        int modifier = 0;
        for (Modifier m : mModifiers) {
            modifier |= m.mValue;
        }
        return new byte[] {(byte) modifier, (byte) mUsage};
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AoaKey)) {
            return false;
        }
        AoaKey key = (AoaKey) object;
        return mUsage == key.mUsage && Objects.equals(mModifiers, key.mModifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUsage, mModifiers);
    }

    @Override
    public String toString() {
        return String.format("AoaKey{%d, %s}", mUsage, mModifiers);
    }
}
