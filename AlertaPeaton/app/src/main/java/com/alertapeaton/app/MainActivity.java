package com.alertapeaton.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private static final int CAMERA_PERMISSION = 10;

    private PreviewView previewView;
    private TextView statusText;
    private TextView detailText;
    private TextView guideText;
    private Switch assistSwitch;
    private Switch guideSwitch;
    private ExecutorService cameraExecutor;
    private ObjectDetector detector;
    private TextToSpeech tts;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private boolean assistanceEnabled = true;
    private boolean guidanceEnabled = true;
    private long lastAlertAt = 0L;
    private String lastAlert = "";
    private float previousCentralArea = 0f;
    private long previousCentralTime = 0L;

    private String guidanceCandidate = "";
    private int guidanceCandidateCount = 0;
    private String lastGuidance = "";
    private long lastGuidanceAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        cameraExecutor = Executors.newSingleThreadExecutor();
        tts = new TextToSpeech(this, this);

        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .build();
        detector = ObjectDetection.getClient(options);

        assistSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            assistanceEnabled = checked;
            assistSwitch.setText(checked ? "Alertas activas" : "Alertas pausadas");
            if (checked) speak("Alertas activadas");
        });

        guideSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            guidanceEnabled = checked;
            guideSwitch.setText(checked ? "Guía de camino activa" : "Guía de camino pausada");
            if (checked) speak("Guía visual activada");
        });

        Button testButton = findViewById(1005);
        testButton.setOnClickListener(v -> {
            guideText.setText("← DESVÍATE A LA IZQUIERDA");
            vibrateLeft();
            speak("Prueba. Desvíate un poco a la izquierda");
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        root.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(16));
        top.setBackgroundColor(0xC0000000);

        statusText = new TextView(this);
        statusText.setText("Iniciando cámara…");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(24);
        statusText.setGravity(Gravity.START);
        top.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        detailText = new TextView(this);
        detailText.setText("Analizando obstáculos y corredor libre frente a ti.");
        detailText.setTextColor(0xFFE0E0E0);
        detailText.setTextSize(16);
        detailText.setPadding(0, dp(6), 0, 0);
        top.addView(detailText, new LinearLayout.LayoutParams(-1, -2));

        guideText = new TextView(this);
        guideText.setText("↑ ESPERANDO CAMINO");
        guideText.setTextColor(Color.WHITE);
        guideText.setTextSize(25);
        guideText.setGravity(Gravity.CENTER);
        guideText.setPadding(dp(8), dp(12), dp(8), dp(8));
        top.addView(guideText, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(top, topParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(10), dp(16), dp(18));
        bottom.setBackgroundColor(0xD9000000);

        assistSwitch = new Switch(this);
        assistSwitch.setChecked(true);
        assistSwitch.setText("Alertas activas");
        assistSwitch.setTextColor(Color.WHITE);
        assistSwitch.setTextSize(18);
        assistSwitch.setMinHeight(dp(50));
        bottom.addView(assistSwitch, new LinearLayout.LayoutParams(-1, -2));

        guideSwitch = new Switch(this);
        guideSwitch.setChecked(true);
        guideSwitch.setText("Guía de camino activa");
        guideSwitch.setTextColor(Color.WHITE);
        guideSwitch.setTextSize(18);
        guideSwitch.setMinHeight(dp(50));
        bottom.addView(guideSwitch, new LinearLayout.LayoutParams(-1, -2));

        Button testButton = new Button(this);
        testButton.setId(1005);
        testButton.setText("Probar indicación");
        testButton.setTextSize(17);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54));
        bp.topMargin = dp(6);
        bottom.addView(testButton, bp);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, bottomParams);

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                runOnUiThread(() -> updateUi("Cámara activa", "Buscando un corredor libre y obstáculos…"));
            } catch (Exception e) {
                runOnUiThread(() -> updateUi("No se pudo abrir la cámara", safeMessage(e)));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (processing.getAndSet(true)) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            processing.set(false);
            imageProxy.close();
            return;
        }

        InputImage input = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        Task<List<DetectedObject>> task = detector.process(input);
        task.addOnSuccessListener(objects -> evaluate(objects, input.getWidth(), input.getHeight()))
                .addOnFailureListener(e -> runOnUiThread(() -> updateUi("Error de visión", safeMessage(e))))
                .addOnCompleteListener(done -> {
                    processing.set(false);
                    imageProxy.close();
                });
    }

    private void evaluate(List<DetectedObject> objects, int imageWidth, int imageHeight) {
        if (objects == null) return;

        Guidance guidance = calculateGuidance(objects, imageWidth, imageHeight);

        if (objects.isEmpty()) {
            previousCentralArea = 0f;
            runOnUiThread(() -> {
                updateUi("Trayectoria despejada", "No hay obstáculos detectados en el corredor inmediato.");
                applyGuidance(guidance, false);
            });
            return;
        }

        DetectedObject best = null;
        float bestArea = 0f;
        for (DetectedObject obj : objects) {
            Rect r = obj.getBoundingBox();
            float cx = r.exactCenterX() / (float) imageWidth;
            float area = (r.width() * r.height()) / (float) (imageWidth * imageHeight);
            boolean central = cx > 0.24f && cx < 0.76f;
            if (central && area > bestArea) {
                best = obj;
                bestArea = area;
            }
        }

        if (best == null) {
            runOnUiThread(() -> {
                updateUi("Camino visible", "Obstáculos laterales detectados. Evaluando por dónde continuar.");
                applyGuidance(guidance, false);
            });
            return;
        }

        Rect r = best.getBoundingBox();
        float cx = r.exactCenterX() / (float) imageWidth;
        String side = cx < 0.40f ? "izquierda" : (cx > 0.60f ? "derecha" : "frente");
        String label = labelFor(best);
        long now = SystemClock.uptimeMillis();

        float growth = 0f;
        if (previousCentralArea > 0f && now - previousCentralTime < 1300L) {
            growth = (bestArea - previousCentralArea) / Math.max(previousCentralArea, 0.01f);
        }
        previousCentralArea = bestArea;
        previousCentralTime = now;

        final String status;
        final String detail;
        final boolean danger;
        final boolean caution;

        if (bestArea > 0.28f || (bestArea > 0.08f && growth > 0.22f)) {
            status = "PELIGRO";
            detail = label + " muy cerca o aproximándose por " + side;
            danger = true;
            caution = false;
        } else if (bestArea > 0.10f || growth > 0.15f) {
            status = "PRECAUCIÓN";
            detail = label + " adelante, hacia " + side;
            danger = false;
            caution = true;
        } else {
            status = "Camino en análisis";
            detail = label + " a " + side + ". Buscando el corredor más libre.";
            danger = false;
            caution = false;
        }

        runOnUiThread(() -> {
            updateUi(status, detail);
            if (assistanceEnabled) {
                if (danger) alert(detail, true);
                else if (caution) alert(detail, false);
            }
            applyGuidance(guidance, danger || caution);
        });
    }

    private Guidance calculateGuidance(List<DetectedObject> objects, int width, int height) {
        float[] blocked = new float[]{0f, 0f, 0f};
        float zoneWidth = width / 3f;
        float corridorTop = height * 0.38f;
        float corridorHeight = Math.max(1f, height - corridorTop);

        for (DetectedObject obj : objects) {
            Rect r = obj.getBoundingBox();
            float top = Math.max(r.top, corridorTop);
            float bottom = Math.min(r.bottom, height);
            if (bottom <= top) continue;

            float proximity = 0.35f + 0.65f * clamp(r.bottom / (float) height, 0f, 1f);
            for (int z = 0; z < 3; z++) {
                float zl = z * zoneWidth;
                float zr = (z + 1) * zoneWidth;
                float overlapW = Math.max(0f, Math.min(r.right, zr) - Math.max(r.left, zl));
                float overlapH = bottom - top;
                float ratio = (overlapW * overlapH) / Math.max(1f, zoneWidth * corridorHeight);
                blocked[z] += ratio * proximity * 2.2f;
            }
        }

        for (int i = 0; i < 3; i++) blocked[i] = clamp(blocked[i], 0f, 1f);

        float left = blocked[0];
        float center = blocked[1];
        float right = blocked[2];

        if (center < 0.18f) {
            return new Guidance("STRAIGHT", "↑ SIGUE RECTO", "Sigue recto", center);
        }

        float bestSide = Math.min(left, right);
        if (center > 0.58f && left > 0.50f && right > 0.50f) {
            return new Guidance("STOP", "■ DETENTE", "Detente. El paso parece bloqueado", Math.min(center, bestSide));
        }

        if (left + 0.08f < right && left + 0.06f < center) {
            return new Guidance("LEFT", "← VE A LA IZQUIERDA", "Desvíate un poco a la izquierda", left);
        }

        if (right + 0.08f < left && right + 0.06f < center) {
            return new Guidance("RIGHT", "VE A LA DERECHA →", "Desvíate un poco a la derecha", right);
        }

        if (left < center - 0.04f) {
            return new Guidance("LEFT", "← VE A LA IZQUIERDA", "Desvíate un poco a la izquierda", left);
        }
        if (right < center - 0.04f) {
            return new Guidance("RIGHT", "VE A LA DERECHA →", "Desvíate un poco a la derecha", right);
        }

        return new Guidance("CAUTION", "↑ AVANZA CON CUIDADO", "Avanza con cuidado", center);
    }

    private void applyGuidance(Guidance guidance, boolean hazardSpeaking) {
        guideText.setText(guidance.display);

        if (!guidanceEnabled || !assistanceEnabled || hazardSpeaking) return;

        if (guidance.key.equals(guidanceCandidate)) {
            guidanceCandidateCount++;
        } else {
            guidanceCandidate = guidance.key;
            guidanceCandidateCount = 1;
        }

        if (guidanceCandidateCount < 3) return;

        long now = SystemClock.uptimeMillis();
        long repeatMs = guidance.key.equals("STRAIGHT") ? 7000L : 3500L;
        boolean changed = !guidance.key.equals(lastGuidance);
        if (!changed && now - lastGuidanceAt < repeatMs) return;
        if (now - lastGuidanceAt < 1800L) return;

        lastGuidance = guidance.key;
        lastGuidanceAt = now;

        switch (guidance.key) {
            case "LEFT":
                vibrateLeft();
                break;
            case "RIGHT":
                vibrateRight();
                break;
            case "STOP":
                vibrateDanger();
                break;
            default:
                break;
        }
        speak(guidance.spoken);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private String labelFor(DetectedObject object) {
        if (object.getLabels() == null || object.getLabels().isEmpty()) return "Obstáculo";
        String raw = object.getLabels().get(0).getText();
        if (raw == null || raw.trim().isEmpty() || "Unknown".equalsIgnoreCase(raw)) return "Obstáculo";
        return raw;
    }

    private void alert(String text, boolean danger) {
        long now = SystemClock.uptimeMillis();
        long interval = danger ? 1400L : 3200L;
        if (text.equals(lastAlert) && now - lastAlertAt < interval) return;
        lastAlert = text;
        lastAlertAt = now;
        if (danger) vibrateDanger(); else vibrateCaution();
        speak(text);
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alerta");
    }

    private Vibrator vibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            return vm.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    private void vibrateDanger() {
        vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 80, 180, 80, 350}, -1));
    }

    private void vibrateCaution() {
        vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0, 120, 120, 120}, -1));
    }

    private void vibrateLeft() {
        vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0, 190}, -1));
    }

    private void vibrateRight() {
        vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0, 90, 90, 90}, -1));
    }

    private void updateUi(String status, String detail) {
        statusText.setText(status);
        detailText.setText(detail);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("es", "CL"));
            tts.setSpeechRate(1.05f);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            updateUi("Permiso de cámara requerido", "Sin cámara la app no puede analizar el camino.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) detector.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private static class Guidance {
        final String key;
        final String display;
        final String spoken;
        final float blockedScore;

        Guidance(String key, String display, String spoken, float blockedScore) {
            this.key = key;
            this.display = display;
            this.spoken = spoken;
            this.blockedScore = blockedScore;
        }
    }
}
