/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.harrier;

import java.lang.annotation.Annotation;

/**
 * Interface used to register a new class which can execute Harrier annotations.
 *
 * This can be used to add additional harrier-compatible annotations without modifying harrier
 */
// This is written in Java because Kotlin interfaces can't expose default methods to Java
public interface AnnotationExecutor {
    /**
     * Called when an annotation should be applied.
     *
     * <p>This should take care of recording any state necessary to correctly restore state after
     * the test.
     */
    void applyAnnotation(Annotation annotation);

    /**
     * Requests the executor to restore the previous state of any non-shareable changes.
     */
    default void teardownShareableState() {}

    /**
     * Requests the executor to restore the previous state of any shareable changes.
     */
    default void teardownNonShareableState() {}

    /**
     * Called when a test has failed which used this annotation executor.
     */
    default void onTestFailed(Throwable exception) {}
}
