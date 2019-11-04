package com.example.djidemo.detection;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;


public abstract class Detector {

    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    public class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /**
         * Optional location within the source image for the location of the recognized object.
         */
        private RectF location;

        public int detectedClass;

        public Recognition(
                final String id, final String title, final Float confidence, final RectF location, int detectedClass) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.detectedClass = detectedClass;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }

    private long lastProcessingTimeMs;

    protected float mNmsThresh = 0.5f;
    protected List<String> mLabelList;

    protected static final int NUM_BOXES_PER_BLOCK = 3;

    protected static final int BATCH_SIZE = 1;
    protected static final int PIXEL_SIZE = 3;

    protected Interpreter mInterpreter;
    protected int mInputh,mInputw;

    protected int[][] mMasks;
    protected int[] mAnchors;
    protected int[][] mOutsize; //wh

    public Detector (AssetManager assetManager,
                     String modelPath,
                     String labelPath,
                     int height,int width) throws IOException {

        // Initialize interpreter with GPU delegate
        GpuDelegate delegate = new GpuDelegate();
        Interpreter.Options options = (new Interpreter.Options()).addDelegate(delegate);
//        Interpreter interpreter = new Interpreter(model, options);

        mInterpreter = new Interpreter(loadModelFile(assetManager, modelPath),options);
        mLabelList = loadLabelList(assetManager, labelPath);

        StringBuilder builder = new StringBuilder();
        for (String label: mLabelList) {
            builder.append(label).append(" ");
        }
        Log.d("wangmin", "Labels are:\n" + builder.toString());

        mInputh = height;
        mInputw = width;
    }

    //non maximum suppression
    protected ArrayList<Recognition> nms(ArrayList<Recognition> list) {
        ArrayList<Recognition> nmsList = new ArrayList<Recognition>();

        for (int k = 0; k < mLabelList.size(); k++) {
            //1.find max confidence per class
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<Recognition>(
                            10,
                            new Comparator<Recognition>() {
                                @Override
                                public int compare(final Recognition lhs, final Recognition rhs) {
                                    // Intentionally reversed to put high confidence at the head of the queue.
                                    return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                                }
                            });

            for (int i = 0; i < list.size(); ++i) {
                if (list.get(i).detectedClass == k) {
                    pq.add(list.get(i));
                }
            }
            Log.d("wangmin", "class[" + k + "] pq size: " + pq.size());

            //2.do non maximum suppression
            while(pq.size() > 0) {
                //insert detection with max confidence
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsList.add(max);

                Log.d("wangmin", "before nms pq size: " + pq.size());

                //clear pq to do next nms
                pq.clear();

                for (int j = 1; j < detections.length; j++) {
                    Recognition detection = detections[j];
                    RectF b = detection.getLocation();
                    if (box_iou(max.getLocation(), b) < mNmsThresh){
                        pq.add(detection);
                    }
                }
                Log.d("wangmin", "after nms pq size: " + pq.size());
            }
        }
        return nmsList;
    }

    public static float box_iou(RectF a, RectF b)
    {
        return box_intersection(a, b)/box_union(a, b);
    }

    public static float box_intersection(RectF a, RectF b)
    {
        float w = overlap((a.left + a.right) / 2, a.right - a.left,
                (b.left + b.right) / 2, b.right - b.left);
        float h = overlap((a.top + a.bottom) / 2, a.bottom - a.top,
                (b.top + b.bottom) / 2, b.bottom - b.top);
        if(w < 0 || h < 0) return 0;
        float area = w*h;
        return area;
    }

    public static float box_union(RectF a, RectF b)
    {
        float i = box_intersection(a, b);
        float u = (a.right - a.left)*(a.bottom - a.top) + (b.right - b.left)*(b.bottom - b.top) - i;
        return u;
    }

    public static float overlap(float x1, float w1, float x2, float w2)
    {
        float l1 = x1 - w1/2;
        float l2 = x2 - w2/2;
        float left = l1 > l2 ? l1 : l2;
        float r1 = x1 + w1/2;
        float r2 = x2 + w2/2;
        float right = r1 < r2 ? r1 : r2;
        return right - left;
    }

    public void close() {
        mInterpreter.close();
        mInterpreter = null;
    }

    protected void softmax(final float[] vals) {
        float max = Float.NEGATIVE_INFINITY;
        for (final float val : vals) {
            max = Math.max(max, val);
        }
        float sum = 0.0f;
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = (float) Math.exp(vals[i] - max);
            sum += vals[i];
        }
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = vals[i] / sum;
        }
    }

    protected float expit(final float x) {

//        return Math.min(Math.max(x+3,0),6) / 6;
        return (float) (1. / (1. + Math.exp(-x)));
    }

    protected MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
//        InputStream fileDescriptor = assetManager.open(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
//        return fileDescriptor;
    }

    /** Writes Image data into a {@code ByteBuffer}. */
    protected ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * mInputw * mInputh * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[mInputw * mInputh];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < mInputw; ++i) {
            for (int j = 0; j < mInputh; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                byteBuffer.putFloat((val & 0xFF) / 255.0f);
            }
        }
        return byteBuffer;
    }

    public ArrayList<Recognition> RecognizeImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);

        Map<Integer, Object> outputMap = new HashMap<>();
        for (int i = 0; i < mOutsize.length; i++) {
            //shuchu 1 h w 21
            float[][][][] out = new float[1][mOutsize[i][1]][mOutsize[i][0]][3 * (5 + mLabelList.size())];
            outputMap.put(i, out);
        }




        Log.d("wangmin", "mObjThresh: " + getObjThresh());

        long startTime = SystemClock.uptimeMillis();
        Object[] inputArray = {byteBuffer};
        mInterpreter.runForMultipleInputsOutputs(inputArray, outputMap);///////////////////////////////////////////////运行模型
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
        Log.d("time", "run model time " + lastProcessingTimeMs);

        ArrayList<Recognition> detections = new ArrayList<Recognition>();

        startTime = SystemClock.uptimeMillis();

        for (int i = 0; i < mOutsize.length; i++) {
            int gridWidth = mOutsize[i][0];
            int gridHeight = mOutsize[i][1];
            float[][][][] out = (float[][][][])outputMap.get(i);

            Log.d("wangmin", "out[" + i + "] detect start");
            for (int y = 0; y < gridHeight; ++y) {
                for (int x = 0; x < gridWidth; ++x) {
                    for (int b = 0; b < NUM_BOXES_PER_BLOCK; ++b) {

                        // offset不知道干啥,第几个框

                        final int offset =
                                (gridWidth * (NUM_BOXES_PER_BLOCK * (mLabelList.size() + 5))) * y
                                        + (NUM_BOXES_PER_BLOCK * (mLabelList.size() + 5)) * x
                                        + (mLabelList.size() + 5) * b;

                        final float confidence = expit(out[0][y][x][b* (5 + mLabelList.size()) + 4]);
                        int detectedClass = -1;
                        float maxClass = 0;

                        final float[] classes = new float[mLabelList.size()];
                        for (int c = 0; c < mLabelList.size(); ++c) {
                            classes[c] = expit(out[0][y][x][b * (5 + mLabelList.size()) + 5+c]);

                        }
//                        softmax(classes);

                        for (int c = 0; c < mLabelList.size(); ++c) {
                            if (classes[c] > maxClass) {
                                detectedClass = c;
                                maxClass = classes[c];
                            }
                        }

                        final float confidenceInClass = maxClass * confidence;

                        if (confidenceInClass > getObjThresh()) {
                            final float xPos = (x + expit(out[0][y][x][b* (5 + mLabelList.size()) + 0])) * (mInputw / gridWidth);
                            final float yPos = (y + expit(out[0][y][x][b* (5 + mLabelList.size()) + 1])) * (mInputh / gridHeight);

                            final float w = (float) (Math.exp(out[0][y][x][b* (5 + mLabelList.size()) + 2]) * mAnchors[2 * mMasks[i][b] + 0]);
                            final float h = (float) (Math.exp(out[0][y][x][b* (5 + mLabelList.size()) + 3]) * mAnchors[2 * mMasks[i][b] + 1]);

                            Log.d("wangmin","box x:" + xPos + ", y:" + yPos + ", w:" + w + ", h:" + h);

                            final RectF rect =
                                    new RectF(
                                            Math.max(0, xPos - w / 2),
                                            Math.max(0, yPos - h / 2),
                                            Math.min(bitmap.getWidth() - 1, xPos + w / 2),
                                            Math.min(bitmap.getHeight() - 1, yPos + h / 2));
                            Log.d("wangmin", "detect " + mLabelList.get(detectedClass)
                                    + ", confidence: " + confidenceInClass
                                    + ", box: " + rect.toString());
                            detections.add(new Recognition("" + offset, mLabelList.get(detectedClass),
                                    confidenceInClass, rect, detectedClass));
                        }
                    }
                }
            }
            Log.d("wangmin", "out[" + i + "] detect end");
        }

        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

        Log.d("time", "explain result time " + lastProcessingTimeMs);

        startTime = SystemClock.uptimeMillis();
        final ArrayList<Recognition> recognitions = nms(detections);
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
        Log.d("time", "nms time " + lastProcessingTimeMs);
        return recognitions;
    }

    protected List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    public int[] getInputSize() {
        int [] size =new int[] {mInputw,mInputh};
        return size;
    }

    protected abstract float getObjThresh();
}
