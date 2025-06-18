package com.bschoun.godot.seekthermal;

// Graphics
import android.content.res.AssetManager;
import android.graphics.Bitmap;

// Seek Thermal
import com.thermal.seekware.SeekCamera;
import com.thermal.seekware.SeekCameraManager;
import com.thermal.seekware.SeekImage;
import com.thermal.seekware.SeekImageReader;
import com.thermal.seekware.SeekUtility;

// Godot
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.Dictionary;
import org.godotengine.godot.plugin.UsedByGodot;

// OpenCV
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.Core;
import org.opencv.core.Scalar;
import org.opencv.core.Rect;

// Tensorflow
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.support.label.Category;

// Java
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

// Android
import android.graphics.BitmapFactory;
import android.util.Log;
import androidx.annotation.NonNull;

public class SeekThermalGodotAndroidPlugin extends GodotPlugin
        implements SeekImageReader.OnImageAvailableListener, ImageClassifierHelper.ClassifierListener {

    private final SeekCameraManager seekCameraManager;
    private final SeekImageReader seekImageReader;        // Access to images without rendering

    private SeekCamera seekCamera;

    // Image configurations
    private java.nio.ByteBuffer bitmapBytes = null; // Buffer to hold bitmap
    private Bitmap bitmap = null;           // Hardware bitmap (to be converted to software bitmap)
    private Bitmap processingBitmap;

    // Prepare for input into classifier
    // Make image square

    private boolean xFlip = false;
    private boolean yFlip = false;

    private int yOffset;

    // Camera info
    private int width;
    private int height;
    private String cameraInfoText;

    private Mat processingMatGray;

    private Mat processingMatGrayMask;

    private Mat processingMatColor;
    private Mat roi;
    private Mat shortMat; // Intermediate data in 16-bit shorts
    private Mat floatMat; // Data as 32-bit floats after conversion to actual values (in C)

    private Mat scaled;

    private Rect roiRect;

    private Mat mask;

    private byte[] scaledBytes;

    private byte[] scaledSquareBytes;
    private short[] shortArray;


    // List of color palettes that can be indexed (because you can't cast Java enums to ints?)
    private final SeekCamera.ColorPalette[] colorPallets = {
        SeekCamera.ColorPalette.WHITEHOT,
        SeekCamera.ColorPalette.BLACKHOT,
        SeekCamera.ColorPalette.SPECTRA,
        SeekCamera.ColorPalette.PRISM,
        SeekCamera.ColorPalette.TYRIAN,
        SeekCamera.ColorPalette.IRON,
        SeekCamera.ColorPalette.AMBER,
        SeekCamera.ColorPalette.HI,
        SeekCamera.ColorPalette.HILO,
        SeekCamera.ColorPalette.IRON2,
        SeekCamera.ColorPalette.GREEN,
        SeekCamera.ColorPalette.RECON,
        SeekCamera.ColorPalette.BLACK_RECON,
        SeekCamera.ColorPalette.USER0,
        SeekCamera.ColorPalette.USER1,
        SeekCamera.ColorPalette.USER2,
        SeekCamera.ColorPalette.USER3,
        SeekCamera.ColorPalette.USER4
    };

    private enum CameraState {
        INITIALIZED,
        OPENED,
        STARTED,
        STOPPED,
        CLOSED
    }

    // TODO: Make these editable from Godot?
    private final float MIN_TEMP = 20.0f;
    private final float MAX_TEMP = 35.0f;

    private CameraState state;

    // Initialize to color 0
    private SeekCamera.ColorPalette _palette = colorPallets[0];

    private ImageClassifierHelper imageClassifierHelper;
    private List<Category> classifications;

    // Keep track of whether we're exhaling or not (inhaling, holding breath)
    private boolean exhaling = false;
    private float prevMaxMin = -1;
    private float maxMin = -1;
    private static final float maxMinThresh = 0.5f;



    /// Thermal Camera things ///

    // Callback adapter allows us to run specific functions when the camera changes state
    SeekCamera.StateCallback stateCallback = new SeekCamera.StateCallbackAdapter() {
        @Override
        public synchronized void onOpened(SeekCamera sc) {
            Log.d(getPluginName(), "Camera opened");
            seekCamera = sc;

            // Get details from camera
            cameraInfoText = seekCamera.toString();
            width = seekCamera.getCharacteristics().getWidth();
            height = seekCamera.getCharacteristics().getHeight();

            // Initialize all of our data storage
            shortArray = new short[width*height];
            shortMat = new Mat(height, width, CvType.CV_16UC1);
            floatMat = new Mat(height, width, CvType.CV_32F);
            scaled = new Mat(height, width, CvType.CV_8U);
            scaledBytes = new byte[width * height];
            scaledSquareBytes = new byte[width * width];
            processingBitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
            processingMatGray = new Mat(width, width, CvType.CV_8U);
            processingMatGrayMask = new Mat(width, width, CvType.CV_8U);
            processingMatColor = new Mat(width, width, CvType.CV_8UC3);
            yOffset = (width - height)/2;
            //roi = processingMatGray.submat(yOffset, yOffset + height - 1, 0, width - 1);
            roiRect = new Rect(0, yOffset, width, height);

            // Set up the mask
            mask = new Mat(width, width, CvType.CV_8U);
            AssetManager assetManager = getActivity().getAssets();
            InputStream istr = null;
            try {
                istr = assetManager.open("mask.jpg");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Bitmap bmp = BitmapFactory.decodeStream(istr);
            Mat m = new Mat();
            Utils.bitmapToMat(bmp, m);
            Imgproc.cvtColor(m, mask, Imgproc.COLOR_BGR2GRAY);

            // Our state is OPENED
            state = CameraState.OPENED;

            // Tell Godot the camera is opened
            emitSignal("camera_opened");
        }

        @Override
        public synchronized void onStarted(SeekCamera sc) {
            Log.d(getPluginName(), "Camera started");
            state = CameraState.STARTED;
            emitSignal("camera_started");
        }

        @Override
        public synchronized void onStopped(SeekCamera sc) {
            Log.d(getPluginName(), "Camera stopped");
            state = CameraState.STOPPED;
            emitSignal("camera_stopped");
        }

        @Override
        public synchronized void onClosed(SeekCamera sc) {
            Log.d(getPluginName(), "Camera closed");
            state = CameraState.CLOSED;
            emitSignal("camera_closed");
        }

        @Override
        public synchronized void onInitialized(SeekCamera sc) {
            Log.d(getPluginName(), "Camera initialized");
            state = CameraState.INITIALIZED;
            emitSignal("camera_initialized");
        }
    };

    //region Thermal camera Godot functions

    @UsedByGodot
    public int getCameraCount() {
        return seekCameraManager.getUSBDeviceCount();
    }

    @UsedByGodot
    public void suspendShutter() {
        seekCamera.suspendShutter();
    }

    @UsedByGodot
    public void triggerShutter() {
        seekCamera.triggerShutter();
    }

    @UsedByGodot
    public void resumeShutter() {
        seekCamera.resumeShutter();
    }

    @UsedByGodot
    public boolean isAutomaticShutter() {
        return seekCamera.isAutomaticShutter();
    }

    @UsedByGodot
    public void setImageSmoothing(boolean value) {
        seekCamera.setImageSmoothing(value);
    }

    @UsedByGodot
    public boolean getImageSmoothing() {
        return seekCamera.getImageSmoothing();
    }

    @UsedByGodot
    public void setEmissivity(float emissivity) {
        seekCamera.setEmissivity(emissivity);
    }

    @UsedByGodot
    public float getEmissivity() {
        return seekCamera.getEmissivity();
    }

    @UsedByGodot
    public void startCamera() {
        if (state != CameraState.OPENED && state != CameraState.STOPPED) {
            Log.d(getPluginName(), "Invalid camera state, cannot start camera.");
            return;
        }
        seekCamera.createSeekCameraCaptureSession(false, true, true, seekImageReader);
        seekCamera.setColorPalette(_palette);
    }

    @UsedByGodot
    public void stopCamera() {
        if (state != CameraState.STARTED) {
            Log.d(getPluginName(), "Invalid camera state, cannot stop camera.");
        }
        seekCamera.stop();
    }

    @UsedByGodot
    public void setXFlip(boolean flipped) {
        xFlip = flipped;
    }

    @UsedByGodot
    public void setYFlip(boolean flipped) {
        yFlip = flipped;
    }

    @UsedByGodot
    public void setColorPalette(int palette) {
        if (palette > (colorPallets.length - 1)) {
            Log.d(getPluginName(),"Invalid color palette.");
            return;
        }
        _palette = colorPallets[palette];
        if (state == CameraState.STARTED) {
            seekCamera.setColorPalette(_palette);
        }
    }

    @UsedByGodot
    public String getCameraInfoText() {
        if (state == CameraState.INITIALIZED || state == CameraState.CLOSED) {
            Log.d(getPluginName(), "Invalid camera state, cannot get info.");
            return "";
        }
        return cameraInfoText;
    }

    @UsedByGodot
    public int getWidth() {
        if (state == CameraState.INITIALIZED || state == CameraState.CLOSED) {
            Log.d(getPluginName(), "Invalid camera state, cannot get width.");
            return -1;
        }
        return width;
    }

    @UsedByGodot
    public int getHeight() {
        if (state == CameraState.INITIALIZED || state == CameraState.CLOSED) {
            Log.d(getPluginName(), "Invalid camera state, cannot get height.");
            return -1;
        }
        return height;
    }
    //endregion

    //region Tensorflow Godot controls

    @UsedByGodot
    public float getTensorflowThreshold() {
        return imageClassifierHelper.getThreshold();
    }

    @UsedByGodot
    public void setTensorflowThreshold(float threshold) {
        if (threshold < 0 || threshold > 1) {
            Log.e(getPluginName(), "Invalid threshold value");
            return;
        }
        imageClassifierHelper.setThreshold(threshold);
    }

    @UsedByGodot
    public int getNumTensorflowThreads() {
        return imageClassifierHelper.getNumThreads();
    }

    @UsedByGodot
    public void setNumTensorflowThreads(int nThreads) {
        imageClassifierHelper.setNumThreads(nThreads);
    }

    @UsedByGodot
    public int getTensorflowMaxResults() {
        return imageClassifierHelper.getMaxResults();
    }

    @UsedByGodot
    public void setTensorflowMaxResults(int maxResults) {
        imageClassifierHelper.setMaxResults(maxResults);
    }

    @UsedByGodot
    public void setTensorflowCurrentDelegate(int currentDelegate) {
        imageClassifierHelper.setCurrentDelegate(currentDelegate);
    }

    //endregion

    public SeekThermalGodotAndroidPlugin(Godot godot) {
        super(godot);

        Log.d(getPluginName(), "Initializing OpenCV...");
        if (OpenCVLoader.initLocal())
        {
            Log.d(getPluginName(), "OpenCV loaded successfully!");
        }
        else
        {
            Log.d(getPluginName(), "ERROR: Could not load OpenCV.");
        }

        imageClassifierHelper = ImageClassifierHelper.create(getActivity(), this);

        // Seek thermal camera initialization
        Log.d(getPluginName(), "Initializing Seek Thermal camera...");
        seekCameraManager = new SeekCameraManager(getActivity(), null, stateCallback);
        seekImageReader = new SeekImageReader();
        seekImageReader.setOnImageAvailableListener(this);

        Log.d(getPluginName(), "Seek Thermal camera initialized!");
    }

    @NonNull
    @Override
    public String getPluginName() { return "SeekThermalGodotAndroidPlugin"; }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        // Define the signals this plugin will emit
        Set<SignalInfo> signals = new HashSet<>();

        // Signal we emit when the wakeword is detected
        signals.add(new SignalInfo("camera_opened"));
        signals.add(new SignalInfo("camera_started"));
        signals.add(new SignalInfo("camera_stopped"));
        signals.add(new SignalInfo("camera_closed"));
        signals.add(new SignalInfo("camera_initialized"));
        signals.add(new SignalInfo("new_image", byte[].class));
        signals.add(new SignalInfo("new_stats", Dictionary.class));
        signals.add(new SignalInfo("new_data", float[].class));
        signals.add(new SignalInfo("new_class", String.class, String.class, Float.class, Integer.class));
        signals.add(new SignalInfo("exhaling_changed", Boolean.class));
        return signals;
    }

    @Override
    public void onImageAvailable(final SeekImage seekImage) {

        // Get the color bitmap (Bitmap.Config is hardware configuration)
        bitmap = seekImage.getColorBitmap();

        // Flip about x or y if necessary
        if (xFlip) {
            bitmap = SeekUtility.flipBitmapHorizontal(bitmap);
        }
        if (yFlip) {
            bitmap = SeekUtility.flipBitmapVertical(bitmap);
        }
        // Allocate the bitmap bytes if needed
        if (bitmapBytes == null) {
            Log.d(getPluginName(), "Byte count: " + String.valueOf(bitmap.getByteCount()));
            bitmapBytes = java.nio.ByteBuffer.allocate(bitmap.getByteCount());
        }
        // Sets read/write position to 0
        bitmapBytes.rewind();

        // Copy bits from bitmap to the bytebuffer
        bitmap.copyPixelsToBuffer(bitmapBytes);


        // Get thermal data from camera and convert to a float Mat
        ByteBuffer dataBuffer = seekImage.getThermography().getThermalData().getBuffer();

        // Data is represented as a 16-bit short, big-endian
        ShortBuffer shortBuffer = dataBuffer.asShortBuffer();

        // Copy the shorts to an array
        shortBuffer.get(shortArray);

        // Convert the array into a 16-bit Mat
        shortMat.put(0, 0, shortArray);

        // Convert the 16-bit Mat to a 32-bit float Mat
        // Seek algorithm scales shorts by 1/64 and subtracts 40 to get the float values
        shortMat.convertTo(floatMat, floatMat.type(), (1/64.0f), -40);

        // Flip floatMat appropriately
        // TODO: maybe wait until end for this
        if (xFlip && yFlip) {
            Core.flip(floatMat, floatMat, -1);
        }
        else if (xFlip) {
            Core.flip(floatMat, floatMat, 1);
        }
        else if (yFlip) {
            Core.flip(floatMat, floatMat, 0);
        }

        // Using OpenCV for this is FAR more efficient than using Seek's Thermography class functions
        // Get the min/max locations and values
        MinMaxLocResult res = Core.minMaxLoc(floatMat);

        Dictionary stats = new Dictionary();
        stats.put("maxX", (int)res.maxLoc.x);
        stats.put("maxY", (int)res.maxLoc.y);
        stats.put("maxValue", res.maxVal);
        stats.put("minX", (int)res.minLoc.x);
        stats.put("minY", (int)res.minLoc.y);
        stats.put("minValue", res.minVal);
        stats.put("avg", Core.mean(floatMat).val[0]);

        maxMin = (float)(res.maxVal - res.minVal);

        if (prevMaxMin == -1) {
            prevMaxMin = maxMin;
        }

        if (maxMin - prevMaxMin > maxMinThresh && !exhaling) {
            emitSignal("exhaling_changed", true);
            exhaling = true;
        }

        scaled = scaleImage(floatMat);
        scaled.get(0, 0, scaledBytes);
        //scaled.copyTo(roi);
        scaled.copyTo(processingMatGray.submat(roiRect));

        // Mask the data
        processingMatGray.copyTo(processingMatGrayMask, mask);

        processingMatGrayMask.get(0, 0, scaledSquareBytes);

        Imgproc.cvtColor(processingMatGrayMask, processingMatColor, Imgproc.COLOR_GRAY2BGR);
        Utils.matToBitmap(processingMatColor, processingBitmap);

        // If we're exhaling, classify the image to see if we're still exhaling, and what type of exhale
        if (exhaling) {
            classifyImage();
        }

        emitSignal("new_stats", stats);
        emitSignal("new_image", scaledSquareBytes);

        prevMaxMin = maxMin;
    }


    /// Scales image between MIN_TEMP and MAX_TEMP, sets to CV_8U
    private Mat scaleImage(Mat image) {

        Mat result = new Mat();
        image.copyTo(result);

        // Clip data below minTemp
        Core.max(result, new Scalar(MIN_TEMP), result);

        // Clip data above maxTemp
        Core.min(result, new Scalar(MAX_TEMP), result);

        // Subtract min value from image
        Core.subtract(result, new Scalar(MIN_TEMP), result);

        // Scale to [0,255] and convert to 8-bit unsigned integers
        result.convertTo(result, CvType.CV_8U, 255.0/(MAX_TEMP - MIN_TEMP));

        return result;
    }

    /// Classifies the image
    private void classifyImage() {
        imageClassifierHelper.classify(processingBitmap, 0);
    }

    @Override
    public void onError(String error) {
        Log.e(getPluginName(), error);
        classifications = new ArrayList<>();
    }

    @Override
    public void onResults(List<Classifications> results, long inferenceTime) {
        // This will generally return 1 or 0 results, because we're thresholding at 0.5. I suppose
        // it's possible it could return 2 if each is exactly .5, but this is highly unlikely
        classifications = results.get(0).getCategories();

        // Sometimes we don't get a category, meaning nothing reached the threshold. If this happens, and we're exhaling,
        // we say that we're no longer exhaling I guess
        if (classifications.isEmpty()) {
            if (exhaling) {
                emitSignal("exhaling_changed", false);
                exhaling = false;
            }
            return;
        }

        Category c = classifications.get(0);
        emitSignal("new_class", c.getLabel(), c.getDisplayName(), c.getScore(), c.getIndex());
    }
}
