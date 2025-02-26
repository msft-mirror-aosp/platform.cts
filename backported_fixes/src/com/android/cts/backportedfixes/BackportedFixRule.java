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
package com.android.cts.backportedfixes;

import android.os.Build;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Validates test annotated with {@link BackportedFixTest}.
 *
 * <p>The test will fail if the {@link BackportedFixTest#id()} is not in the list of approved
 * backported fixes.
 *
 * <p>Other test failures will be changed to an assumption failures if
 * {@link Build#getBackportedFixStatus(long)} returns false.
 */
public class BackportedFixRule implements TestRule {
    private final ApprovedBackportedFixes mFixes = ApprovedBackportedFixes.getInstance();

    // TODO: make host version of this.

    @Override
    public Statement apply(Statement statement, Description description) {
        BackportedFixTest issue = description.getAnnotation(BackportedFixTest.class);
        if (issue == null) {
            return statement;
        }
        int alias = mFixes.getAlias(issue.value());
        if (!mFixes.getAllIssues().contains(issue.value())) {
            throw new IllegalStateException(
                    "https://issuetracker.google.com/issues/%d is not an approved backported fix."
                            .formatted(issue.value()));
        }
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    statement.evaluate();
                } catch (AssertionError e) {


                    if (Build.getBackportedFixStatus(alias)
                            == Build.BACKPORTED_FIX_STATUS_FIXED) {
                        throw e;
                    }
                    throw new AssumptionViolatedException(
                            ("https://issuetracker.google.com/issues/%d with alias %d is not "
                                    + "marked fixed on this device.")
                                    .formatted(issue.value(), alias), e);
                }
            }
        };
    }
}
