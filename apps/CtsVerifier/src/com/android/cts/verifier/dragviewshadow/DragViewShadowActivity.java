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
package com.android.cts.verifier.dragviewshadow;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * This activity verifies the correct behavior of a custom drag shadow during a drag-and-drop. It
 * allows the user to drag a 'draggableContainer' and observes its overlap with a
 * 'dropTargetContainer'. The drag shadow is custom drawn in grayscale and partially transparent.
 */
public class DragViewShadowActivity extends PassFailButtons.Activity {

    // UI elements
    private TextView mInstructionsTextView;
    private FrameLayout mDragContainer;
    private FrameLayout mDropTargetContainer;

    // Initial position and parent of the draggable view
    private ViewGroup mInitialParent;
    private float mInitialDraggableX;
    private float mInitialDraggableY;

    // Current position of the drag shadow
    private float mCurrentDragX;
    private float mCurrentDragY;

    /** Defines the containment state of the drag shadow within the drop target. */
    private enum DragState {
        NO_OVERLAP, // No significant overlap with the drop target
        PARTIAL_OVERLAP, // Partial overlap (less than 70%)
        SUFFICIENT_OVERLAP // Sufficient overlap (70% or more)
    }

    private DragState mCurrentDragState;

    private static final String TAG = "DragViewShadowActivity"; // Tag for logging
    private static final float SUFFICIENT_OVERLAP_THRESHOLD = 0.7f; // 70% overlap

    /* Called when the activity is first created.*/
    @SuppressLint("ClickableViewAccessibility") // Suppress lint warning for setOnTouchListener
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.drag_view_shadow_test_layout);

        // Initialize UI components
        mInstructionsTextView = findViewById(R.id.instructions_text);
        mDragContainer = findViewById(R.id.draggable_container);
        mDropTargetContainer = findViewById(R.id.drop_target_container);

        // Set initial background color for the drop target and drag state
        mDropTargetContainer.setBackgroundColor(Color.GRAY);
        mCurrentDragState = DragState.NO_OVERLAP;

        // Set Z-order to ensure draggable container is on top
        mDragContainer.setZ(1f);
        mDropTargetContainer.setZ(0f);

        // Post a runnable to get initial position after layout is complete
        mDragContainer.post(() -> {
            mInitialDraggableX = mDragContainer.getX();
            mInitialDraggableY = mDragContainer.getY();
            mInitialParent = (ViewGroup) mDragContainer.getParent();

            // Initialize current drag position to initial position
            mCurrentDragX = mInitialDraggableX;
            mCurrentDragY = mInitialDraggableY;

            // Set a global drag listener on the parent view to track drag events
            if (mInitialParent != null) {
                mInitialParent.setOnDragListener(
                        new View.OnDragListener() {
                            @Override
                            public boolean onDrag(View v, DragEvent event) {
                                int action = event.getAction();
                                switch (action) {
                                    case DragEvent.ACTION_DRAG_STARTED:
                                        Log.d(TAG, "Global: ACTION_DRAG_STARTED");
                                        // Indicate that the listener can accept the drag
                                        return true;

                                    case DragEvent.ACTION_DRAG_ENDED:
                                        Log.d(TAG, "Global: ACTION_DRAG_ENDED. Result: "
                                                + event.getResult());
                                        // Reposition the draggable container to the last known
                                        // drag location
                                        mDragContainer.setX(
                                                mCurrentDragX - (mDragContainer.getWidth()
                                                        / 2));
                                        mDragContainer.setY(
                                                mCurrentDragY - (mDragContainer.getHeight()
                                                        / 2));

                                        // Check the final drag state and provide feedback
                                        if (mCurrentDragState == DragState.PARTIAL_OVERLAP) {
                                            Toast.makeText(
                                                            DragViewShadowActivity.this,
                                                            R.string.drag_view_shadow_toast_remind,
                                                            Toast.LENGTH_LONG)
                                                    .show();
                                            mCurrentDragState = DragState.NO_OVERLAP;
                                        } else if (mCurrentDragState
                                                == DragState.SUFFICIENT_OVERLAP) {
                                            showShadowConfirmationDialog(); // Prompt user for
                                            // confirmation
                                            mCurrentDragState = DragState.NO_OVERLAP;
                                        }
                                        return true;

                                    case DragEvent.ACTION_DRAG_LOCATION:
                                        // Update the last known drag location and check overlap
                                        mCurrentDragX = event.getX();
                                        mCurrentDragY = event.getY();
                                        checkShadowOverlap(mCurrentDragX, mCurrentDragY);
                                        return true;

                                    case DragEvent.ACTION_DRAG_ENTERED:
                                    case DragEvent.ACTION_DRAG_EXITED:
                                        return true;
                                    case DragEvent.ACTION_DROP:
                                        return true;
                                    default:
                                        break;
                                }
                                return false; // Event not consumed
                            }
                        });
            }
        });

        // Set a touch listener on the draggable container to initiate drag-and-drop
        mDragContainer.setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        // Create ClipData for the drag operation (not strictly used for this
                        // test's data)
                        ClipData dragData = ClipData.newPlainText("", "Drag Shadow Test");
                        // Create and set the custom drag shadow builder
                        View.DragShadowBuilder customShadowBuilder =
                                new CustomDragShadowBuilder(mDragContainer);
                        // Start the drag-and-drop operation
                        v.startDragAndDrop(dragData, customShadowBuilder, null, 0);
                        return true; // Consume the touch event
                    }
                });

        // Set instructions text
        mInstructionsTextView.setText(getString(R.string.drag_view_shadow_instructions));

        // Set up the pass/fail buttons
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false); // Pass button is initially disabled
    }

    /**
     * Checks the overlap between the drag shadow and the drop target container. Updates the drop
     * target's background color based on the overlap percentage.
     *
     * @param dropX The X coordinate of the drag shadow's touch point relative to its parent.
     * @param dropY The Y coordinate of the drag shadow's touch point relative to its parent.
     */
    private void checkShadowOverlap(float dropX, float dropY) {
        // Get the absolute position of the initial parent view
        int[] parentLocationOnScreen = new int[2];
        mInitialParent.getLocationInWindow(parentLocationOnScreen);

        // Calculate the absolute coordinates of the drag shadow (draggable container)
        float absoluteShadowCenterX = dropX + parentLocationOnScreen[0];
        float absoluteShadowCenterY = dropY + parentLocationOnScreen[1];

        // Create a Rect representing the drag shadow's bounds
        Rect draggableShadowRect =
                new Rect(
                        (int) (absoluteShadowCenterX - (mDragContainer.getWidth() / 2)),
                        (int) (absoluteShadowCenterY - (mDragContainer.getHeight() / 2)),
                        (int) (absoluteShadowCenterX + (mDragContainer.getWidth() / 2)),
                        (int) (absoluteShadowCenterY + (mDragContainer.getHeight() / 2)));

        // Get the absolute position of the drop target container
        int[] dropTargetLocationOnScreen = new int[2];
        mDropTargetContainer.getLocationInWindow(dropTargetLocationOnScreen);

        // Create a Rect representing the drop target's bounds
        Rect dropTargetBounds =
                new Rect(
                        dropTargetLocationOnScreen[0],
                        dropTargetLocationOnScreen[1],
                        dropTargetLocationOnScreen[0] + mDropTargetContainer.getWidth(),
                        dropTargetLocationOnScreen[1] + mDropTargetContainer.getHeight());

        // Calculate the intersection of the two rectangles
        Rect intersectionAreaRect = new Rect();
        boolean intersects = intersectionAreaRect.setIntersect(draggableShadowRect,
                dropTargetBounds);

        // If there's no intersection, set state to NO_CONTAINED and reset drop target color
        if (!intersects) {
            mCurrentDragState = DragState.NO_OVERLAP;
            mDropTargetContainer.setBackgroundColor(Color.GRAY);
            return;
        }

        // Calculate the total area of the draggable shadow and the intersection area
        float draggableShadowArea = mDragContainer.getWidth() * mDragContainer.getHeight();
        float intersectionArea = intersectionAreaRect.width() * intersectionAreaRect.height();

        // Determine the containment state based on overlap percentage
        if (draggableShadowArea > 0
                && (intersectionArea / draggableShadowArea) >= SUFFICIENT_OVERLAP_THRESHOLD) {
            mCurrentDragState = DragState.SUFFICIENT_OVERLAP;
            mDropTargetContainer.setBackgroundColor(Color.LTGRAY);
        } else {
            mCurrentDragState = DragState.PARTIAL_OVERLAP;
            mDropTargetContainer.setBackgroundColor(Color.LTGRAY);
        }
    }

    /**
     * Shows a confirmation dialog to the user after a successful drag operation, allowing them to
     * confirm if the shadow was correctly displayed.
     */
    private void showShadowConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.drag_view_shadow_dialog_title);
        builder.setMessage(R.string.drag_view_shadow_dialog_content);
        builder.setPositiveButton(
                R.string.drag_view_shadow_dialog_positive,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // If user confirms correct shadow, show pass toast and enable pass button
                        Toast.makeText(
                                        DragViewShadowActivity.this,
                                        R.string.drag_view_shadow_toast_pass,
                                        Toast.LENGTH_LONG)
                                .show();
                        // Disable further dragging and change draggable container's background
                        mDragContainer.setOnTouchListener(null);
                        mDragContainer.setBackgroundColor(Color.GRAY);
                        setTestResultAndFinish(true);
                    }
                });
        builder.setNegativeButton(
                R.string.drag_view_shadow_dialog_negative,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // If user confirms correct shadow, show pass toast and enable pass button
                        Toast.makeText(
                                        DragViewShadowActivity.this,
                                        R.string.drag_view_shadow_toast_fail,
                                        Toast.LENGTH_LONG)
                                .show();
                        // Disable further dragging and change draggable container's background
                        mDragContainer.setOnTouchListener(null);
                        mDragContainer.setBackgroundColor(Color.GRAY);
                        setTestResultAndFinish(false);
                    }
                });
        builder.setNeutralButton(
                R.string.drag_view_shadow_dialog_neutral,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // If user denies, reset draggable view position and drop target color
                        mDragContainer.setX(mInitialDraggableX);
                        mDragContainer.setY(mInitialDraggableY);
                        mDropTargetContainer.setBackgroundColor(Color.GRAY);
                    }
                });
        builder.setCancelable(false); // Prevent dialog dismissal by back button
        builder.show();
    }

    /**
     * Custom implementation of View.DragShadowBuilder to draw a gray and partially transparent
     * shadow of the draggable view.
     */
    private static class CustomDragShadowBuilder extends View.DragShadowBuilder {
        private final View mViewToDrag; // The view for which the shadow is created

        /**
         * Constructor for the CustomDragShadowBuilder.
         *
         * @param view The view to create the drag shadow from.
         */
        CustomDragShadowBuilder(View view) {
            super(view); // Call the super constructor
            this.mViewToDrag = view;
        }

        /**
         * Called to provide the dimensions and touch point for the drag shadow.
         * The shadow will be the
         * same size as the original view, and the touch point will be in the center of the shadow.
         *
         * @param shadowSize A Point object to write the dimensions of the shadow to.
         * @param shadowTouchPoint A Point object to write the touch point coordinates to.
         */
        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            int width = mViewToDrag.getWidth();
            int height = mViewToDrag.getHeight();

            shadowSize.set(width, height); // Set shadow size to match the view
            shadowTouchPoint.set(width / 2, height / 2); // Set touch point to the center
        }

        /**
         * Called to draw the drag shadow onto the provided canvas.
         * The shadow is drawn as a grayscale,
         * partially transparent version of the original view.
         *
         * @param canvas The Canvas on which to draw the drag shadow.
         */
        @Override
        public void onDrawShadow(Canvas canvas) {
            // Create a bitmap to render the original view onto
            Bitmap bitmap =
                    Bitmap.createBitmap(
                            mViewToDrag.getWidth(), mViewToDrag.getHeight(),
                            Bitmap.Config.ARGB_8888);
            Canvas tempCanvas = new Canvas(bitmap);

            // Draw the original view onto the temporary canvas (which is backed by the bitmap)
            mViewToDrag.draw(tempCanvas);

            // Create a ColorMatrix to convert to grayscale
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0); // Set saturation to 0 for grayscale
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);

            // Create a Paint object with the grayscale filter and alpha for transparency
            Paint greyscalePaint = new Paint();
            greyscalePaint.setColorFilter(filter);
            greyscalePaint.setAlpha(180); // Set alpha for partial transparency (0-255)

            // Draw the grayscale and transparent bitmap onto the provided drag shadow canvas
            canvas.drawBitmap(bitmap, 0, 0, greyscalePaint);
        }
    }
}
