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

package android.telecom.cts.apps;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class IntegerTransaction extends BaseTransaction implements Parcelable {

  private int mIntegerResult;

  public int getIntegerResult() {
    return mIntegerResult;
  }

  public IntegerTransaction(TestAppTransaction result, int integerResult) {
    mResult = result;
    mIntegerResult = integerResult;
    mException = null;
  }

  public IntegerTransaction(TestAppTransaction result, TestAppException exception) {
    mResult = result;
    mException = exception;
    mIntegerResult = -1;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeParcelable(mResult, flags);
    if (isTransactionSuccessful()) {
      dest.writeInt(mIntegerResult);
    } else {
      dest.writeParcelable(mException, flags);
    }
  }

  public static final Creator<IntegerTransaction> CREATOR = new Creator<>() {
    @Override
    public IntegerTransaction createFromParcel(Parcel source) {
      TestAppTransaction transactionResult =
          source.readParcelable(getClass().getClassLoader(), TestAppTransaction.class);

      if (transactionResult != null
          && transactionResult.equals(TestAppTransaction.Success)) {
        return new IntegerTransaction(
            transactionResult,
            source.readInt());
      } else {
        return new IntegerTransaction(
            transactionResult,
            source.readParcelable(getClass().getClassLoader(), TestAppException.class));
      }
    }

    @Override
    public IntegerTransaction[] newArray(int size) {
      return new IntegerTransaction[size];
    }
  };
}
