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

import static com.android.sts.common.SystemUtil.DEFAULT_MAX_POLL_TIME_MS;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.os.Looper;
import android.platform.test.annotations.AsbSecurityTest;
import android.view.InputChannel;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodSession;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_26450 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 331730488)
    public void testPocCVE_2025_26450() {
        try {
            // Set up 'InputChannels'
            InputChannel[] channels = InputChannel.openInputChannelPair("CVE_2025_26450");
            try (AutoCloseable clientChannel = () -> channels[0].dispose();
                    AutoCloseable serverChannel = () -> channels[1].dispose(); ) {

                // Load 'IInputMethodSessionWrapper' and its inner class 'ImeInputEventReceiver'
                // from the system classloader.
                final ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
                final String IInputMethodSessionWrapper =
                        "android.inputmethodservice.IInputMethodSessionWrapper";
                final Class<?> IInputMethodSessionWrapperClass =
                        systemLoader.loadClass(IInputMethodSessionWrapper);
                String ImeInputEventReceiver =
                        IInputMethodSessionWrapper + "$ImeInputEventReceiver";
                final Class<?> ImeInputEventReceiverClass =
                        systemLoader.loadClass(ImeInputEventReceiver);

                // Use a dynamic proxy to mock InputMethodSession and capture the KeyEvent
                // received in dispatchKeyEvent for validation after event injection.
                List<KeyEvent> capturedEvents = new ArrayList<>();
                InputMethodSession inputMethodSession =
                        (InputMethodSession)
                                Proxy.newProxyInstance(
                                        InputMethodSession.class.getClassLoader(),
                                        new Class<?>[] {InputMethodSession.class},
                                        new InvocationHandler() {
                                            @Override
                                            public Object invoke(
                                                    Object proxy, Method method, Object[] args) {
                                                if ("dispatchKeyEvent".equals(method.getName())
                                                        && args[1] instanceof KeyEvent) {
                                                    capturedEvents.add((KeyEvent) args[1]);
                                                }
                                                return null;
                                            }
                                        });

                // Instantiate 'IInputMethodSessionWrapper' via reflection using 'context', proxy
                // 'InputMethodSession', and InputChannel 'channels[0]' for event injection.
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                Constructor<?> IInputMethodSessionWrapperClassCtor =
                        IInputMethodSessionWrapperClass.getDeclaredConstructor(
                                Context.class, InputMethodSession.class, InputChannel.class);
                IInputMethodSessionWrapperClassCtor.setAccessible(true);
                Object IInputMethodSessionWrapperClassObject =
                        IInputMethodSessionWrapperClassCtor.newInstance(
                                context.getApplicationContext(), inputMethodSession, channels[0]);

                // Reflectively instantiate 'ImeInputEventReceiver' (inner class) with the outer
                // class instance, client-side 'InputChannel', and main 'Looper' to prepare for
                // input event injection.
                Looper looper = context.getMainLooper();
                Constructor<?> imeInputEventReceiverCtor =
                        ImeInputEventReceiverClass.getDeclaredConstructor(
                                IInputMethodSessionWrapperClass, // outer class type
                                InputChannel.class,
                                Looper.class);
                imeInputEventReceiverCtor.setAccessible(true);
                Object imeReceiverObject =
                        imeInputEventReceiverCtor.newInstance(
                                IInputMethodSessionWrapperClassObject, // outer instance
                                channels[0],
                                looper);

                // Retrieve the private method 'onInputEvent()' from 'ImeInputEventReceiver' via
                // reflection for invocation.
                Method onInputEventMethod = null;
                for (Method method : ImeInputEventReceiverClass.getDeclaredMethods()) {
                    if (method.getName().equals("onInputEvent")) {
                        onInputEventMethod = method;
                        method.setAccessible(true);
                        break;
                    }
                }

                // Set event 'downTime' and  'eventTime' far enough to exceed
                // 'KEY_EVENT_ALLOW_PERIOD_MS', with a margin to accommodate any future increase in
                // the threshold.
                KeyEvent injectedKeyEvent =
                        new KeyEvent(
                                DEFAULT_MAX_POLL_TIME_MS, // downTime
                                DEFAULT_MAX_POLL_TIME_MS, // eventTime
                                KeyEvent.ACTION_DOWN, // action
                                KeyEvent.KEYCODE_A, // code
                                0, // repeat
                                KeyEvent.META_CTRL_ON // metaState
                                        | KeyEvent.META_ALT_ON
                                        | KeyEvent.META_FUNCTION_ON
                                        | KeyEvent.META_META_ON,
                                331730488, // deviceID
                                0 // scanCode
                                );
                onInputEventMethod.invoke(imeReceiverObject, injectedKeyEvent);

                // Without fix 'dispatchKeyEvent()' will be called by passing 'injectedKeyEvent' as
                // an argument leading to failure of test.
                for (KeyEvent event : capturedEvents) {
                    assertWithMessage(
                                    "Vulnerable to b/331730488 !!! 'KeyEvent' can be injected to"
                                        + " IME.")
                            .that(event.getDeviceId())
                            .isNotEqualTo(injectedKeyEvent.getDeviceId());
                }
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
