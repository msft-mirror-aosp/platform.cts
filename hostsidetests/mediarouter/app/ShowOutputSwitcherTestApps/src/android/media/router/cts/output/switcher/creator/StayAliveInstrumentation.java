/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.media.router.cts.output.switcher.creator;

import android.app.Instrumentation;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Enables {@link MediaSessionCreatorService} to adopt shell permission identity.
 *
 * <p>This application doesn't actually have any tests, so we need to hold this app alive to give
 * the tests (who live in another app) to bind to the session creation service and launch the output
 * switcher.
 */
public class StayAliveInstrumentation extends Instrumentation {

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        InstrumentationRegistry.registerInstance(this, arguments);
    }

    @Override
    public void onStart() {
        try {
            // We block until the service has been destroyed.
            MediaSessionCreatorService.sServiceDestroyedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
