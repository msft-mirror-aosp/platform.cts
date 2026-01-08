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
package android.app.fgstesthelper;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;

/** Utility methods for Foreground Service (FGS) test cases. */
public final class FgsTestHelper {

    /**
     * Navigates to the Home screen on a specific display.
     *
     * <p>On Android systems supporting Visible Background Users, such as Android Automotive with
     * Multiple Users on Multiple Displays (MUMD), multiple users can be active simultaneously. In
     * these environments, passengers are secondary users running on secondary displays. To
     * correctly navigate to the Home screen for the specific test user, the Home Activity must be
     * started on the corresponding display by passing the correct display ID via {@link
     * ActivityOptions}.
     *
     * @param context the context used to start the activity
     * @param displayId the target display ID where the Home Activity should be launched
     */
    public static void navigateToHome(Context context, int displayId) {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        context.startActivity(homeIntent, options.toBundle());
    }

    private FgsTestHelper() {}
}
