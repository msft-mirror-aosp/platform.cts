/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.app.UiAutomation;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Class to hold utilities to run {@link Runnable} with shell permission identity.
 *
 * <p>This is basically same as {@link com.android.compatibility.common.util.SystemUtil}, but we
 * have the same functionality here, so it can be used by AppSearch in g3 as well.
 */
public final class SystemUtil {
    private SystemUtil() {}

    /** Runs a {@link ThrowingRunnable} adopting a subset of Shell's permissions. */
    public static void runWithShellPermissionIdentity(
            @NonNull ThrowingRunnable runnable, String... permissions) {
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        runWithShellPermissionIdentity(automan, runnable, permissions);
    }

    /**
     * Runs a {@link ThrowingRunnable} adopting Shell's permissions, where you can specify the
     * uiAutomation used.
     *
     * @param automan UIAutomation to use.
     * @param runnable The code to run with Shell's identity.
     * @param permissions A subset of Shell's permissions.
     *                    Passing {@code null} will use all available permissions.
     */
    public static void runWithShellPermissionIdentity(
            @NonNull UiAutomation automan,
            @NonNull ThrowingRunnable runnable,
            String... permissions) {
        automan.adoptShellPermissionIdentity(permissions);
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    /**
     * Executes a given shell command using standard Java Runtime.exec() and captures its output.
     *
     * <p>This method safely handles common deadlocks by consuming the error stream (Stderr) in a
     * separate thread and enforcing a timeout.
     *
     * @param command The shell command string to execute (e.g., "dumpsys jobscheduler").
     * @return The standard output (Stdout) of the command as a String.
     * @throws IOException If an I/O error occurs during command execution, stream handling, or
     *     timeout.
     */
    public static String runShellCommandRuntime(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command);

        // 1. Create a separate thread to consume the Error Stream (Stderr).
        // This is critical to prevent the child process from blocking due to a full Stderr buffer.
        Thread errorConsumer =
                new Thread(
                        () -> {
                            try (BufferedReader errorReader =
                                    new BufferedReader(
                                            new InputStreamReader(process.getErrorStream()))) {
                                // Consume and ignore all error stream data.
                                while (errorReader.readLine() != null) {
                                    // Keep reading
                                }
                            } catch (IOException ignored) {
                                // Ignore stream closed or error occurred
                            }
                        });
        errorConsumer.start();

        // 2. Explicitly close the child process's input stream (Stdin).
        // This prevents the child process from hanging while waiting for input.
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Ignore closing failures.
        }

        // 3: Read and capture the standard output (Stdout) line by line.
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                // If the process exceeds the timeout, forcibly terminate it.
                process.destroyForcibly();

                throw new IOException("Shell command did not complete within 2 seconds.");
            }

            // 4. Ensure the error consumer thread finishes its cleanup.
            errorConsumer.join(1000);

            return output.toString();
        } catch (InterruptedException e) {
            // Handle thread interruption while waiting for the process.
            process.destroyForcibly(); // Clean up the hung process.
            errorConsumer.interrupt(); // Interrupt the Stderr consumer.
            Thread.currentThread().interrupt();
            throw new IOException("Shell command interrupted", e);
        }
    }
}
