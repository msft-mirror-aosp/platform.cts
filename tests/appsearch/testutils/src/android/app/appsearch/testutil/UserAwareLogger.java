/*
 * Copyright 2025 The Android Open Source Project
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
package android.app.appsearch.testutil;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.os.UserHandle;
import android.util.Log;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Locale;
import java.util.Objects;

/** Helper class that logs with string formatting, including the Android user in the logs. */
public final class UserAwareLogger {

    private final String mTag;
    private final @UserIdInt int mUserId;

    /** Creates a logger for the given {@code tag} and {@code userId} */
    public UserAwareLogger(String tag, @UserIdInt int userId) {
        mTag = Objects.requireNonNull(tag, "tag cannot be null");
        mUserId = userId;
    }

    /** Creates a logger for the given {@code tag} and {@code user} */
    public UserAwareLogger(String tag, UserHandle user) {
        this(tag, Objects.requireNonNull(user, "user cannot be null").getIdentifier());
    }

    /** Gets the user id. */
    public @UserIdInt int getUserId() {
        return mUserId;
    }

    /** Logs an error message. */
    @FormatMethod
    public void logE(@FormatString String fmt, @Nullable Object... args) {
        log(Log.ERROR, fmt, args);
    }

    /** Logs a warning message. */
    @FormatMethod
    public void logW(@FormatString String fmt, @Nullable Object... args) {
        log(Log.WARN, fmt, args);
    }

    /** Logs an info message. */
    @FormatMethod
    public void logI(@FormatString String fmt, @Nullable Object... args) {
        log(Log.INFO, fmt, args);
    }

    /** Logs a debug message. */
    @FormatMethod
    public void logD(@FormatString String fmt, @Nullable Object... args) {
        log(Log.DEBUG, fmt, args);
    }

    /** Logs a verbose message. */
    @FormatMethod
    public void logV(@FormatString String fmt, @Nullable Object... args) {
        log(Log.VERBOSE, fmt, args);
    }

    @Override
    public String toString() {
        return "UserAwareLogger[tag=" + mTag + ", userId=" + mUserId + "]";
    }

    @FormatMethod
    private void log(int level, @FormatString String fmt, @Nullable Object... args) {
        String msg = "(userId=" + mUserId + "): " + String.format(Locale.ENGLISH, fmt, args);
        switch (level) {
            case Log.ERROR -> Log.e(mTag, msg);
            case Log.WARN -> Log.w(mTag, msg);
            case Log.INFO -> Log.i(mTag, msg);
            case Log.DEBUG -> Log.d(mTag, msg);
            case Log.VERBOSE -> Log.v(mTag, msg);
        }
    }
}
