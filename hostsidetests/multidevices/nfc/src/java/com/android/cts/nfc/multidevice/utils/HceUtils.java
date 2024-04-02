/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.nfc.multidevice.utils;

import static android.Manifest.permission.MANAGE_DEFAULT_APPLICATIONS;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.cts.nfc.multidevice.emulator.service.PaymentService1;
import com.android.cts.nfc.multidevice.emulator.service.TransportService1;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Utilites for multi-device HCE tests. */
public final class HceUtils {

    private HceUtils() {}

    public static final String MC_AID = "A0000000041010";
    public static final String PPSE_AID = "325041592E5359532E4444463031";
    public static final String TRANSPORT_AID = "F001020304";

    /** Service-specific APDU Command/Response sequences */
    public static final HashMap<String, CommandApdu[]> COMMAND_APDUS_BY_SERVICE = new HashMap<>();

    public static final HashMap<String, String[]> RESPONSE_APDUS_BY_SERVICE = new HashMap<>();

    static {
        COMMAND_APDUS_BY_SERVICE.put(
                TransportService1.class.getName(),
                new CommandApdu[] {
                    buildSelectApdu(TRANSPORT_AID, true), buildCommandApdu("80CA01E000", true)
                });

        RESPONSE_APDUS_BY_SERVICE.put(
                TransportService1.class.getName(), new String[] {"80CA9000", "83947102829000"});

        // Payment Service #1
        COMMAND_APDUS_BY_SERVICE.put(
                PaymentService1.class.getName(),
                new CommandApdu[] {
                    buildSelectApdu(PPSE_AID, true),
                    buildSelectApdu(MC_AID, true),
                    buildCommandApdu("80CA01F000", true)
                });
        RESPONSE_APDUS_BY_SERVICE.put(
                PaymentService1.class.getName(),
                new String[] {"FFFF9000", "FFEF9000", "FFDFFFAABB9000"});
    }

    /** Enables specified component */
    public static void enableComponent(PackageManager pm, ComponentName component) {
        pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** Disables specified component */
    public static void disableComponent(PackageManager pm, ComponentName component) {
        pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** Converts a byte array to hex string */
    public static String getHexBytes(String header, byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        if (header != null) {
            sb.append(header + ": ");
        }
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    /** Converts a hex string to byte array */
    public static byte[] hexStringToBytes(String s) {
        if (s == null || s.length() == 0) return null;
        int len = s.length();
        if (len % 2 != 0) {
            s = '0' + s;
            len++;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] =
                    (byte)
                            ((Character.digit(s.charAt(i), 16) << 4)
                                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /** Builds a command APDU from given string */
    public static CommandApdu buildCommandApdu(String apdu, boolean reachable) {
        return new CommandApdu(apdu, reachable);
    }

    /** Builds a select AID command APDU */
    public static CommandApdu buildSelectApdu(String aid, boolean reachable) {
        String apdu = String.format("00A40400%02X%s", aid.length() / 2, aid);
        return new CommandApdu(apdu, reachable);
    }

    /** Sets default wallet role holder to given package name */
    public static boolean setDefaultWalletRoleHolder(Context context, String packageName) {
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(MANAGE_DEFAULT_APPLICATIONS);
            roleManager.setDefaultApplication(
                    RoleManager.ROLE_WALLET,
                    packageName,
                    0,
                    MoreExecutors.directExecutor(),
                    aBoolean -> {
                        result.set(aBoolean);
                        countDownLatch.countDown();
                    });
            countDownLatch.await(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        return result.get();
    }
}
