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
package android.content.pm.cts.shortcutmanager.packages;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.annotation.Nullable;

public class RequestPinShortcutActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("RequestPinShortcutActivity", "activity started");
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        assertTrue(shortcutManager.isRequestPinShortcutSupported());
        PersistableBundle extras = new PersistableBundle();
        extras.putString(Constants.EXTRA_REPLY_ACTION,
            getIntent().getStringExtra(Constants.EXTRA_REPLY_ACTION));
        extras.putString(Constants.LABEL, "Bal Test Shortcut");
        final ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "bal_test_shortcut")
            .setShortLabel("Bal Test Shortcut")
            .setExtras(extras)
            .setIntent((new Intent(Intent.ACTION_VIEW)).setData(Uri.parse("https://google.com")))
            .build();
        IntentSender pinCallback = getIntent().getParcelableExtra(Constants.EXTRA_TARGET_INTENT,
           IntentSender.class);
        shortcutManager.requestPinShortcut(shortcut, pinCallback);
        Log.i("RequestPinShortcutActivity", "requested pin shortcut");
        finish();
    }
}
