/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.server.wm.component;

import android.content.ComponentName;

/**
 * Base class for holding component constants.
 *
 * <p>Note for Kotlin users: For new tests, it is strongly recommended to extend the {@link
 * ComponentsProvider} abstract class on a singleton {@code object} instead of extending this class.
 * It offers a more idiomatic Kotlin API and reduces boilerplate.
 *
 * <p>Legacy Java Usage: For Java-based tests, each test APK should contain a {@code Components}
 * class that extends this base class. This provides a central place for component constants.
 *
 * <p>Example:
 *
 * <pre><code>
 * package com.example.app;
 *
 * import android.content.ComponentName;
 * import android.server.wm.component.ComponentsBase;
 *
 * public class Components extends ComponentsBase {
 *   public static final ComponentName MY_ACTIVITY = component(Components.class, "MyActivity");
 * }
 * </code></pre>
 *
 * @see ComponentsProvider
 */
public class ComponentsBase {

    /**
     * Builds a {@link ComponentName} that belongs to the given {@code componentsClass}'s package.
     *
     * @param componentsClass the {@code .class} of a class named "Components". This is used to
     *     determine the package name.
     * @param className the simple class name (e.g., "MyActivity") or a fully qualified class name.
     *     Must not start with a '.'.
     * @return a {@link ComponentName} for the specified class.
     */
    protected static ComponentName component(Class<?> componentsClass, String className) {
        if (className.startsWith(".")) {
            throw new AssertionError("Class name should not start with '.'");
        }
        final String packageName = getPackageName(componentsClass);
        final boolean isSimpleClassName = className.indexOf('.') < 0;
        final String fullClassName = isSimpleClassName ? packageName + "." + className : className;
        return new ComponentName(packageName, fullClassName);
    }

    /**
     * Gets the package name from a class named "Components".
     *
     * @param componentsClass the {@code .class} of a class named "Components".
     * @return the package name of the class.
     * @throws AssertionError if the class is not named exactly "Components".
     */
    protected static String getPackageName(Class<?> componentsClass) {
        if (!"Components".equals(componentsClass.getSimpleName())) {
            throw new AssertionError("The class name must be 'Components'");
        }
        return componentsClass.getPackage().getName();
    }
}
