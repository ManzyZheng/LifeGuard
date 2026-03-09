package com.example.firstaid.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class PoseDetectorHelper {
    public interface Listener {
        void onChestDetected(float chestX, float chestY);

        void onPoseMissing();

        void onError(String message);
    }

    private static final String TAG = "PoseDetectorHelper";
    private static final int LEFT_SHOULDER_INDEX = 11;
    private static final int RIGHT_SHOULDER_INDEX = 12;
    private static final String MODEL_ASSET_PATH = "pose_landmarker_lite.task";

    private final Listener listener;
    private PoseLandmarker poseLandmarker;

    public PoseDetectorHelper(@NonNull Context context, @NonNull Listener listener) {
        this.listener = listener;
        initPoseLandmarker(context.getApplicationContext());
    }

    private void initPoseLandmarker(Context context) {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .build();

            PoseLandmarker.PoseLandmarkerOptions options =
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setMinPoseDetectionConfidence(0.5f)
                            .setMinTrackingConfidence(0.5f)
                            .setMinPosePresenceConfidence(0.5f)
                            .setNumPoses(1)
                            .setRunningMode(RunningMode.IMAGE)
                            .build();
            poseLandmarker = PoseLandmarker.createFromOptions(context, options);
        } catch (Exception e) {
            Log.e(TAG, "initPoseLandmarker failed", e);
            listener.onError("Pose模型初始化失败，请确认 assets 中存在 " + MODEL_ASSET_PATH);
        }
    }

    public void detectFromImageProxy(@NonNull ImageProxy imageProxy) {
        try {
            if (poseLandmarker == null) {
                listener.onPoseMissing();
                return;
            }

            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                listener.onPoseMissing();
                return;
            }
            Bitmap rotated = rotateBitmap(bitmap, imageProxy.getImageInfo().getRotationDegrees());
            MPImage mpImage = new BitmapImageBuilder(rotated).build();
            PoseLandmarkerResult result = poseLandmarker.detect(mpImage);
            handlePoseResult(result);
        } catch (Exception e) {
            Log.e(TAG, "detectFromImageProxy failed", e);
            listener.onPoseMissing();
        } finally {
            imageProxy.close();
        }
    }

    private void handlePoseResult(PoseLandmarkerResult result) {
        if (result == null || result.landmarks().isEmpty()) {
            listener.onPoseMissing();
            return;
        }
        List<NormalizedLandmark> landmarks = result.landmarks().get(0);
        if (landmarks.size() <= RIGHT_SHOULDER_INDEX) {
            listener.onPoseMissing();
            return;
        }

        NormalizedLandmark leftShoulder = landmarks.get(LEFT_SHOULDER_INDEX);
        NormalizedLandmark rightShoulder = landmarks.get(RIGHT_SHOULDER_INDEX);
        float chestX = (leftShoulder.x() + rightShoulder.x()) * 0.5f;
        float chestY = (leftShoulder.y() + rightShoulder.y()) * 0.5f;

        if (Float.isNaN(chestX) || Float.isNaN(chestY)) {
            listener.onPoseMissing();
            return;
        }
        chestX = clamp01(chestX);
        chestY = clamp01(chestY);
        listener.onChestDetected(chestX, chestY);
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) {
            return null;
        }

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 85, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private Bitmap rotateBitmap(Bitmap source, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return source;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public void release() {
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
    }
}
