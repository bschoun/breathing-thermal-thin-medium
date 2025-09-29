/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Source: https://github.com/tensorflow/examples/blob/master/lite/examples/image_classification/android_java/app/src/main/java/org/tensorflow/lite/examples/imageclassification/ImageClassifierHelper.java
 */

package com.bschoun.godot.seekthermal;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Helper class for wrapping Image Classification actions with raw Interpreter */
public class ImageClassifierHelper {
    private static final String TAG = "ImageClassifierHelper";
    private static final int DELEGATE_CPU = 0;
    private static final int DELEGATE_GPU = 1;
    private static final int DELEGATE_NNAPI = 2;

    private final float threshold;
    private final int numThreads;
    private final int maxResults;
    private final int currentDelegate;
    private final Context context;
    private final ClassifierListener listener;

    private Interpreter interpreter;
    private Delegate delegate;
    private List<String> labels;

    // Paths inside assets
    private String modelPath = "converted_tflite/model_unquant.tflite";
    private String labelPath = "converted_tflite/labels.txt";

    public ImageClassifierHelper(Float threshold,
                                 int numThreads,
                                 int maxResults,
                                 int currentDelegate,
                                 Context context,
                                 ClassifierListener listener) {
        this.threshold = threshold;
        this.numThreads = numThreads;
        this.maxResults = maxResults;
        this.currentDelegate = currentDelegate;
        this.context = context;
        this.listener = listener;
        setupInterpreter();
    }

    public static ImageClassifierHelper create(Context context, ClassifierListener listener) {
        return new ImageClassifierHelper(0.5f, 2, 3, DELEGATE_CPU, context, listener);
    }

    /** Load model and configure interpreter */
    private void setupInterpreter() {
        try {
            MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(context, modelPath);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(numThreads);

            switch (currentDelegate) {
                case DELEGATE_GPU:
                    delegate = new GpuDelegate();
                    options.addDelegate(delegate);
                    break;
                case DELEGATE_NNAPI:
                    delegate = new NnApiDelegate();
                    options.addDelegate(delegate);
                    break;
                case DELEGATE_CPU:
                default:
                    // no delegate
            }

            interpreter = new Interpreter(modelBuffer, options);
            labels = FileUtil.loadLabels(context, labelPath);

        } catch (IOException e) {
            listener.onError("Failed to load TFLite model or labels: " + e.getMessage());
            Log.e(TAG, "TFLite failed to load", e);
        }
    }

    /** Run classification */
    public void classify(Bitmap bitmap, int imageRotation) {
        if (interpreter == null) {
            setupInterpreter();
            if (interpreter == null) return;
        }

        long startTime = SystemClock.uptimeMillis();

        Log.d(TAG, "Creating imageProcessor");

        // Preprocess: rotate + normalize to [-1,1]
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new Rot90Op(-imageRotation / 90))
                        .add(new NormalizeOp(127.5f, 127.5f)) // scale to [-1,1]
                        .build();

        Log.d(TAG, "Creating tensorImage");
        TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap));

        // Allocate output buffer: [1, NUM_CLASSES]
        Log.d(TAG, "Setting up output");
        int[] outputShape = interpreter.getOutputTensor(0).shape(); // e.g. [1,1001]
        float[][] output = new float[outputShape[0]][outputShape[1]];

        Log.d(TAG, "Running inference");
        // Run inference
        interpreter.run(tensorImage.getBuffer(), output);

        long inferenceTime = SystemClock.uptimeMillis() - startTime;

        Log.d(TAG, "Creating results");

        // Postprocess: extract top-K classes
        List<Classification> results = getTopK(output[0], maxResults, threshold);

        listener.onResults(results, inferenceTime);
    }

    /** Extract top-K results above threshold */
    private List<Classification> getTopK(float[] scores, int topK, float threshold) {
        List<Classification> all = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= threshold) {
                String label = (labels != null && i < labels.size()) ? labels.get(i) : "Unknown";
                all.add(new Classification(i, label, scores[i]));
            }
        }
        Collections.sort(all, new Comparator<Classification>() {
            @Override
            public int compare(Classification a, Classification b) {
                return Float.compare(b.score, a.score);
            }
        });
        if (all.size() > topK) {
            return all.subList(0, topK);
        }
        return all;
    }

    public void clearInterpreter() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }
    }

    /** Simple result object */
    public static class Classification {
        public final int index;
        public final String label;
        public final float score;

        public Classification(int index, String label, float score) {
            this.index = index;
            this.label = label;
            this.score = score;
        }

        @Override
        public String toString() {
            return label + " (" + score + ")";
        }
    }

    /** Listener for passing results back to calling class */
    public interface ClassifierListener {
        void onError(String error);
        void onResults(List<Classification> results, long inferenceTime);
    }
}
