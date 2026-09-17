package com.alertapeaton.app;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GpsOverlayApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private final WeakHashMap<Activity, GpsPanel> panels = new WeakHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        GpsPanel panel = panels.get(activity);
        if (panel == null) {
            panel = new GpsPanel(activity);
            panels.put(activity, panel);
            panel.attach();
        }
        panel.onResume();
    }

    @Override public void onActivityPaused(@NonNull Activity activity) {
        GpsPanel panel = panels.get(activity);
        if (panel != null) panel.onPause();
    }

    @Override public void onActivityDestroyed(@NonNull Activity activity) {
        GpsPanel panel = panels.remove(activity);
        if (panel != null) panel.destroy();
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityStarted(@NonNull Activity activity) {}
    @Override public void onActivityStopped(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    private static final class GpsPanel {
        private static final int LOCATION_PERMISSION = 43;
        private final Activity activity;
        private final FusedLocationProviderClient fused;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();
        private final Geocoder geocoder;

        private FrameLayout host;
        private LinearLayout card;
        private TextView locationText;
        private EditText destinationEdit;
        private Button gpsButton;
        private boolean trackingRequested = false;
        private boolean tracking = false;
        private Location lastLocation;
        private long lastGeocodeAt = 0L;
        private Location lastGeocodedLocation;

        private final LocationCallback callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) updateLocation(location);
            }
        };

        GpsPanel(Activity activity) {
            this.activity = activity;
            this.fused = LocationServices.getFusedLocationProviderClient(activity);
            this.geocoder = new Geocoder(activity, new Locale("es", "CL"));
        }

        void attach() {
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout)) return;
            host = (FrameLayout) content;

            LinearLayout wrapper = new LinearLayout(activity);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setGravity(Gravity.END);

            gpsButton = new Button(activity);
            gpsButton.setText("📍 GPS");
            gpsButton.setTextSize(14);
            gpsButton.setAllCaps(false);
            gpsButton.setOnClickListener(v -> {
                boolean show = card.getVisibility() != View.VISIBLE;
                card.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) enableGps();
            });
            LinearLayout.LayoutParams gpsLp = new LinearLayout.LayoutParams(dp(108), dp(48));
            wrapper.addView(gpsButton, gpsLp);

            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(12));
            card.setBackgroundColor(0xE6000000);
            card.setVisibility(View.GONE);

            TextView title = text("UBICACIÓN Y RUTA", 15, Color.WHITE);
            title.setGravity(Gravity.CENTER);
            card.addView(title, new LinearLayout.LayoutParams(-1, -2));

            locationText = text("GPS apagado. Toca Activar GPS.", 13, 0xFFE0E0E0);
            locationText.setPadding(0, dp(5), 0, dp(7));
            card.addView(locationText, new LinearLayout.LayoutParams(-1, -2));

            Button activate = new Button(activity);
            activate.setText("Activar GPS");
            activate.setAllCaps(false);
            activate.setOnClickListener(v -> enableGps());
            card.addView(activate, new LinearLayout.LayoutParams(-1, dp(44)));

            destinationEdit = new EditText(activity);
            destinationEdit.setHint("Destino o lugar: metro, farmacia, dirección…");
            destinationEdit.setHintTextColor(0xFF9E9E9E);
            destinationEdit.setTextColor(Color.WHITE);
            destinationEdit.setSingleLine(true);
            LinearLayout.LayoutParams destLp = new LinearLayout.LayoutParams(-1, dp(48));
            destLp.topMargin = dp(6);
            card.addView(destinationEdit, destLp);

            LinearLayout row1 = new LinearLayout(activity);
            row1.setOrientation(LinearLayout.HORIZONTAL);

            Button map = new Button(activity);
            map.setText("Ver mapa");
            map.setAllCaps(false);
            map.setOnClickListener(v -> openCurrentLocation());
            row1.addView(map, new LinearLayout.LayoutParams(0, dp(46), 1f));

            Button walk = new Button(activity);
            walk.setText("Ruta a pie");
            walk.setAllCaps(false);
            walk.setOnClickListener(v -> openWalkingDirections());
            LinearLayout.LayoutParams walkLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            walkLp.leftMargin = dp(5);
            row1.addView(walk, walkLp);
            card.addView(row1, new LinearLayout.LayoutParams(-1, -2));

            Button waze = new Button(activity);
            waze.setText("Abrir en Waze (referencia vehicular)");
            waze.setAllCaps(false);
            waze.setOnClickListener(v -> openWaze());
            LinearLayout.LayoutParams wazeLp = new LinearLayout.LayoutParams(-1, dp(46));
            wazeLp.topMargin = dp(5);
            card.addView(waze, wazeLp);

            TextView note = text("El GPS y el mapa orientan la ruta; la cámara sigue verificando vereda y obstáculos.", 11, 0xFFBDBDBD);
            note.setPadding(0, dp(6), 0, 0);
            card.addView(note, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(330), -2);
            cardLp.topMargin = dp(4);
            wrapper.addView(card, cardLp);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            lp.topMargin = dp(178);
            lp.rightMargin = dp(10);
            host.addView(wrapper, lp);
        }

        void onResume() {
            if (trackingRequested && hasLocationPermission()) startLocationUpdates();
        }

        void onPause() {
            stopLocationUpdates();
        }

        void destroy() {
            stopLocationUpdates();
            geocodeExecutor.shutdownNow();
        }

        private void enableGps() {
            trackingRequested = true;
            card.setVisibility(View.VISIBLE);
            if (!hasLocationPermission()) {
                locationText.setText("Autoriza ubicación para conectar el GPS con mapas.");
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        LOCATION_PERMISSION);
                pollPermission(0);
                return;
            }
            startLocationUpdates();
        }

        private void pollPermission(int attempt) {
            if (!trackingRequested || activity.isFinishing()) return;
            if (hasLocationPermission()) {
                startLocationUpdates();
                return;
            }
            if (attempt < 20) handler.postDelayed(() -> pollPermission(attempt + 1), 750L);
        }

        private boolean hasLocationPermission() {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        private void startLocationUpdates() {
            if (tracking || !hasLocationPermission()) return;
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                    .setMinUpdateIntervalMillis(1000L)
                    .setMinUpdateDistanceMeters(1.5f)
                    .build();
            try {
                fused.requestLocationUpdates(request, callback, Looper.getMainLooper());
                tracking = true;
                gpsButton.setText("📍 GPS ON");
                locationText.setText("Buscando ubicación GPS…");
                fused.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) updateLocation(location);
                });
            } catch (SecurityException ignored) {
                tracking = false;
                locationText.setText("No se pudo iniciar GPS. Revisa el permiso de ubicación.");
            }
        }

        private void stopLocationUpdates() {
            if (!tracking) return;
            fused.removeLocationUpdates(callback);
            tracking = false;
        }

        private void updateLocation(Location location) {
            lastLocation = location;
            String precision = location.hasAccuracy() ? " ±" + Math.round(location.getAccuracy()) + " m" : "";
            locationText.setText(String.format(Locale.US,
                    "GPS%s\n%.6f, %.6f\nBuscando calle…",
                    precision, location.getLatitude(), location.getLongitude()));
            maybeReverseGeocode(location);
        }

        private void maybeReverseGeocode(Location location) {
            long now = System.currentTimeMillis();
            boolean moved = lastGeocodedLocation == null || lastGeocodedLocation.distanceTo(location) > 20f;
            if (!moved && now - lastGeocodeAt < 15000L) return;
            lastGeocodeAt = now;
            lastGeocodedLocation = new Location(location);

            geocodeExecutor.execute(() -> {
                String address = null;
                try {
                    List<Address> results = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (results != null && !results.isEmpty()) {
                        Address a = results.get(0);
                        String line = a.getAddressLine(0);
                        if (line != null && !line.trim().isEmpty()) address = line;
                    }
                } catch (IOException | RuntimeException ignored) {}

                final String resolved = address;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || lastLocation == null) return;
                    String precision = lastLocation.hasAccuracy() ? " ±" + Math.round(lastLocation.getAccuracy()) + " m" : "";
                    String base = String.format(Locale.US, "GPS%s\n%.6f, %.6f",
                            precision, lastLocation.getLatitude(), lastLocation.getLongitude());
                    locationText.setText(resolved == null ? base : base + "\n" + resolved);
                });
            });
        }

        private void openCurrentLocation() {
            if (!ensureLocation()) return;
            String point = String.format(Locale.US, "%.7f,%.7f", lastLocation.getLatitude(), lastLocation.getLongitude());
            Uri uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(point));
            openUri(uri);
        }

        private void openWalkingDirections() {
            String destination = destinationEdit.getText().toString().trim();
            if (destination.isEmpty()) {
                Toast.makeText(activity, "Escribe un destino o lugar.", Toast.LENGTH_SHORT).show();
                destinationEdit.requestFocus();
                return;
            }
            if (!ensureLocation()) return;
            String origin = String.format(Locale.US, "%.7f,%.7f", lastLocation.getLatitude(), lastLocation.getLongitude());
            Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1" +
                    "&origin=" + Uri.encode(origin) +
                    "&destination=" + Uri.encode(destination) +
                    "&travelmode=walking&dir_action=navigate");
            openUri(uri);
        }

        private void openWaze() {
            String destination = destinationEdit.getText().toString().trim();
            Uri uri;
            if (!destination.isEmpty()) {
                uri = Uri.parse("https://waze.com/ul?q=" + Uri.encode(destination) + "&navigate=yes");
            } else if (lastLocation != null) {
                String point = String.format(Locale.US, "%.7f,%.7f", lastLocation.getLatitude(), lastLocation.getLongitude());
                uri = Uri.parse("https://waze.com/ul?ll=" + Uri.encode(point));
            } else {
                Toast.makeText(activity, "Activa GPS o escribe un destino.", Toast.LENGTH_SHORT).show();
                return;
            }
            openUri(uri);
        }

        private boolean ensureLocation() {
            if (lastLocation != null) return true;
            enableGps();
            Toast.makeText(activity, "Esperando posición GPS…", Toast.LENGTH_SHORT).show();
            return false;
        }

        private void openUri(Uri uri) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(activity, "No hay una app de mapas disponible.", Toast.LENGTH_SHORT).show();
            }
        }

        private TextView text(String value, int size, int color) {
            TextView t = new TextView(activity);
            t.setText(value);
            t.setTextSize(size);
            t.setTextColor(color);
            return t;
        }

        private int dp(int value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }
    }
}
