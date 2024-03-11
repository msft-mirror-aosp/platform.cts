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

package android.graphics.pdf.cts.module;

import static android.graphics.pdf.models.FormEditRecord.EDIT_TYPE_CLICK;
import static android.graphics.pdf.models.FormEditRecord.EDIT_TYPE_SET_INDICES;
import static android.graphics.pdf.models.FormEditRecord.EDIT_TYPE_SET_TEXT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.graphics.Point;
import android.graphics.pdf.models.FormEditRecord;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FormEditRecordTest {

    @Test
    public void testClickType() {
        Point clickPoint = new Point(153, 256);
        FormEditRecord clickRecord = new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_CLICK,
                /* pageNumber= */ 5,
                /* widgetIndex= */ 6).setClickPoint(clickPoint).build();

        // Applicable properties
        assertEquals(EDIT_TYPE_CLICK, clickRecord.getType());
        assertEquals(5, clickRecord.getPageNumber());
        assertEquals(6, clickRecord.getWidgetIndex());
        assertEquals(clickPoint, clickRecord.getClickPoint());

        // Inapplicable properties
        assertEquals(0, clickRecord.getSelectedIndices().length);
        assertNull(clickRecord.getText());
    }

    @Test
    public void testSetIndicesType() {
        int[] selectedIndices = new int[]{12, 13, 15};
        FormEditRecord setIndicesRecord = new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_INDICES,
                /* pageNumber= */ 44,
                /* widgetIndex= */ 13).setSelectedIndices(selectedIndices).build();

        // Applicable properties
        assertEquals(EDIT_TYPE_SET_INDICES, setIndicesRecord.getType());
        assertEquals(44, setIndicesRecord.getPageNumber());
        assertEquals(13, setIndicesRecord.getWidgetIndex());
        assertEquals(selectedIndices, setIndicesRecord.getSelectedIndices());

        // Inapplicable properties
        assertNull(setIndicesRecord.getClickPoint());
        assertNull(setIndicesRecord.getText());
    }

    @Test
    public void testSetTextType() {
        String text = "The quick brown fox jumped over the lazy dog";
        FormEditRecord setTextRecord = new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_TEXT,
                /* pageNumber= */ 0,
                /* widgetIndex= */ 2).setText(text).build();

        // Applicable properties
        assertEquals(EDIT_TYPE_SET_TEXT, setTextRecord.getType());
        assertEquals(0, setTextRecord.getPageNumber());
        assertEquals(2, setTextRecord.getWidgetIndex());
        assertEquals(text, setTextRecord.getText());

        // Inapplicable properties
        assertNull(setTextRecord.getClickPoint());
        assertEquals(0, setTextRecord.getSelectedIndices().length);
    }

    @Test
    public void testBuilder_invalidPageNumber() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_CLICK,
                /* pageNumber= */ -1,
                /* widgetIndex= */ 2));
    }

    @Test
    public void testBuilder_invalidWidgetIndex() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_TEXT,
                /* pageNumber= */ 0,
                /* widgetIndex= */ -20));
    }

    @Test
    public void testBuilder_clickWithoutPoint() {
        assertThrows(NullPointerException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_CLICK,
                /* pageNumber= */ 5,
                /* widgetIndex= */ 2).build());
    }

    @Test
    public void testBuilder_setIndicesWithoutIndices() {
        assertThrows(NullPointerException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_INDICES,
                /* pageNumber= */ 4,
                /* widgetIndex= */ 3).build());
    }

    @Test
    public void testBuilder_setTextWithoutText() {
        assertThrows(NullPointerException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_TEXT,
                /* pageNumber= */ 1,
                /* widgetIndex= */ 1).build());
    }

    @Test
    public void testBuilder_setTextOnClickRecord() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_CLICK,
                /* pageNumber= */ 2,
                /* widgetIndex= */ 5).setText("Foo"));
    }

    @Test
    public void testBuilder_setTextOnSetIndicesRecord() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_INDICES,
                /* pageNumber= */ 21,
                /* widgetIndex= */ 7).setText("Foo"));
    }

    @Test
    public void testBuilder_setIndicesOnClickRecord() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_CLICK,
                /* pageNumber= */ 0,
                /* widgetIndex= */ 3).setSelectedIndices(new int[]{1}));
    }

    @Test
    public void testBuilder_setIndicesOnTextRecord() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_TEXT,
                /* pageNumber= */ 13,
                /* widgetIndex= */ 2).setSelectedIndices(new int[]{13, 15}));
    }

    @Test
    public void testBuilder_setClickPointOnSetIndicesRecord() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_INDICES,
                /* pageNumber= */ 8,
                /* widgetIndex= */ 16).setClickPoint(new Point(12, 12)));
    }

    @Test
    public void testBuilder_setClickPointOnSetTextRecord() {
        assertThrows(IllegalArgumentException.class, () -> new FormEditRecord.Builder(
                /* type= */ EDIT_TYPE_SET_TEXT,
                /* pageNumber= */ 1,
                /* widgetIndex= */ 5).setClickPoint(new Point(220, 140)));
    }
}
