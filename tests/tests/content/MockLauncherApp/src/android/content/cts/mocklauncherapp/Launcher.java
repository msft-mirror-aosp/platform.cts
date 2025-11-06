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
package android.content.cts.mocklauncherapp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class Launcher extends Activity {
    private static final String TAG = "mocklauncherapp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File cacheDir = getApplicationContext().getCacheDir();
        File myCacheFile = new File(cacheDir, "my_cached_data.txt");
        createEmptyFileOfSize(myCacheFile, 4096 * 10);
    }

    private static void createEmptyFileOfSize(File file, long sizeInBytes) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                FileChannel channel = raf.getChannel()) {
            raf.setLength(sizeInBytes);
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            for (long i = 0; i < sizeInBytes; i += buffer.capacity()) {
                channel.write(buffer, i);
            }
            channel.force(true);
            Log.i(TAG, "created cache file " + file.getName() + " of " + sizeInBytes + " bytes");
        } catch (IOException e) {
            Log.e(TAG, "Failed to create empty file of size " + sizeInBytes
                    + " at " + file.getAbsolutePath(), e);
        }
    }
}
