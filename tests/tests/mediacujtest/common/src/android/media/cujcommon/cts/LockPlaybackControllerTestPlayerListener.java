/**
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

package android.media.cujcommon.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;
import androidx.media3.common.Player.PositionInfo;

public class LockPlaybackControllerTestPlayerListener extends PlayerListener {

  private static final int MESSAGE_INTERVAL_MS = 2000;

  private boolean mRewindDone;
  private boolean mIsControllerLocked;

  public LockPlaybackControllerTestPlayerListener(long sendMessagePosition) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
  }

  @Override
  public void onPositionDiscontinuity(PositionInfo oldPosition, PositionInfo newPosition,
      int reason) {
    super.onPositionDiscontinuity(oldPosition, newPosition, reason);
    // Verify that the position has changed due to seek
    assertEquals(Player.DISCONTINUITY_REASON_SEEK, reason);
    // Update the variable if rewind has been executed
    mRewindDone = (newPosition.positionMs < oldPosition.positionMs);
    // Add change in duration due to seek
    mExpectedTotalTime += (mSendMessagePosition - newPosition.positionMs);
  }

  @Override
  public TestType getTestType() {
    return TestType.LOCK_PLAYBACK_CONTROLLER_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (mExpectedTotalTime == 0 && player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
      // Set the controller show timeout to 0 so that controller UI stays eternally
      mActivity.mExoplayerView.setControllerShowTimeoutMs(0);
      mActivity.mExoplayerView.showController();
      activateLockButton(mActivity);
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Rewind by 5 sec
          mActivity.mExoRewindButton.performClick();
        }).setLooper(Looper.getMainLooper())
        .setPosition(mSendMessagePosition)
        .setDeleteAfterDelivery(true)
        .send();
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Verify that the rewind has been executed
          assertTrue(mRewindDone);
          mRewindDone = false;
          // Lock the playback controller
          mActivity.mLockControllerButton.performClick();
        }).setLooper(Looper.getMainLooper())
        .setPosition(mSendMessagePosition + MESSAGE_INTERVAL_MS)
        .setDeleteAfterDelivery(true)
        .send();
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Verify that the controller UI is locked
          assertTrue(mIsControllerLocked);
          // Try to rewind while the controller UI is locked
          mActivity.mExoRewindButton.performClick();
        }).setLooper(Looper.getMainLooper())
        .setPosition(mSendMessagePosition + 2 * MESSAGE_INTERVAL_MS)
        .setDeleteAfterDelivery(true)
        .send();
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Verify that the rewind has not been executed
          assertFalse(mRewindDone);
          // Unlock the playback controller
          mActivity.mLockControllerButton.performClick();
        }).setLooper(Looper.getMainLooper())
        .setPosition(mSendMessagePosition + 3 * MESSAGE_INTERVAL_MS)
        .setDeleteAfterDelivery(true)
        .send();
  }

  /**
   * By default, the lock controller button is invisible. So we need to make it visible and set its
   * onClick listener.
   *
   * @param activity MainActivity
   */
  private void activateLockButton(MainActivity activity) {
    activity.mLockControllerButton.setVisibility(View.VISIBLE);
    activity.mLockControllerButton.setOnClickListener(view -> {
      if (mIsControllerLocked) {
        mIsControllerLocked = false;
        activity.mExoplayerView.setUseController(true);
        activity.mExoplayerView.showController();
      } else {
        mIsControllerLocked = true;
        activity.mExoplayerView.hideController();
        activity.mExoplayerView.setUseController(false);
      }
    });
  }
}
