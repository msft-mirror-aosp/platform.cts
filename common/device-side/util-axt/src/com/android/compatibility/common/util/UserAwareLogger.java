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
package com.android.compatibility.common.util;

import static android.os.UserHandle.USER_NULL;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Locale;
import java.util.Objects;

/** Helper class that logs with string formatting, including the Android user in the logs. */
public final class UserAwareLogger {

    private final String mTag;
    private final @Nullable String mSubTag;
    private final @UserIdInt int mUserId;

    /**
     * Bob, the builder!
     *
     * <p>By default, uses the {@link UserHandle#myUserId() user id of the current process}.
     */
    public static final class Builder {
        private final String mTag;
        private @Nullable String mSubTag;
        private @UserIdInt int mUserId = USER_NULL;

        private Builder(String tag) {
            mTag = tag;
        }

        /** Sets a {@code subTag} (typically the name of a subclass) to be logged on each line. */
        public Builder setSubTag(String subTag) {
            mSubTag = Objects.requireNonNull(subTag, "subTag cannot be null");
            return this;
        }

        /** Explicitly sets the user id that will be logged. */
        public Builder setUserId(@UserIdInt int userId) {
            if (userId == USER_NULL) {
                throw new IllegalArgumentException("userId cannot be USER_NULL");
            }
            mUserId = userId;
            return this;
        }

        /** Explicitly sets the user id that will be logged. */
        public Builder setUser(UserHandle user) {
            Objects.requireNonNull(user, "user cannot be null");
            return setUserId(user.getIdentifier());
        }

        /** Sets the user it that will be logged as the {@link Context#getUser() context user}. */
        public Builder setUser(Context context) {
            Objects.requireNonNull(context, "context cannot be null");
            return setUser(context.getUser());
        }

        /** Can we build it? Yes we can! */
        public UserAwareLogger build() {
            return new UserAwareLogger(this);
        }
    }

    /** Creates a builder for the given {@code tag}. */
    public static Builder newBuilder(String tag) {
        Objects.requireNonNull(tag, "tag cannot be null");
        return new Builder(tag);
    }

    private UserAwareLogger(Builder builder) {
        mTag = builder.mTag;
        mSubTag = builder.mSubTag;
        mUserId = builder.mUserId == USER_NULL ? UserHandle.myUserId() : builder.mUserId;
    }

    /** Gets the user ID. */
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
        var string = new StringBuilder("UserAwareLogger[tag=").append(mTag);
        if (mSubTag != null) {
            string.append(", subTag=").append(mSubTag);
        }

        return string.append(", userId=").append(mUserId).append("]").toString();
    }

    @FormatMethod
    private void log(int level, @FormatString String fmt, @Nullable Object... args) {
        StringBuilder builder = new StringBuilder("[");
        if (mSubTag != null) {
            builder.append(mSubTag).append(", ");
        }
        String msg = builder.append("userId=").append(mUserId).append("]: ")
                .append(String.format(Locale.ENGLISH, fmt, args)).toString();
        switch (level) {
            case Log.ERROR -> Log.e(mTag, msg);
            case Log.WARN -> Log.w(mTag, msg);
            case Log.INFO -> Log.i(mTag, msg);
            case Log.DEBUG -> Log.d(mTag, msg);
            case Log.VERBOSE -> Log.v(mTag, msg);
        }
    }
}
