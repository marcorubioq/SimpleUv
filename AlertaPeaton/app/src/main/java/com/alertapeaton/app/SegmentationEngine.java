package com.alertapeaton.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public final class SegmentationEngine implements AutoCloseable {
    private static final int INPUT = 1024;
    private static final int OUTPUT = 128;
    private static final int CLASSES = 19;
    private static final int HW = OUTPUT * OUTPUT;

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private final Interpreter interpreter;
    private final ByteBuffer input;
    private final ByteBuffer output;

    public SegmentationEngine(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        options.setUseXNNPACK(true);
        interpreter = new Interpreter(loadModel(context, "pidnet_s.tflite"), options);
        input = ByteBuffer.allocateDirect(INPUT * INPUT * 3 * 4).order(ByteOrder.nativeOrder());
        output = ByteBuffer.allocateDirect(CLASSES * OUTPUT * OUTPUT * 4).order(ByteOrder.nativeOrder());
    }

    private static MappedByteBuffer loadModel(Context context, String assetName) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(assetName);
        try (FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            return stream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(),
                    fd.getDeclaredLength());
        }
    }

    public Result analyze(ImageProxy image) {
        fillInput(image);
        output.rewind();
        interpreter.run(input, output);
        return summarizeOutput();
    }

    private void fillInput(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer y = planes[0].getBuffer();
        ByteBuffer u = planes[1].getBuffer();
        ByteBuffer v = planes[2].getBuffer();

        int srcW = image.getWidth();
        int srcH = image.getHeight();
        int rotation = image.getImageInfo().getRotationDegrees();
        int orientedW = (rotation == 90 || rotation == 270) ? srcH : srcW;
        int orientedH = (rotation == 90 || rotation == 270) ? srcW : srcH;

        int yRow = planes[0].getRowStride();
        int yPix = planes[0].getPixelStride();
        int uRow = planes[1].getRowStride();
        int uPix = planes[1].getPixelStride();
        int vRow = planes[2].getRowStride();
        int vPix = planes[2].getPixelStride();

        input.clear();
        final int planeBytes = INPUT * INPUT * 4;

        for (int ty = 0; ty < INPUT; ty++) {
            int oy = Math.min(orientedH - 1, (int) (((ty + 0.5f) / INPUT) * orientedH));
            for (int tx = 0; tx < INPUT; tx++) {
                int ox = Math.min(orientedW - 1, (int) (((tx + 0.5f) / INPUT) * orientedW));

                int sx;
                int sy;
                if (rotation == 90) {
                    sx = oy;
                    sy = srcH - 1 - ox;
                } else if (rotation == 180) {
                    sx = srcW - 1 - ox;
                    sy = srcH - 1 - oy;
                } else if (rotation == 270) {
                    sx = srcW - 1 - oy;
                    sy = ox;
                } else {
                    sx = ox;
                    sy = oy;
                }

                sx = clampInt(sx, 0, srcW - 1);
                sy = clampInt(sy, 0, srcH - 1);

                int yi = clampInt(sy * yRow + sx * yPix, 0, y.limit() - 1);
                int uvx = sx / 2;
                int uvy = sy / 2;
                int ui = clampInt(uvy * uRow + uvx * uPix, 0, u.limit() - 1);
                int vi = clampInt(uvy * vRow + uvx * vPix, 0, v.limit() - 1);

                int yy = y.get(yi) & 0xff;
                int uu = (u.get(ui) & 0xff) - 128;
                int vv = (v.get(vi) & 0xff) - 128;

                float rf = clamp01((yy + 1.402f * vv) / 255f);
                float gf = clamp01((yy - 0.344136f * uu - 0.714136f * vv) / 255f);
                float bf = clamp01((yy + 1.772f * uu) / 255f);

                int p = ty * INPUT + tx;
                input.putFloat(p * 4, (rf - MEAN[0]) / STD[0]);
                input.putFloat(planeBytes + p * 4, (gf - MEAN[1]) / STD[1]);
                input.putFloat(2 * planeBytes + p * 4, (bf - MEAN[2]) / STD[2]);
            }
        }
        input.rewind();
    }

    private Result summarizeOutput() {
        int[] sidewalk = new int[3];
        int[] road = new int[3];
        int[] total = new int[3];
        int[] classes = new int[CLASSES];

        int startY = 46;
        for (int oy = startY; oy < OUTPUT; oy += 2) {
            for (int ox = 0; ox < OUTPUT; ox += 2) {
                int idx = oy * OUTPUT + ox;
                int best = 0;
                float bestVal = output.getFloat(idx * 4);
                for (int c = 1; c < CLASSES; c++) {
                    float val = output.getFloat((c * HW + idx) * 4);
                    if (val > bestVal) {
                        bestVal = val;
                        best = c;
                    }
                }
                int zone = Math.min(2, (ox * 3) / OUTPUT);
                total[zone]++;
                classes[best]++;
                if (best == 0) road[zone]++;
                if (best == 1) sidewalk[zone]++;
            }
        }

        float[] sidewalkRatio = new float[3];
        float[] roadRatio = new float[3];
        for (int z = 0; z < 3; z++) {
            sidewalkRatio[z] = total[z] == 0 ? 0f : sidewalk[z] / (float) total[z];
            roadRatio[z] = total[z] == 0 ? 0f : road[z] / (float) total[z];
        }

        int sampled = classes[0] + classes[1] + classes[2] + classes[3] + classes[4] + classes[5] +
                classes[6] + classes[7] + classes[8] + classes[9] + classes[10] + classes[11] +
                classes[12] + classes[13] + classes[14] + classes[15] + classes[16] + classes[17] + classes[18];

        float vehicleRatio = sampled == 0 ? 0f :
                (classes[13] + classes[14] + classes[15] + classes[17] + classes[18]) / (float) sampled;
        float personRatio = sampled == 0 ? 0f : (classes[11] + classes[12]) / (float) sampled;
        return new Result(sidewalkRatio, roadRatio, vehicleRatio, personRatio, System.currentTimeMillis());
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    public void close() {
        interpreter.close();
    }

    public static final class Result {
        public final float[] sidewalk;
        public final float[] road;
        public final float vehicleRatio;
        public final float personRatio;
        public final long timestampMs;

        Result(float[] sidewalk, float[] road, float vehicleRatio, float personRatio, long timestampMs) {
            this.sidewalk = sidewalk;
            this.road = road;
            this.vehicleRatio = vehicleRatio;
            this.personRatio = personRatio;
            this.timestampMs = timestampMs;
        }

        public String debugText() {
            return "Vereda I " + pct(sidewalk[0]) + "% · C " + pct(sidewalk[1]) + "% · D " + pct(sidewalk[2]) +
                    "% | Calzada C " + pct(road[1]) + "%";
        }

        private int pct(float v) {
            return Math.round(v * 100f);
        }
    }
}
