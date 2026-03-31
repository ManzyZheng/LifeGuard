package com.example.firstaid.logic;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import com.example.firstaid.model.RiskLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Locale;

/**
 * ML-based fall detector using the ONNX FP32 model (risk_model.onnx).
 * Note: INT8 dynamic-quantized model (risk_model_int8.onnx) is NOT used because
 * ONNX Runtime Android does not support the ConvInteger operator it generates.
 *
 * Pipeline:
 *   1. Collect accel + gyro at 20 Hz via a 50 ms Handler tick
 *   2. Maintain a 5-second sliding window (100 samples × 6 channels)
 *   3. Every 10 new samples (0.5 s) run ONNX inference → risk_score [0-100] + confidence [0-1]
 *   4. EMA-smooth outputs (α = 0.3)
 *   5. AlertDetector state machine → LOW / MEDIUM / HIGH risk
 *   6. Invoke RiskCallback on main thread (same interface as SensorFusionManager)
 *
 * Scaler constants are hard-coded from MobiAct/outputs/scaler.npz (mean/std per channel).
 * AlertDetector thresholds match MobiAct/infer.py exactly.
 */
public class OnnxFallDetector implements SensorEventListener {

    private static final String TAG = "OnnxFallDetector";

    // ── Public callback interface (same signature as SensorFusionManager.RiskCallback) ──
    public interface RiskCallback {
        void onRiskUpdated(RiskLevel level, int score, String monitorState, String suggestion);
    }

    // ── Scaler constants: Z-score per channel (ax, ay, az, gx, gy, gz) ──────────────────
    // Loaded from MobiAct/outputs/scaler.npz  shape (6,1) → broadcast over timesteps
    private static final float[] MEAN = {
            0.16108017f,  7.8491607f,  1.2381543f,
            -0.00887468f, 0.00809547f, -0.00928215f
    };
    private static final float[] STD = {
            3.7698333f, 5.032198f,  3.8375442f,
            1.1406645f, 1.0059208f, 0.7560915f
    };

    // ── Model / window parameters (must match training config) ──────────────────────────
    private static final int  WINDOW_SIZE     = 100;    // 5 s × 20 Hz
    private static final int  N_CHANNELS      = 6;      // ax ay az gx gy gz
    private static final int  INFER_STEP      = 10;     // infer every 10 samples = 0.5 s
    private static final long SAMPLE_PERIOD_MS = 50L;   // 20 Hz tick

    // ── Alert thresholds (from MobiAct/infer.py) ────────────────────────────────────────
    private static final float SCORE_THRESHOLD  = 70f;
    private static final float CONF_THRESHOLD   = 0.7f;
    private static final int   L1_WINDOWS       = 2;    // 1.0 s → LOW  (trigger once)
    private static final int   L2_WINDOWS       = 4;    // 2.0 s → MEDIUM (trigger once)
    private static final int   L3_WINDOWS       = 6;    // 3.0 s → HIGH (continuous)
    private static final int   CANCEL_COOLDOWN  = 60;   // 30 s after user cancel

    // ── EMA ─────────────────────────────────────────────────────────────────────────────
    private static final float EMA_ALPHA = 0.3f;

    // ── Android sensor / threading ──────────────────────────────────────────────────────
    private final SensorManager sensorManager;
    private final Sensor        accelerometer;
    private final Sensor        gyroscope;
    private final Sensor        gravitySensor;   // TYPE_GRAVITY for orientation alignment
    private final Handler       handler = new Handler(Looper.getMainLooper());
    private final RiskCallback  callback;

    // ── Gravity vector (updated by TYPE_GRAVITY, device frame) ──────────────────────────
    // Default to (0, 9.81, 0) — matches training data orientation — until first reading.
    private float gravX = 0f, gravY = 9.81f, gravZ = 0f;

    // ── ONNX Runtime ────────────────────────────────────────────────────────────────────
    private volatile OrtEnvironment ortEnv;
    private volatile OrtSession     ortSession;

    // ── Circular ring buffer: [sample_slot][channel] ────────────────────────────────────
    private final float[][] ringBuffer = new float[WINDOW_SIZE][N_CHANNELS];
    private int ringHead      = 0;   // next write position (oldest = ringHead when full)
    private int samplesFilled = 0;   // total samples written (capped at WINDOW_SIZE)
    private int stepCounter   = 0;   // samples since last inference

    // ── Latest sensor readings (updated by SensorEventListener) ─────────────────────────
    private float   accelX, accelY, accelZ;
    private float   gyroX,  gyroY,  gyroZ;
    private boolean hasAccel = false;
    private boolean hasGyro  = false;

    // ── EMA state ────────────────────────────────────────────────────────────────────────
    private float   emaScore       = 0f;
    private float   emaConf        = 0f;
    private boolean emaInitialized = false;

    // ── AlertDetector state ──────────────────────────────────────────────────────────────
    private int alertCount     = 0;
    private int cancelCooldown = 0;

    // ── Last accel / gyro magnitudes (for monitorState text) ─────────────────────────────
    private float lastAccelMag = 0f;
    private float lastGyroMag  = 0f;

    private boolean running = false;

    // ── 20 Hz sampler Runnable ───────────────────────────────────────────────────────────
    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (hasAccel && hasGyro) {
                pushSample();
            }
            if (running) {
                handler.postDelayed(this, SAMPLE_PERIOD_MS);
            }
        }
    };

    // ────────────────────────────────────────────────────────────────────────────────────
    // Constructor
    // ────────────────────────────────────────────────────────────────────────────────────

    public OnnxFallDetector(Context context, RiskCallback callback) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer  = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        this.gyroscope      = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        this.gravitySensor  = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) : null;
        this.callback = callback;

        // Load ONNX model on a background thread to avoid blocking the main thread.
        // ortSession stays null until loading completes; inference is safely skipped during this time.
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                // Extract model from assets to internal storage, then load from file path.
                // Loading from a file path is more reliable than byte-array on all ONNX RT versions.
                String modelPath = extractModelToFile(appContext, "risk_model.onnx");
                OrtEnvironment env = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                OrtSession session = env.createSession(modelPath, opts);
                ortEnv     = env;
                ortSession = session;
                Log.i(TAG, "ONNX model loaded from: " + modelPath);
            } catch (Throwable t) {
                // Catches OrtException, IOException, UnsatisfiedLinkError and any other error.
                // Surface the real error class + message so it is visible in both Logcat and UI.
                final String errDetail = t.getClass().getSimpleName() + ": " + t.getMessage();
                Log.e(TAG, "Failed to load ONNX model — " + errDetail, t);
                handler.post(() -> reportRisk(RiskLevel.SAFE, 100,
                        "模型加载失败: " + errDetail,
                        "请查看 Logcat (tag=OnnxFallDetector) 获取详情"));
                ortSession = null;
            }
        }, "onnx-init").start();
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────────────────

    public void start() {
        if (sensorManager == null || running) return;
        running = true;
        // SENSOR_DELAY_GAME ≈ 20 ms (50 Hz) — plenty for 20 Hz sampling
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
        }
        handler.postDelayed(sampler, SAMPLE_PERIOD_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(sampler);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (ortSession != null) {
            try { ortSession.close(); } catch (Exception ignored) {}
        }
        if (ortEnv != null) {
            try { ortEnv.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Called when user confirms "我没事" — resets fall state and enters 30 s cooldown.
     * Mirrors SensorFusionManager.resetToSafe().
     */
    public void resetToSafe() {
        alertCount     = 0;
        cancelCooldown = CANCEL_COOLDOWN;
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // SensorEventListener
    // ────────────────────────────────────────────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0];
            accelY = event.values[1];
            accelZ = event.values[2];
            hasAccel = true;
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroX = event.values[0];
            gyroY = event.values[1];
            gyroZ = event.values[2];
            hasGyro = true;
        } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravX = event.values[0];
            gravY = event.values[1];
            gravZ = event.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ────────────────────────────────────────────────────────────────────────────────────
    // Sampling & inference
    // ────────────────────────────────────────────────────────────────────────────────────

    /** Write the latest sensor reading into the ring buffer; trigger inference as needed. */
    private void pushSample() {
        ringBuffer[ringHead][0] = accelX;
        ringBuffer[ringHead][1] = accelY;
        ringBuffer[ringHead][2] = accelZ;
        ringBuffer[ringHead][3] = gyroX;
        ringBuffer[ringHead][4] = gyroY;
        ringBuffer[ringHead][5] = gyroZ;
        ringHead = (ringHead + 1) % WINDOW_SIZE;
        if (samplesFilled < WINDOW_SIZE) samplesFilled++;
        stepCounter++;

        lastAccelMag = (float) Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
        lastGyroMag  = (float) Math.sqrt(gyroX  * gyroX  + gyroY  * gyroY  + gyroZ  * gyroZ);

        if (samplesFilled >= WINDOW_SIZE && stepCounter >= INFER_STEP) {
            stepCounter = 0;
            runInference();
        }
    }

    /**
     * Build the 5-second window from the ring buffer, normalize it, run ONNX,
     * apply EMA and AlertDetector, then invoke the risk callback.
     */
    private void runInference() {
        if (ortSession == null) {
            reportRisk(RiskLevel.SAFE, 100, "AI模型未加载", "请检查模型文件 risk_model.onnx。");
            return;
        }

        // ── Step 1: compute rotation matrix R = align(gravity → [0,1,0]) ────────────────
        float[] R = gravityAlignmentMatrix(gravX, gravY, gravZ);

        // ── Step 2 & 3: apply R to each raw sample, then z-score normalize ──────────────
        // flat layout: [channel][time] row-major, shape (1, 6, 100)
        float[] flat = new float[N_CHANNELS * WINDOW_SIZE];
        float[] aligned = new float[3];
        for (int t = 0; t < WINDOW_SIZE; t++) {
            int idx = (ringHead + t) % WINDOW_SIZE;
            // acc_aligned = R @ acc_raw
            rotate3(R, ringBuffer[idx][0], ringBuffer[idx][1], ringBuffer[idx][2], aligned);
            flat[0 * WINDOW_SIZE + t] = (aligned[0] - MEAN[0]) / STD[0];
            flat[1 * WINDOW_SIZE + t] = (aligned[1] - MEAN[1]) / STD[1];
            flat[2 * WINDOW_SIZE + t] = (aligned[2] - MEAN[2]) / STD[2];
            // gyro_aligned = R @ gyro_raw
            rotate3(R, ringBuffer[idx][3], ringBuffer[idx][4], ringBuffer[idx][5], aligned);
            flat[3 * WINDOW_SIZE + t] = (aligned[0] - MEAN[3]) / STD[3];
            flat[4 * WINDOW_SIZE + t] = (aligned[1] - MEAN[4]) / STD[4];
            flat[5 * WINDOW_SIZE + t] = (aligned[2] - MEAN[5]) / STD[5];
        }

        float rawScore, rawConf;
        try {
            OnnxTensor tensor = OnnxTensor.createTensor(
                    ortEnv,
                    FloatBuffer.wrap(flat),
                    new long[]{1, N_CHANNELS, WINDOW_SIZE});

            OrtSession.Result result = ortSession.run(
                    Collections.singletonMap("sensor_window", tensor));

            rawScore = ((OnnxTensor) result.get(0)).getFloatBuffer().get(0);
            rawConf  = ((OnnxTensor) result.get(1)).getFloatBuffer().get(0);

            result.close();
            tensor.close();
        } catch (OrtException e) {
            Log.e(TAG, "Inference error: " + e.getMessage());
            return;
        }

        // ── EMA smoothing ──
        if (!emaInitialized) {
            emaScore       = rawScore;
            emaConf        = rawConf;
            emaInitialized = true;
        } else {
            emaScore = EMA_ALPHA * rawScore + (1f - EMA_ALPHA) * emaScore;
            emaConf  = EMA_ALPHA * rawConf  + (1f - EMA_ALPHA) * emaConf;
        }

        // ── AlertDetector ──
        int[] decision = alertDetectorUpdate(emaScore, emaConf);
        int alertLevel = decision[0]; // 0=safe 1=low 2=medium 3=high

        // ── Map to RiskLevel ──
        RiskLevel riskLevel;
        int       uiScore;
        String    suggestion;
        switch (alertLevel) {
            case 3:
                riskLevel  = RiskLevel.HIGH;
                uiScore    = 20;
                suggestion = "AI检测到跌倒（持续三级警报），请立即启动急救流程。";
                break;
            case 2:
                riskLevel  = RiskLevel.MEDIUM;
                uiScore    = 60;
                suggestion = "AI检测到高风险动作（二级警报），请尽快确认安全状态。";
                break;
            case 1:
                riskLevel  = RiskLevel.LOW;
                uiScore    = 80;
                suggestion = "AI检测到轻微异常（一级警报），请留意自身状态。";
                break;
            default:
                riskLevel  = RiskLevel.SAFE;
                uiScore    = Math.round(100f - emaScore);   // show live score trend
                uiScore    = Math.max(50, Math.min(100, uiScore));
                suggestion = "状态稳定，AI模型持续监测中。";
                break;
        }

        String monitorState = String.format(
                Locale.getDefault(),
                "评分 %.1f | 置信 %.2f | 加速度 %.2f | 角速度 %.2f",
                emaScore, emaConf, lastAccelMag, lastGyroMag);

        reportRisk(riskLevel, uiScore, monitorState, suggestion);
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // AlertDetector state machine (ported from MobiAct/infer.py AlertDetector.update)
    // ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Returns [alertLevel, isNewTrigger(0/1)].
     * alertLevel: 0=normal, 1=LOW, 2=MEDIUM, 3=HIGH
     * isNewTrigger: 1 = this call is the first trigger at this level
     */
    private int[] alertDetectorUpdate(float smoothedScore, float conf) {
        // Cancel cooldown period
        if (cancelCooldown > 0) {
            cancelCooldown--;
            alertCount = 0;
            return new int[]{0, 0};
        }

        // Accumulate or reset consecutive-window counter
        if (smoothedScore > SCORE_THRESHOLD && conf > CONF_THRESHOLD) {
            alertCount++;
        } else {
            alertCount = 0;
            return new int[]{0, 0};
        }

        // Determine level and whether this is a new trigger
        if (alertCount >= L3_WINDOWS)      return new int[]{3, 1};   // HIGH: continuous
        if (alertCount == L2_WINDOWS)      return new int[]{2, 1};   // MEDIUM: trigger once
        if (alertCount == L1_WINDOWS)      return new int[]{1, 1};   // LOW: trigger once
        if (alertCount > L1_WINDOWS)       return new int[]{2, 0};   // ascending, no re-trigger
        return new int[]{0, 0};
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────────────────

    private void reportRisk(RiskLevel level, int score, String monitorState, String suggestion) {
        if (callback != null) {
            callback.onRiskUpdated(level, score, monitorState, suggestion);
        }
    }

    /**
     * 计算 3×3 旋转矩阵（行主序，float[9]），将设备重力向量对齐到训练数据的 Y 轴方向 [0, 1, 0]。
     *
     * 使用 Rodrigues 旋转公式：
     *   v     = normalize(gravity)
     *   axis  = cross(v, [0,1,0])
     *   sinθ  = |axis|，cosθ = dot(v, [0,1,0])
     *   R = cosθ·I + sinθ·[k]× + (1-cosθ)·k⊗k
     *
     * 边界情况：
     *   • 已对齐（sinθ ≈ 0，cosθ > 0）→ 返回单位矩阵
     *   • 反向对齐（sinθ ≈ 0，cosθ < 0）→ 返回绕 Z 轴旋转 180°
     */
    private static float[] gravityAlignmentMatrix(float gx, float gy, float gz) {
        // 归一化重力向量
        float norm = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (norm < 1e-6f) {
            // 向量接近零，退化情况，返回单位矩阵
            return new float[]{1,0,0, 0,1,0, 0,0,1};
        }
        float vx = gx / norm;
        float vy = gy / norm;
        float vz = gz / norm;

        // 目标方向 [0, 1, 0]
        // 旋转轴 = cross(v, [0,1,0]) = cross([vx,vy,vz], [0,1,0])
        //        = (vy*0 - vz*1, vz*0 - vx*0, vx*1 - vy*0)
        //        = (-vz, 0, vx)
        float ax = -vz;
        float ay =  0f;
        float az =  vx;

        float sinTheta = (float) Math.sqrt(ax * ax + ay * ay + az * az); // 叉积模长即 sinθ
        float cosTheta = vy;  // dot(v, [0,1,0]) = vy

        // 已对齐，返回单位矩阵
        if (sinTheta < 1e-6f && cosTheta > 0f) {
            return new float[]{1,0,0, 0,1,0, 0,0,1};
        }

        // 反向对齐（手机倒置），绕 Z 轴旋转 180°
        if (sinTheta < 1e-6f) {
            return new float[]{-1,0,0, 0,-1,0, 0,0,1};
        }

        // 归一化旋转轴
        ax /= sinTheta;
        // ay 始终为 0
        az /= sinTheta;

        // Rodrigues 公式：R = cosθ·I + sinθ·[k]× + (1-cosθ)·k⊗k
        // 其中 k = (ax, ay=0, az)
        float c = cosTheta;
        float s = sinTheta;
        float t = 1f - c;

        // 行主序排列 [r00,r01,r02, r10,r11,r12, r20,r21,r22]
        return new float[]{
                t*ax*ax + c,      t*ax*ay - s*az,  t*ax*az + s*ay,
                t*ay*ax + s*az,  t*ay*ay + c,      t*ay*az - s*ax,
                t*az*ax - s*ay,  t*az*ay + s*ax,  t*az*az + c
        };
    }

    /**
     * 将 3×3 行主序旋转矩阵作用于向量 (x, y, z)，结果写入 {@code out[0..2]}。
     */
    private static void rotate3(float[] R, float x, float y, float z, float[] out) {
        out[0] = R[0]*x + R[1]*y + R[2]*z;
        out[1] = R[3]*x + R[4]*y + R[5]*z;
        out[2] = R[6]*x + R[7]*y + R[8]*z;
    }

    /**
     * Copies an asset file to the app's internal files directory and returns its absolute path.
     * Re-uses the existing file on subsequent calls (no redundant I/O).
     */
    private static String extractModelToFile(Context context, String assetName) throws IOException {
        File dest = new File(context.getFilesDir(), assetName);
        if (!dest.exists()) {
            try (InputStream in = context.getAssets().open(assetName);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
        return dest.getAbsolutePath();
    }
}
