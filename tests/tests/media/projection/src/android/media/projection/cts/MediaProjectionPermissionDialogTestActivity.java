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

package android.media.projection.cts;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/*
 * Test Activity which immediately requests a MediaProjection on startup. Does not actually start
 * a real MediaProjection session, only used to query the MediaProjectionPermissionDialog.
 */
public class MediaProjectionPermissionDialogTestActivity extends Activity {

    static final String EXTRA_MEDIA_PROJECTION_CONFIG = "extra_media_projection_config";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        MediaProjectionManager service = getSystemService(MediaProjectionManager.class);
        MediaProjectionConfig config = intent.hasExtra(EXTRA_MEDIA_PROJECTION_CONFIG)
                ? intent.getParcelableExtra(
                EXTRA_MEDIA_PROJECTION_CONFIG, MediaProjectionConfig.class) : null;
        Intent screenCaptureIntent = service.createScreenCaptureIntent(config);
        startActivityForResult(screenCaptureIntent, 0);
    }
}