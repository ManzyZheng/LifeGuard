package com.example.firstaid.logic;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import com.example.firstaid.model.RiskLevel;

import java.util.Locale;

public class SensorFusionManager implements SensorEventListener {

    public interface RiskCallback {
        void onRiskUpdated(RiskLevel level, int score, String monitorState, String suggestion);
    }

    // Physical constants and algorithm thresholds.
    private static final float GRAVITY = 9.81f;
    private static final float IMPACT_THRESHOLD = 12.0f;          // m/s^2
    private static final float ORIENTATION_THRESHOLD = 3.0f;      // rad/s
    private static final float IMMOBILITY_THRESHOLD = 1.2f;       // m/s^2
    private static final long ORIENTATION_WINDOW_MS = 2_000L;     // 2 seconds
    private static final long IMMOBILITY_DURATION_MS = 10_000L;   // 10 seconds
    private static final long IMPACT_STALE_MS = 8_000L;           // Stage 1 stale timeout
    private static final long ORIENTATION_STALE_MS = 15_000L;     // Stage 2 stale timeout
    private static final float FALL_RECOVERY_MOVEMENT_THRESHOLD = 2.0f;
    private static final long FALL_RECOVERY_DURATION_MS = 5_000L;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor gyroscope;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RiskCallback callback;

    // Real-time sensor features.
    private float accelMagnitude = GRAVITY;
    private float gyroMagnitude = 0f;
    private float dynamicAccel = 0f;

    // Required timestamps.
    private long impactTimestamp = -1L;      // Stage 1 timestamp
    private long rotationTimestamp = -1L;    // Stage 2 timestamp
    private long movementTimestamp;          // Last movement timestamp

    // Stage-3 helper timestamp.
    private long immobilityStartTimestamp = -1L;
    private long fallRecoveryStartTimestamp = -1L;

    // Stage flags.
    private boolean impactDetected = false;
    private boolean orientationChanged = false;
    private boolean fallDetected = false;

    private boolean running;

    private final Runnable evaluator = new Runnable() {
        @Override
        public void run() {
            evaluateRisk();
            if (running) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    public SensorFusionManager(Context context, RiskCallback callback) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        this.gyroscope = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        this.callback = callback;
        this.movementTimestamp = System.currentTimeMillis();
    }

    public void start() {
        if (sensorManager == null || running) {
            return;
        }
        running = true;
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        }
        handler.post(evaluator);
    }

    /**
     * Manual reset entry point for UI:
     * user confirms safety ("我没事") and clears current fall pipeline state.
     */
    public void resetToSafe() {
        resetStages();
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(evaluator);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = System.currentTimeMillis();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            accelMagnitude = (float) Math.sqrt(x * x + y * y + z * z);
            dynamicAccel = Math.abs(accelMagnitude - GRAVITY);

            // Update latest movement timestamp when meaningful movement exists.
            if (dynamicAccel >= IMMOBILITY_THRESHOLD) {
                movementTimestamp = now;
            }

            // -------- Stage 1: Impact Detection --------
            // Detect a potential impact event.
            if (dynamicAccel > IMPACT_THRESHOLD) {
                impactDetected = true;
                impactTimestamp = now;
                orientationChanged = false;
                rotationTimestamp = -1L;
                immobilityStartTimestamp = -1L;
                fallDetected = false;
            }

            // -------- Stage 3: Immobility Detection --------
            // After Stage 1 + Stage 2 are satisfied, check if the user remains immobile.
            if (impactDetected && orientationChanged && !fallDetected) {
                if (dynamicAccel < IMMOBILITY_THRESHOLD) {
                    if (immobilityStartTimestamp < 0L) {
                        immobilityStartTimestamp = now;
                    }
                    long immobileDuration = now - immobilityStartTimestamp;
                    if (immobileDuration >= IMMOBILITY_DURATION_MS) {
                        fallDetected = true;
                    }
                } else {
                    // Movement resumed, Stage 3 must be accumulated again.
                    immobilityStartTimestamp = -1L;
                }
            }

            // If fall has already been detected, allow automatic recovery to safe
            // when sufficient movement is observed continuously.
            if (fallDetected) {
                if (dynamicAccel >= FALL_RECOVERY_MOVEMENT_THRESHOLD) {
                    if (fallRecoveryStartTimestamp < 0L) {
                        fallRecoveryStartTimestamp = now;
                    }
                    if (now - fallRecoveryStartTimestamp >= FALL_RECOVERY_DURATION_MS) {
                        resetStages();
                    }
                } else {
                    fallRecoveryStartTimestamp = -1L;
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            gyroMagnitude = (float) Math.sqrt(x * x + y * y + z * z);

            // -------- Stage 2: Orientation Change Detection --------
            // Within 2 seconds after impact, detect significant body rotation.
            if (impactDetected && !orientationChanged && impactTimestamp > 0L) {
                long elapsedAfterImpact = now - impactTimestamp;
                if (elapsedAfterImpact <= ORIENTATION_WINDOW_MS && gyroMagnitude > ORIENTATION_THRESHOLD) {
                    orientationChanged = true;
                    rotationTimestamp = now;
                    immobilityStartTimestamp = -1L;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no-op
    }

    private void evaluateRisk() {
        long now = System.currentTimeMillis();
        long immobileSeconds = Math.max(0L, (now - movementTimestamp) / 1000L);

        // If Stage 2 is not detected within the window, clear Stage 1/2 state.
        if (impactDetected && !orientationChanged && impactTimestamp > 0L
                && now - impactTimestamp > ORIENTATION_WINDOW_MS) {
            resetStages();
        }
        // Stage 1 stale timeout guard.
        if (impactDetected && !orientationChanged && impactTimestamp > 0L
                && now - impactTimestamp > IMPACT_STALE_MS) {
            resetStages();
        }
        // Stage 2 stale timeout guard (rotation happened but immobility not met).
        if (impactDetected && orientationChanged && rotationTimestamp > 0L && !fallDetected
                && now - rotationTimestamp > ORIENTATION_STALE_MS) {
            resetStages();
        }

        int score;
        RiskLevel level = RiskLevel.SAFE;
        String suggestion = "状态稳定，继续监测。";
        
        // Fall must satisfy strict order:
        // Stage 1 (Impact) -> Stage 2 (Orientation Change) -> Stage 3 (Immobility).
        if (fallDetected) {
            level = RiskLevel.HIGH;
            score = 20;
            suggestion = "检测到疑似跌倒（三阶段命中），请立即启动急救流程。";
        } else {
            boolean severeAbnormal = dynamicAccel >= 8.0f || gyroMagnitude >= 2.8f || impactDetected;
            boolean slightAbnormal = dynamicAccel >= 2.0f || gyroMagnitude >= 1.0f;

            if (severeAbnormal) {
                level = RiskLevel.MEDIUM;
                score = 60;
                suggestion = "检测到剧烈异常运动，请尽快确认是否需要帮助。";
            } else if (slightAbnormal) {
                level = RiskLevel.LOW;
                score = 80;
                suggestion = "检测到轻微异常运动，请留意自身状态。";
            } else {
                level = RiskLevel.SAFE;
                score = 100;
                suggestion = "状态稳定，继续监测。";
            }
        }

        // Build monitor state output required by UI.
        String monitorState = String.format(
                Locale.getDefault(),
                "加速度 %.2f | 角速度 %.2f | 静止 %ds",
                accelMagnitude, gyroMagnitude, immobileSeconds
        );

        if (callback != null) {
            callback.onRiskUpdated(level, score, monitorState, suggestion);
        }
    }

    private void resetStages() {
        impactDetected = false;
        orientationChanged = false;
        impactTimestamp = -1L;
        rotationTimestamp = -1L;
        immobilityStartTimestamp = -1L;
        fallRecoveryStartTimestamp = -1L;
        fallDetected = false;
    }
}
