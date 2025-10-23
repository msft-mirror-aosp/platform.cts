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
package com.android.cts.backportedfixes;

import com.android.cts.backportedfixes.resolver.Status;
import com.android.cts.backportedfixes.resolver.StatusResolver;

import java.util.Locale;

/** Verifies the status of a known issue(KI) that has a backported fix. */
public final class BackportedFixVerifier {

    private final ApprovedBackportedFixes mFixes = ApprovedBackportedFixes.getInstance();
    private final StatusResolver mStatusResolver;

    /** Creates a verifier that uses the default resolver. */
    public BackportedFixVerifier() {
        this(StatusResolver.create());
    }

    /**
     * Creates a verifier that uses the given resolver.
     *
     * @param statusResolver the resolver to use to get the status of the backported fix.
     */
    public BackportedFixVerifier(StatusResolver statusResolver) {
        mStatusResolver = statusResolver;
    }

    /**
     * Returns true if the issue is in the list of approved backported fixes.
     *
     * <p>See the list of approved backported fixes in <a
     * href="https://cs.android.com/android/platform/superproject/+/android-latest-release:cts
     * /backported_fixes/approved"> cts/backported_fixes/approved</a> directory.
     */
    public boolean isApproved(long issueId) {
        return mFixes.getAllIssues().contains(issueId);
    }

    /**
     * Returns the alias of the backported fix with the given issue ID or 0 if the issue is not in
     * the list of approved backported fixes.
     */
    public long getAlias(long issueId) {
        return mFixes.getAlias(issueId);
    }

    /**
     * Returns the status of the backported fix with the given issue ID.
     *
     * @throws IllegalStateException if the issue not in the list of approved backported fixes. See
     *     {@link #isApproved(long)}
     */
    public Status getStatus(long issueId) throws IllegalStateException {
        if (!isApproved(issueId)) {
            String msg =
                    String.format(
                            Locale.ROOT,
                            "https://issuetracker.google.com/issues/%d is not an approved"
                                    + " backported fix.",
                            issueId);
            throw new IllegalStateException(msg);
        }
        int alias = mFixes.getAlias(issueId);
        return mStatusResolver.getBackportedFixStatus(alias);
    }
}
