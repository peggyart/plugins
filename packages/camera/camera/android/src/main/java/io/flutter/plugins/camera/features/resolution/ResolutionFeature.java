// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera.features.resolution;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.util.Log;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import android.hardware.camera2.CameraManager;
import android.app.Activity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import io.flutter.plugins.camera.CameraProperties;
import io.flutter.plugins.camera.features.CameraFeature;

/**
 * Controls the resolutions configuration on the {@link android.hardware.camera2} API.
 *
 * <p>The {@link ResolutionFeature} is responsible for converting the platform independent {@link
 * ResolutionPreset} into a {@link android.media.CamcorderProfile} which contains all the properties
 * required to configure the resolution using the {@link android.hardware.camera2} API.
 */
public class ResolutionFeature extends CameraFeature<ResolutionPreset> {
  private Size captureSize;
  private Size previewSize;
  private CamcorderProfile recordingProfile;
  private ResolutionPreset currentSetting;
  private int cameraId;

  private final Boolean enableTakePictureWithMaxResolution;
  private CameraCharacteristics cameraCharacteristics;

  /**
   * Creates a new instance of the {@link ResolutionFeature}.
   *
   * @param cameraProperties Collection of characteristics for the current camera device.
   * @param resolutionPreset Platform agnostic enum containing resolution information.
   * @param cameraName Camera identifier of the camera for which to configure the resolution.
   */
  public ResolutionFeature(
          final Activity activity,
      CameraProperties cameraProperties, ResolutionPreset resolutionPreset, String cameraName,
          Boolean enableTakePictureWithMaxResolution) {
    super(cameraProperties);
    this.currentSetting = resolutionPreset;
    this.enableTakePictureWithMaxResolution = enableTakePictureWithMaxResolution;
    CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      this.cameraId = Integer.parseInt(cameraName, 10);
    } catch (NumberFormatException e) {
      this.cameraId = -1;
      return;
    }

    configureResolution(resolutionPreset, cameraId);

    try {
      cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraName);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Gets the {@link android.media.CamcorderProfile} containing the information to configure the
   * resolution using the {@link android.hardware.camera2} API.
   *
   * @return Resolution information to configure the {@link android.hardware.camera2} API.
   */
  public CamcorderProfile getRecordingProfile() {
    return this.recordingProfile;
  }

  /**
   * Gets the optimal preview size based on the configured resolution.
   *
   * @return The optimal preview size.
   */
  public Size getPreviewSize() {
    return this.previewSize;
  }

  /**
   * Gets the optimal capture size based on the configured resolution.
   *
   * @return The optimal capture size.
   */
  public Size getCaptureSize() {
    return this.captureSize;
  }

  @Override
  public String getDebugName() {
    return "ResolutionFeature";
  }

  @Override
  public ResolutionPreset getValue() {
    return currentSetting;
  }

  @Override
  public void setValue(ResolutionPreset value) {
    this.currentSetting = value;
    configureResolution(currentSetting, cameraId);
  }

  @Override
  public boolean checkIsSupported() {
    return cameraId >= 0;
  }

  @Override
  public void updateBuilder(CaptureRequest.Builder requestBuilder) {
    // No-op: when setting a resolution there is no need to update the request builder.
  }

  @VisibleForTesting
  static Size computeBestCaptureSize(StreamConfigurationMap streamConfigurationMap) {
    return Collections.max(
            Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)),
            new CompareSizesByArea());
  }

  /**
   * Gets the best possible {@link android.media.CamcorderProfile} for the supplied {@link
   * ResolutionPreset}.
   *
   * @param cameraId Camera identifier which indicates the device's camera for which to select a
   *     {@link android.media.CamcorderProfile}.
   * @param preset The {@link ResolutionPreset} for which is to be translated to a {@link
   *     android.media.CamcorderProfile}.
   * @return The best possible {@link android.media.CamcorderProfile} that matches the supplied
   *     {@link ResolutionPreset}.
   */
  public static CamcorderProfile getBestAvailableCamcorderProfileForResolutionPreset(
      int cameraId, ResolutionPreset preset) {
    if (cameraId < 0) {
      throw new AssertionError(
          "getBestAvailableCamcorderProfileForResolutionPreset can only be used with valid (>=0) camera identifiers.");
    }

    switch (preset) {
        // All of these cases deliberately fall through to get the best available profile.
      case max:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
        }
      case ultraHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P);
        }
      case veryHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
        }
      case high:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
        }
      case medium:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
        }
      case low:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA);
        }
      default:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
        } else {
          throw new IllegalArgumentException(
              "No capture session available for current capture session.");
        }
    }
  }

  private void configureResolution(ResolutionPreset resolutionPreset, int cameraId) {
    if (!checkIsSupported()) {
      return;
    }
    recordingProfile =
        getBestAvailableCamcorderProfileForResolutionPreset(cameraId, resolutionPreset);
    previewSize = new Size(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight);
    if (enableTakePictureWithMaxResolution) {
      captureSize =
              computeBestCaptureSize(
                      cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP));
      Log.i("Camera", "enableTakePictureWithMaxResolution");
    } else {
      captureSize = new Size(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight);
    }
    Log.i("Camera", "[Preview Resolution] :" + previewSize);
    Log.i("Camera", "[Capture Resolution] :" + captureSize);
  }

  private static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }
}
