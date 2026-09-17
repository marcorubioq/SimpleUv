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
import android.view.View;
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
    private Switch assistSwitch;
    private ExecutorService cameraExecutor;
    private ObjectDetector detector;
    private TextToSpeech tts;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private boolean assistanceEnabled = true;
    private long lastAlertAt = 0L;
    private String lastAlert = "";
    private float previousCentralArea = 0f;
    private long previousCentralTime = 0L;

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
            assistSwitch.setText(checked ? "Asistencia activa" : "Asistencia pausada");
            if (checked) speak("Asistencia activada");
        });

        Button testButton = findViewById(1005);
        testButton.setOnClickListener(v -> {
            vibrateDanger();
            speak("Prueba de alerta. Obstáculo aproximándose por la izquierda");
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
        detailText.setText("La app analizará objetos frente a ti.");
        detailText.setTextColor(0xFFE0E0E0);
        detailText.setTextSize(16);
        detailText.setPadding(0, dp(6), 0, 0);
        top.addView(detailText, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(top, topParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(14), dp(16), dp(20));
        bottom.setBackgroundColor(0xD9000000);

        assistSwitch = new Switch(this);
        assistSwitch.setChecked(true);
        assistSwitch.setText("Asistencia activa");
        assistSwitch.setTextColor(Color.WHITE);
        assistSwitch.setTextSize(19);
        assistSwitch.setMinHeight(dp(56));
        bottom.addView(assistSwitch, new LinearLayout.LayoutParams(-1, -2));

        Button testButton = new Button(this);
        testButton.setId(1005);
        testButton.setText("Probar voz y vibración");
        testButton.setTextSize(18);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(58));
        bp.topMargin = dp(10);
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
                runOnUiThread(() -> updateUi("Cámara activa", "Buscando obstáculos y movimiento frente a ti…"));
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
        if (objects == null || objects.isEmpty()) {
            runOnUiThread(() -> updateUi("Trayectoria despejada", "Sin objetos relevantes al frente"));
            previousCentralArea = 0f;
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
            runOnUiThread(() -> updateUi("Atención lateral", objects.size() + " objeto(s) detectado(s), fuera de la trayectoria central"));
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
            status = "Objeto detectado";
            detail = label + " a " + side + ". Riesgo bajo por ahora.";
            danger = false;
            caution = false;
        }

        runOnUiThread(() -> {
            updateUi(status, detail);
            if (!assistanceEnabled) return;
            if (danger) alert(detail, true);
            else if (caution) alert(detail, false);
        });
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

    private void updateUi(String status, String detail) {
        statusText.setText(status);
        detailText.setText(detail);
        statusText.announceForAccessibility(status + ". " + detail);
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
            updateUi("Permiso de cámara requerido", "Sin cámara la app no puede detectar obstáculos.");
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
}
