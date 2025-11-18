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

package android.security.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Looper;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.PathClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48618 extends StsExtraBusinessLogicTestCase {
    private static final String TELEPHONY_COMMON_JAR = "/system/framework/telephony-common.jar";
    private static final String COMMAND_PARAMS_FACTORY_CLASS =
            "com.android.internal.telephony.cat.CommandParamsFactory";
    private static final String RIL_MESSAGE_DECODER_CLASS =
            "com.android.internal.telephony.cat.RilMessageDecoder";
    private static final String ICC_FILE_HANDLER_CLASS =
            "com.android.internal.telephony.uicc.IccFileHandler";
    private static final String COMMAND_DETAILS_CLASS =
            "com.android.internal.telephony.cat.CommandDetails";
    private static final String COMPREHENSION_TLV_CLASS =
            "com.android.internal.telephony.cat.ComprehensionTlv";
    private static final String COMPREHENSION_TLV_TAG_CLASS =
            "com.android.internal.telephony.cat.ComprehensionTlvTag";

    @AsbSecurityTest(cveBugId = 404254549)
    @Test
    @SuppressWarnings("MissingFail")
    public void testPocCVE_2025_48618() throws Exception {
        try {
            final Instrumentation instrumentation = getInstrumentation();
            final Context context = instrumentation.getContext();

            // Load the necessary internal classes
            final PathClassLoader pathClassLoader =
                    new PathClassLoader(TELEPHONY_COMMON_JAR, ClassLoader.getSystemClassLoader());
            Looper.prepare();
            final Class<?> commandParamsFactoryClass =
                    pathClassLoader.loadClass(COMMAND_PARAMS_FACTORY_CLASS);
            final Class<?> rilMessageDecoderClass =
                    pathClassLoader.loadClass(RIL_MESSAGE_DECODER_CLASS);
            final Class<?> iccFileHandlerClass = pathClassLoader.loadClass(ICC_FILE_HANDLER_CLASS);
            final Class<?> commandDetailsClass = pathClassLoader.loadClass(COMMAND_DETAILS_CLASS);
            final Class<?> comprehensionTlvClass =
                    pathClassLoader.loadClass(COMPREHENSION_TLV_CLASS);
            final Class<?> comprehensionTlvTagClass =
                    pathClassLoader.loadClass(COMPREHENSION_TLV_TAG_CLASS);

            // Create an instance of CommandParamsFactory for invoking the vulnerable method
            final Constructor<?> commandParamsFactoryConstructor =
                    commandParamsFactoryClass.getDeclaredConstructor(
                            rilMessageDecoderClass, iccFileHandlerClass, Context.class);
            commandParamsFactoryConstructor.setAccessible(true);
            final Object commandParamsFactoryInstance =
                    commandParamsFactoryConstructor.newInstance(null, null, context);

            // Ensure 'mCommandParams' is null before invoking the vulnerable method
            final Field cmdParamsField = commandParamsFactoryClass.getDeclaredField("mCmdParams");
            cmdParamsField.setAccessible(true);
            assume().that(cmdParamsField.get(commandParamsFactoryInstance)).isNull();

            // Create an instance of ComprehensionTlv for the icon
            final Constructor<?> comprehensionTlvClassConstructor =
                    comprehensionTlvClass.getDeclaredConstructor(
                            int.class, // tag
                            boolean.class, // cr
                            int.class, // length
                            byte[].class, // data
                            int.class // valueIndex
                            );
            comprehensionTlvClassConstructor.setAccessible(true);
            final Method valueOfMethod =
                    comprehensionTlvTagClass.getMethod("valueOf", String.class);
            final Object iconIdTag = valueOfMethod.invoke(null, "ICON_ID");
            final Method valueMethod = comprehensionTlvTagClass.getMethod("value");
            final int iconIdValue = (int) valueMethod.invoke(iconIdTag);
            final Object iconTlv =
                    comprehensionTlvClassConstructor.newInstance(
                            iconIdValue, // tag for ICON_ID
                            false, // cr (comprehension required)
                            2, // length of the raw data (updated to 2)
                            new byte[] {0x00, 0x01}, // the raw data byte array
                            0 // valueIndex
                            );

            // Create a list of comprehensionTlv instance
            final List<Object> ctlvs = new ArrayList<>();
            ctlvs.add(iconTlv);

            // Create commandDetails
            final Constructor<?> commandDetailsConstructor =
                    commandDetailsClass.getDeclaredConstructor();
            commandDetailsConstructor.setAccessible(true);
            final Object commandDetails = commandDetailsConstructor.newInstance();

            // Invoke the vulnerable method with a locked keyguard
            try (AutoCloseable withPinLockScreen = new LockSettingsUtil(context).withLockScreen()) {
                // Screen lock the device
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_SLEEP");
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_WAKEUP");

                // Ensure keyguardManager.isDeviceLocked() is true
                final KeyguardManager keyguardManager =
                        context.getSystemService(KeyguardManager.class);
                assume().that(poll(() -> keyguardManager.isDeviceLocked())).isTrue();

                // Invoke the vulnerable method
                // Without fix, loading the icon is attempted via loadIcon() and mCommandParams is
                // set without checking keyguard state.
                // With fix, the keyguard check prevents further code execution and test passes.
                try {
                    final Method vulnerableMethod =
                            commandParamsFactoryClass.getDeclaredMethod(
                                    "processLaunchBrowser",
                                    commandDetailsClass,
                                    java.util.List.class);
                    vulnerableMethod.setAccessible(true);
                    vulnerableMethod.invoke(commandParamsFactoryInstance, commandDetails, ctlvs);
                } catch (InvocationTargetException e) {
                    final String exceptionMessage = e.getCause().getMessage();
                    if (exceptionMessage != null) {
                        // Fail the test if 'mCmdParams' is set and if icon loading is attempted.
                        assertWithMessage(
                                        "Device is vulnerable to b/404254549. Browser can be"
                                                + " launched despite locked screen")
                                .that(
                                        e.getCause().getMessage().contains("IconLoader.loadIcon")
                                                && cmdParamsField.get(commandParamsFactoryInstance)
                                                        != null)
                                .isFalse();
                    }
                }
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
