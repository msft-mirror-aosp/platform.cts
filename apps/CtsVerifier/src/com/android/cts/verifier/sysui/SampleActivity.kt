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

package com.android.cts.verifier.sysui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import com.android.cts.verifier.R

class SampleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sysui_sample_activity)

        val rootContainer = findViewById<View>(R.id.root_container)
        rootContainer.background = createTiledLatticeDrawable()
    }

    /**
     * Creates a BitmapDrawable that repeats a distinguishable small lattice pattern. This is needed
     * to make it obvious to the tester which elements on screen are drawn by the test app, and
     * which are drawn by the SysUI.
     */
    private fun createTiledLatticeDrawable(): BitmapDrawable {
        val tileSizePx = 16
        val lineThicknessPx = 4f
        val backgroundColor = Color.parseColor("#6A0DAD")

        val tileBitmap = Bitmap.createBitmap(tileSizePx, tileSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tileBitmap)
        canvas.drawColor(backgroundColor)

        val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = lineThicknessPx
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        canvas.drawLine(0f, 0f, tileSizePx.toFloat(), tileSizePx.toFloat(), paint)
        canvas.drawLine(0f, tileSizePx.toFloat(), tileSizePx.toFloat(), 0f, paint)

        val tiledDrawable = BitmapDrawable(resources, tileBitmap)
        tiledDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        return tiledDrawable
    }
}
