/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.utils;

import com.android.bedstead.nene.exceptions.AdbException;

import java.util.function.Function;

/**
 * A tool for progressively building and then executing a shell command.
 */
public final class ShellCommand {

    public static Builder builder(String command) {
        if (command == null) {
            throw new NullPointerException();
        }
        return new Builder(command);
    }

    public static final class Builder {
        private final StringBuilder commandBuilder;

        private Builder(String command) {
            commandBuilder = new StringBuilder(command);
        }

        /**
         * Add an option to the command.
         *
         * <p>e.g. --user 10
         */
        public Builder addOption(String key, Object value) {
            // TODO: Deal with spaces/etc.
            commandBuilder.append(" ").append(key).append(" ").append(value);
            return this;
        }

        /**
         * Add an operand to the command.
         */
        public Builder addOperand(Object value) {
            // TODO: Deal with spaces/etc.
            commandBuilder.append(" ").append(value);
            return this;
        }

        /**
         * Build the full command including all options and operands.
         */
        public String build() {
            return commandBuilder.toString();
        }

        /** See {@link ShellCommandUtils#executeCommand(java.lang.String)}. */
        public String execute() throws AdbException {
            return ShellCommandUtils.executeCommand(commandBuilder.toString());
        }

        /** See {@link ShellCommandUtils#executeCommandAndValidateOutput(String, Function)}. */
        public String executeAndValidateOutput(Function<String, Boolean> outputSuccessChecker)
                throws AdbException {
            return ShellCommandUtils.executeCommandAndValidateOutput(
                    commandBuilder.toString(), outputSuccessChecker);
        }
    }
}
