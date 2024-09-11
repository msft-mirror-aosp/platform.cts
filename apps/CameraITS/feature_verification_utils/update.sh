#!/bin/bash

if [[ -z "${ANDROID_BUILD_TOP}" ]]; then
  echo "ANDROID_BUILD_TOP not set. Please run `source build/envsetup.sh`"
  exit 1
fi

if [[ -z "${ANDROID_PRODUCT_OUT}" ]]; then
  echo "lunch target is not set!"
  exit 1
fi

CAMERA_ITS_TOP=${ANDROID_BUILD_TOP}/cts/apps/CameraITS
echo "CAMERA_ITS_TOP=${CAMERA_ITS_TOP}"

m -j feature_verification_test 2>&1 || exit 1

PB2PATH=out/soong/.intermediates/cts/apps/CameraITS/feature_verification_utils/feature_combination_proto/linux_glibc_x86_64_PY3/gen
if ! [[ -r "${ANDROID_BUILD_TOP}/${PB2PATH}/feature_combination_info.proto.srcszip" ]]; then
  echo "feature_combination_info.proto.srcszip not generated!"
  exit 1
fi

unzip -o ${ANDROID_BUILD_TOP}/${PB2PATH}/feature_combination_info.proto.srcszip -d ${CAMERA_ITS_TOP}/feature_verification_utils/
