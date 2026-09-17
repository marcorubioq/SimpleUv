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
    private static final long SEGMENT_INTERVAL_MS = 1200L;

    private PreviewView previewView;
    private TextView statusText;
    private TextView detailText;
    private TextView guideText;
    private TextView semanticText;
    private Switch assistSwitch;
    private Switch guideSwitch;

    private ExecutorService cameraExecutor;
    private ObjectDetector detector;
    private SegmentationEngine segmentationEngine;
    private volatile SegmentationEngine.Result latestSegmentation;
    private TextToSpeech tts;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private boolean assistanceEnabled = true;
    private boolean guidanceEnabled = true;
    private long lastSegmentationAt = 0L;
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

        try {
            segmentationEngine = new SegmentationEngine(this);
            semanticText.setText("Segmentación: PIDNet lista");
        } catch (Exception e) {
            segmentationEngine = null;
            semanticText.setText("Segmentación no disponible: " + safeMessage(e));
        }

        assistSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            assistanceEnabled = checked;
            assistSwitch.setText(checked ? "Alertas activas" : "Alertas pausadas");
            if (checked) speak("Alertas activadas");
        });

        guideSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            guidanceEnabled = checked;
            guideSwitch.setText(checked ? "Guía semántica activa" : "Guía semántica pausada");
            if (checked) speak("Guía de vereda activada");
        });

        Button testButton = findViewById(1005);
        testButton.setOnClickListener(v -> {
            guideText.setText("← VEREDA A LA IZQUIERDA");
            vibrateLeft();
            speak("Prueba. La vereda continúa hacia la izquierda");
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
        root.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(14));
        top.setBackgroundColor(0xC8000000);

        statusText = text("Iniciando cámara…", 24, Color.WHITE);
        top.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        detailText = text("Analizando obstáculos y superficie transitable.", 16, 0xFFE0E0E0);
        detailText.setPadding(0, dp(5), 0, 0);
        top.addView(detailText, new LinearLayout.LayoutParams(-1, -2));

        guideText = text("↑ BUSCANDO VEREDA", 25, Color.WHITE);
        guideText.setGravity(Gravity.CENTER);
        guideText.setPadding(dp(8), dp(10), dp(8), dp(8));
        top.addView(guideText, new LinearLayout.LayoutParams(-1, -2));

        semanticText = text("Segmentación: iniciando…", 13, 0xFFBDBDBD);
        semanticText.setGravity(Gravity.CENTER);
        top.addView(semanticText, new LinearLayout.LayoutParams(-1, -2));

        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(16));
        bottom.setBackgroundColor(0xD9000000);

        assistSwitch = new Switch(this);
        assistSwitch.setChecked(true);
        assistSwitch.setText("Alertas activas");
        assistSwitch.setTextColor(Color.WHITE);
        assistSwitch.setTextSize(18);
        assistSwitch.setMinHeight(dp(48));
        bottom.addView(assistSwitch, new LinearLayout.LayoutParams(-1, -2));

        guideSwitch = new Switch(this);
        guideSwitch.setChecked(true);
        guideSwitch.setText("Guía semántica activa");
        guideSwitch.setTextColor(Color.WHITE);
        guideSwitch.setTextSize(18);
        guideSwitch.setMinHeight(dp(48));
        bottom.addView(guideSwitch, new LinearLayout.LayoutParams(-1, -2));

        Button testButton = new Button(this);
        testButton.setId(1005);
        testButton.setText("Probar indicación");
        testButton.setTextSize(17);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(52));
        bp.topMargin = dp(5);
        bottom.addView(testButton, bp);

        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
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
                runOnUiThread(() -> updateUi("Cámara activa", "Reconociendo vereda, calzada y obstáculos…"));
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

        long now = SystemClock.uptimeMillis();
        if (segmentationEngine != null && now - lastSegmentationAt >= SEGMENT_INTERVAL_MS) {
            lastSegmentationAt = now;
            try {
                latestSegmentation = segmentationEngine.analyze(imageProxy);
                SegmentationEngine.Result snapshot = latestSegmentation;
                runOnUiThread(() -> semanticText.setText(snapshot.debugText()));
            } catch (Exception e) {
                runOnUiThread(() -> semanticText.setText("Segmentación: " + safeMessage(e)));
            }
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

    private void evaluate(List<DetectedObject> objects, int width, int height) {
        if (objects == null) return;

        Guidance obstacleGuide = calculateObstacleGuidance(objects, width, height);
        Guidance semanticGuide = semanticGuidance(latestSegmentation);
        Guidance guide = semanticGuide != null ? fuseGuidance(semanticGuide, obstacleGuide) : obstacleGuide;

        DetectedObject best = null;
        float bestArea = 0f;
        for (DetectedObject obj : objects) {
            Rect r = obj.getBoundingBox();
            float cx = r.exactCenterX() / (float) width;
            float area = (r.width() * r.height()) / (float) (width * height);
            if (cx > 0.24f && cx < 0.76f && area > bestArea) {
                best = obj;
                bestArea = area;
            }
        }

        if (best == null) {
            previousCentralArea = 0f;
            runOnUiThread(() -> {
                updateUi(semanticGuide == null ? "Camino en análisis" : "Superficie reconocida",
                        semanticGuide == null ? "Buscando un corredor libre." : semanticGuide.detail);
                applyGuidance(guide, false);
            });
            return;
        }

        Rect r = best.getBoundingBox();
        float cx = r.exactCenterX() / (float) width;
        String side = cx < 0.40f ? "izquierda" : (cx > 0.60f ? "derecha" : "frente");
        String label = labelFor(best);
        long now = SystemClock.uptimeMillis();
        float growth = 0f;
        if (previousCentralArea > 0f && now - previousCentralTime < 1300L) {
            growth = (bestArea - previousCentralArea) / Math.max(previousCentralArea, 0.01f);
        }
        previousCentralArea = bestArea;
        previousCentralTime = now;

        final boolean danger = bestArea > 0.28f || (bestArea > 0.08f && growth > 0.22f);
        final boolean caution = !danger && (bestArea > 0.10f || growth > 0.15f);
        final String status = danger ? "PELIGRO" : (caution ? "PRECAUCIÓN" : "Guía activa");
        final String detail = danger ? label + " muy cerca o aproximándose por " + side
                : (caution ? label + " adelante, hacia " + side : guide.detail);

        runOnUiThread(() -> {
            updateUi(status, detail);
            if (assistanceEnabled) {
                if (danger) alert(detail, true);
                else if (caution) alert(detail, false);
            }
            applyGuidance(guide, danger || caution);
        });
    }

    private Guidance semanticGuidance(SegmentationEngine.Result s) {
        if (s == null || System.currentTimeMillis() - s.timestampMs > 3500L) return null;

        float l = s.sidewalk[0] - 0.75f * s.road[0];
        float c = s.sidewalk[1] - 0.75f * s.road[1];
        float r = s.sidewalk[2] - 0.75f * s.road[2];
        float maxSidewalk = Math.max(s.sidewalk[0], Math.max(s.sidewalk[1], s.sidewalk[2]));

        if (s.road[1] > 0.55f && maxSidewalk < 0.14f) {
            return new Guidance("STOP", "■ CALZADA AL FRENTE",
                    "Atención. La cámara ve calzada al frente. Detente y verifica el cruce.",
                    "Calzada predominante frente a ti.");
        }
        if (s.sidewalk[1] > 0.20f && c >= l - 0.05f && c >= r - 0.05f) {
            return new Guidance("STRAIGHT", "↑ MANTENTE EN LA VEREDA",
                    "Mantente sobre la vereda y sigue recto.", "Vereda reconocida al frente.");
        }
        if (s.sidewalk[0] > 0.16f && l > c + 0.07f && l > r + 0.04f) {
            return new Guidance("LEFT", "← VEREDA A LA IZQUIERDA",
                    "La vereda continúa hacia la izquierda.", "Mayor superficie de vereda hacia la izquierda.");
        }
        if (s.sidewalk[2] > 0.16f && r > c + 0.07f && r > l + 0.04f) {
            return new Guidance("RIGHT", "VEREDA A LA DERECHA →",
                    "La vereda continúa hacia la derecha.", "Mayor superficie de vereda hacia la derecha.");
        }
        if (s.road[1] > 0.35f && s.sidewalk[1] < 0.12f) {
            return new Guidance("CAUTION", "! POSIBLE BORDE DE VEREDA",
                    "Precaución. La vereda puede terminar delante.", "Posible transición de vereda a calzada.");
        }
        return null;
    }

    private Guidance fuseGuidance(Guidance semantic, Guidance obstacle) {
        if ("STOP".equals(obstacle.key)) return obstacle;
        if ("STOP".equals(semantic.key)) return semantic;
        if (("LEFT".equals(semantic.key) || "RIGHT".equals(semantic.key)) &&
                semantic.key.equals(obstacle.key)) return semantic;
        if ("STRAIGHT".equals(semantic.key) && !"STOP".equals(obstacle.key)) return semantic;
        if ("CAUTION".equals(semantic.key)) return semantic;
        return obstacle;
    }

    private Guidance calculateObstacleGuidance(List<DetectedObject> objects, int width, int height) {
        float[] blocked = new float[]{0f, 0f, 0f};
        float zoneWidth = width / 3f;
        float topLimit = height * 0.38f;
        float corridorHeight = Math.max(1f, height - topLimit);

        for (DetectedObject obj : objects) {
            Rect r = obj.getBoundingBox();
            float top = Math.max(r.top, topLimit);
            float bottom = Math.min(r.bottom, height);
            if (bottom <= top) continue;
            float proximity = 0.35f + 0.65f * clamp(r.bottom / (float) height, 0f, 1f);
            for (int z = 0; z < 3; z++) {
                float zl = z * zoneWidth;
                float zr = (z + 1) * zoneWidth;
                float overlapW = Math.max(0f, Math.min(r.right, zr) - Math.max(r.left, zl));
                float ratio = (overlapW * (bottom - top)) / Math.max(1f, zoneWidth * corridorHeight);
                blocked[z] += ratio * proximity * 2.2f;
            }
        }
        for (int i = 0; i < 3; i++) blocked[i] = clamp(blocked[i], 0f, 1f);

        float l = blocked[0], c = blocked[1], r = blocked[2];
        if (c > 0.58f && l > 0.50f && r > 0.50f) {
            return new Guidance("STOP", "■ DETENTE", "Detente. El paso parece bloqueado.", "Paso bloqueado por obstáculos.");
        }
        if (c < 0.18f) return new Guidance("STRAIGHT", "↑ SIGUE RECTO", "Sigue recto.", "Corredor central libre.");
        if (l + 0.08f < r && l + 0.06f < c) return new Guidance("LEFT", "← VE A LA IZQUIERDA", "Desvíate un poco a la izquierda.", "Corredor izquierdo más libre.");
        if (r + 0.08f < l && r + 0.06f < c) return new Guidance("RIGHT", "VE A LA DERECHA →", "Desvíate un poco a la derecha.", "Corredor derecho más libre.");
        return new Guidance("CAUTION", "↑ AVANZA CON CUIDADO", "Avanza con cuidado.", "Espacio limitado al frente.");
    }

    private void applyGuidance(Guidance g, boolean hazardSpeaking) {
        guideText.setText(g.display);
        if (!guidanceEnabled || !assistanceEnabled || hazardSpeaking) return;

        if (g.key.equals(guidanceCandidate)) guidanceCandidateCount++;
        else {
            guidanceCandidate = g.key;
            guidanceCandidateCount = 1;
        }
        if (guidanceCandidateCount < 2) return;

        long now = SystemClock.uptimeMillis();
        long repeat = "STRAIGHT".equals(g.key) ? 8000L : 4000L;
        if (g.key.equals(lastGuidance) && now - lastGuidanceAt < repeat) return;
        if (now - lastGuidanceAt < 1800L) return;

        lastGuidance = g.key;
        lastGuidanceAt = now;
        if ("LEFT".equals(g.key)) vibrateLeft();
        else if ("RIGHT".equals(g.key)) vibrateRight();
        else if ("STOP".equals(g.key)) vibrateDanger();
        speak(g.spoken);
    }

    private String labelFor(DetectedObject object) {
        if (object.getLabels() == null || object.getLabels().isEmpty()) return "Obstáculo";
        String raw = object.getLabels().get(0).getText();
        return raw == null || raw.trim().isEmpty() || "Unknown".equalsIgnoreCase(raw) ? "Obstáculo" : raw;
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

    private void vibrateDanger() { vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0,180,80,180,80,350}, -1)); }
    private void vibrateCaution() { vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0,120,120,120}, -1)); }
    private void vibrateLeft() { vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0,190}, -1)); }
    private void vibrateRight() { vibrator().vibrate(VibrationEffect.createWaveform(new long[]{0,90,90,90}, -1)); }

    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private void updateUi(String status, String detail) { statusText.setText(status); detailText.setText(detail); }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("es", "CL"));
            tts.setSpeechRate(1.03f);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else updateUi("Permiso de cámara requerido", "Sin cámara la app no puede analizar la vereda.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) detector.close();
        if (segmentationEngine != null) segmentationEngine.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }

    private static class Guidance {
        final String key;
        final String display;
        final String spoken;
        final String detail;
        Guidance(String key, String display, String spoken, String detail) {
            this.key = key;
            this.display = display;
            this.spoken = spoken;
            this.detail = detail;
        }
    }
}
