package com.bschoun.godot.seekthermal;

// Graphics
import android.content.res.AssetManager;
import android.graphics.Bitmap;

// Seek Thermal
import com.thermal.seekware.SeekCamera;
import com.thermal.seekware.SeekCameraManager;
import com.thermal.seekware.SeekImage;
import com.thermal.seekware.SeekImageReader;

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
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

// Java
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
    //private java.nio.ByteBuffer bitmapBytes = null; // Buffer to hold bitmap
    private Bitmap processingBitmap;

    // Prepare for input into classifier
    // Make image square

    private boolean xFlip = false;
    private boolean yFlip = false;

    // Camera info
    private int width = 320;
    private int height = 240;

    private Size targetSize;

    private final int FPS = 27;

    private final int EXHALE_TIMEOUT = 7;

    //private final float SHORT_HISTORY = 0.5f;
    private final float HISTORY_SECONDS = 1;

    private final float EXHALE_START_THRESHOLD = 0.5f;
    private final float EXHALE_END_THRESHOLD = -0.25f;

    private final float GALE_THRESHOLD = -0.125f;
    private final float WAFT_THRESHOLD = 2.0f;

    private final float STD_DEV = 2;
    private String cameraInfoText;

    private Mat processingMatGray;

    private Mat processingMatGrayMask;

    private Mat processingMatGrayMaskSmall;

    private Mat processingMatColor;
    private Mat shortMat; // Intermediate data in 16-bit shorts
    private Mat floatMat; // Data as 32-bit floats after conversion to actual values (in C)

    private Mat shortDiffMat;
    private Mat diffMat;

    private Mat scaled;

    private Rect roiRect;

    private Mat mask320x320;

    private Mat mask320x240;

    private Mat mask320x240Inverse;

    private Mat shortMovingAverageMat;
    private Mat movingAverageMat;

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
        NONE,
        INITIALIZED,
        OPENED,
        STARTED,
        STOPPED,
        CLOSED
    }

    private enum ExhaleType {
        GALE,
        WAFT,
        NONE,
        CALM
    }

    private CameraState state = CameraState.NONE;

    // Initialize to color 0
    private SeekCamera.ColorPalette _palette = colorPallets[0];

    private final ImageClassifierHelper imageClassifierHelper;

    // Keep track of whether we're exhaling or not (inhaling, holding breath)
    private boolean exhaling = false;

    private static final int nFrames = 27;

    private long exhaleStartTime = 0;
    private long exhaleEndTime;

    private float accumulateAlpha;

    private boolean initialized = false;

    private final Scalar blackColor = new Scalar(0,0,0);

    private static int totalPixels;

    private ExhaleType exhaleType = ExhaleType.NONE;


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

            totalPixels = width*height;
            targetSize = new Size(224, 224);

            exhaleEndTime = System.nanoTime();

            Log.d(getPluginName(), "Setting up mats...");

            // Initialize all of our data storage
            shortArray = new short[totalPixels];
            shortMat = new Mat(height, width, CvType.CV_16UC1);
            floatMat = new Mat(height, width, CvType.CV_32F);

            shortMovingAverageMat = new Mat(height, width, CvType.CV_32F);
            movingAverageMat = new Mat(height, width, CvType.CV_32F);

            shortDiffMat = new Mat(height, width, CvType.CV_32F);
            diffMat = new Mat(height, width, CvType.CV_32F);

            // Mats and byte arrays for passing image data to Godot
            scaled = new Mat(height, width, CvType.CV_8U);
            scaledBytes = new byte[totalPixels];
            scaledSquareBytes = new byte[width * width]; // usually (width, with)

            // For CNN-related processing
            processingBitmap = Bitmap.createBitmap((int)targetSize.width, (int)targetSize.height, Bitmap.Config.ARGB_8888);
            processingMatGray = new Mat(width, width, CvType.CV_8U);
            processingMatGrayMask = new Mat(width, width, CvType.CV_8U);
            processingMatGrayMaskSmall = new Mat(targetSize, CvType.CV_8U);
            processingMatColor = new Mat(targetSize, CvType.CV_8UC3);

            // Alpha values for moving averages
            //shortAccumulateAlpha = 1.0f/(FPS*SHORT_HISTORY);
            accumulateAlpha = 1.0f/(FPS* HISTORY_SECONDS);

            Log.d(getPluginName(), "Mats set up!");

            int yOffset = (width - height) / 2;
            roiRect = new Rect(0, yOffset, width, height);
            // Our state is OPENED
            state = CameraState.OPENED;

            Log.d(getPluginName(), "Emitting camera_opened");

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
    // TODO: Ideally, separate this functionality from exhale detection

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
        if (xFlip != flipped) {
            Core.flip(mask320x320, mask320x320, 1);
            Core.flip(mask320x240, mask320x240, 1);
            xFlip = flipped;
        }
    }

    @UsedByGodot
    public void setYFlip(boolean flipped) {
        if (yFlip != flipped) {
            yFlip = flipped;
            Core.flip(mask320x320, mask320x320, 0);
            Core.flip(mask320x240, mask320x240, 0);
        }
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
    public int getState() {
        return state.ordinal();
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
        seekCameraManager = new SeekCameraManager(Objects.requireNonNull(getActivity()), null, stateCallback);
        seekImageReader = new SeekImageReader();
        seekImageReader.setOnImageAvailableListener(this);

        Log.d(getPluginName(), "Seek Thermal camera initialized!");

        // Set up the mask
        mask320x320 = new Mat(width, width, CvType.CV_8U);

        AssetManager assetManager = getActivity().getAssets();
        InputStream istr = null;
        try {
            istr = assetManager.open("mask.jpg");
        } catch (IOException e) {
            Log.e(getPluginName(), e.toString());
        }
        Bitmap bmp = BitmapFactory.decodeStream(istr);
        Mat m = new Mat();
        Utils.bitmapToMat(bmp, m);
        Imgproc.cvtColor(m, mask320x320, Imgproc.COLOR_BGR2GRAY);

        // Make sure mask is completely black/white, no in-between
        Imgproc.threshold(mask320x320, mask320x320, 127, 255, Imgproc.THRESH_BINARY);
        Rect roiRect = new Rect(0, 40, width, height);

        // The mask that is the size of the image is a submat of the square image
        mask320x240 = mask320x320.submat(roiRect);

        // Get the inverted mask
        mask320x240Inverse = new Mat();
        Core.bitwise_not(mask320x240, mask320x240Inverse);
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
        signals.add(new SignalInfo("exhaling_changed", Boolean.class, String.class));

        return signals;
    }

    @Override
    public void onImageAvailable(final SeekImage seekImage) {

        // Get the color bitmap (Bitmap.Config is hardware configuration)
        // Hardware bitmap (to be converted to software bitmap)
        /*Bitmap bitmap = seekImage.getColorBitmap();

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
        bitmap.copyPixelsToBuffer(bitmapBytes);*/

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
        shortMat.convertTo(floatMat, floatMat.type(), (1 / 64.0f), -40);

        // Flip floatMat appropriately
        // TODO: maybe wait until end for this
        /*if (xFlip && yFlip) {
            Core.flip(floatMat, floatMat, -1);
        }
        else if (xFlip) {
            Core.flip(floatMat, floatMat, 1);
        }
        else if (yFlip) {
            Core.flip(floatMat, floatMat, 0);
        }*/

        // Get the min/max locations and values to find the min (within mask320x240)
        MinMaxLocResult res = Core.minMaxLoc(floatMat, mask320x240);

        // Now let's start detecting exhales
        // Subtract the min value from the data (within mask)
        Core.subtract(floatMat, new Scalar(res.minVal), floatMat, mask320x240);

        // Scale the image between 0 and 10, which is the approximate range of the image after subtracting
        scaled = scaleImage(floatMat, 0, 10, mask320x240);

        // Make sure the pixels of scaled that are masked are black
        scaled.setTo(blackColor, mask320x240Inverse);

        scaled.get(0, 0, scaledBytes);
        scaled.copyTo(processingMatGray.submat(roiRect));

        // Mask the data
        processingMatGray.copyTo(processingMatGrayMask, mask320x320);

        // Get the bytes and send this image to Godot so we can view it
        processingMatGrayMask.get(0, 0, scaledSquareBytes);
        emitSignal("new_image", (Object) scaledSquareBytes);

        // Resize for our CNN target size
        Imgproc.resize(processingMatGrayMask, processingMatGrayMaskSmall, targetSize, 0, 0, Imgproc.INTER_AREA);

        // Convert to color for CNN processing
        Imgproc.cvtColor(processingMatGrayMaskSmall, processingMatColor, Imgproc.COLOR_GRAY2BGR);
        Utils.matToBitmap(processingMatColor, processingBitmap);


        // Initialize the moving average with the current image
        if (!initialized) {
            //floatMat.copyTo(shortMovingAverageMat, mask320x240);
            floatMat.copyTo(movingAverageMat, mask320x240);
            initialized = true;
        }

        /* Statistical classification */

        // Get the max value of the current float data
        float max = (float) (res.maxVal - res.minVal);

        // Subtract the moving average from the float data to get the difference image
        Core.subtract(floatMat, movingAverageMat, diffMat);

        // Get the min/max values and locations of diffMat, masked
        MinMaxLocResult diffRes = Core.minMaxLoc(diffMat, mask320x240);

        // Calculate the midrange of the data
        float midrange = (float) (diffRes.maxVal + diffRes.minVal) / 2.0f;
        float iqrMaxDiff = getIqrUpperFence(diffMat) - midrange;


        // Send stats to Godot
        Dictionary stats = new Dictionary();
        stats.put("maxX", (int) res.maxLoc.x);
        stats.put("maxY", (int) res.maxLoc.y);
        stats.put("maxValue", res.maxVal);
        stats.put("minX", (int) res.minLoc.x);
        stats.put("minY", (int) res.minLoc.y);
        stats.put("minValue", res.minVal);

        emitSignal("new_stats", stats);

        // When we're not exhaling, check for the start of the exhale
        if (!exhaling) {

            if (midrange > EXHALE_START_THRESHOLD) {
                // Start the exhale but don't classify it yet
                startExhale(ExhaleType.NONE);
            }
        }

        // When we're exhaling, check for the end of the exhale
        // This needs to be exhaling, not else, to process any exhale we found above immediately and not one frame later
        if (exhaling) {

            // If we're exhaling and haven't yet classified the exhale, see if we can classify it
            if (exhaleType == ExhaleType.NONE) {

                if (iqrMaxDiff < GALE_THRESHOLD) {
                    startExhale(ExhaleType.GALE);
                } else if (iqrMaxDiff > WAFT_THRESHOLD) {
                    startExhale(ExhaleType.WAFT);
                }
            }
            // If our exhale type is WAFT or GALE, try classifying the image to look for an early end
            else {
                // Classify the resulting bitmap using the CNN
                //classifyImage(processingBitmap);
            }

            if (midrange < EXHALE_END_THRESHOLD) {
                endExhale("");
            }
            // Check for timeout (six seconds or longer exhale)
            else if ((System.nanoTime() - exhaleStartTime) / 1_000_000_000.0f > EXHALE_TIMEOUT) {
                endExhale("(TIMEOUT)");
            }
        }

        // Add floatMat into the accumulator after doing all of this processing
        Imgproc.accumulateWeighted(floatMat, movingAverageMat, accumulateAlpha, mask320x240);
    }

    private float getIqrUpperFence(Mat m) {

        MinMaxLocResult res = Core.minMaxLoc(m, mask320x240);
        // Scale to 8-bit (0–255)
        Mat byteImage = new Mat();
        float alpha = 255.0f / (float)(res.maxVal - res.minVal);
        float beta = (float)(-res.minVal * alpha);
        m.convertTo(byteImage, CvType.CV_8UC1, alpha, beta);

        // Compute histogram with mask
        Mat hist = new Mat();
        int histSize = 256;
        float[] range = {0f, 256f};
        Imgproc.calcHist(
                java.util.Collections.singletonList(byteImage),
                new MatOfInt(0),
                mask320x240, // <-- apply mask here
                hist,
                new MatOfInt(histSize),
                new MatOfFloat(range),
                false
        );

        // Compute cumulative histogram
        double total = Core.sumElems(hist).val[0];
        if (total == 0.0) {
            return 0.0f;
        }

        double q1Count = total * 0.25;
        double q3Count = total * 0.75;

        double cumulative = 0.0;
        int q1Bin = -1, q3Bin = -1;

        for (int i = 0; i < histSize; i++) {
            cumulative += hist.get(i, 0)[0];
            if (q1Bin == -1 && cumulative >= q1Count) {
                q1Bin = i;
            }
            if (q3Bin == -1 && cumulative >= q3Count) {
                q3Bin = i;
                break;
            }
        }

        // Convert back to original float scale
        double q1Val = (q1Bin / 255.0) * (res.maxVal - res.minVal) + res.minVal;
        double q3Val = (q3Bin / 255.0) * (res.maxVal - res.minVal) + res.minVal;
        double iqr = q3Val - q1Val;
        return (float)(q3Val + iqr*1.5);
    }

    private void endExhale(String source) {
        exhaling = false;
        exhaleEndTime = System.nanoTime();
        exhaleType = ExhaleType.NONE;
        emitSignal("exhaling_changed", false, "NONE " + source);
    }

    private void startExhale(ExhaleType _exhaleType) {
        exhaling = true;
        exhaleStartTime = System.nanoTime();
        exhaleType = _exhaleType;
        emitSignal("exhaling_changed", true, _exhaleType.name());
    }

    /// Scales image between MIN_TEMP and MAX_TEMP, sets to CV_8U
    private Mat scaleImage(Mat image, float _minTemp, float _maxTemp, Mat mask) {

        // Create a results array, copy the image to it
        Mat result = new Mat();
        image.copyTo(result);

        // Clip data below minTemp
        Core.max(result, new Scalar(_minTemp), result);

        // Clip data above maxTemp
        Core.min(result, new Scalar(_maxTemp), result);

        // Subtract min value from image (within the mask)
        Core.subtract(result, new Scalar(_minTemp), result, mask);

        // Scale to [0,255] and convert to 8-bit unsigned integers
        result.convertTo(result, CvType.CV_8U, 255.0/(_maxTemp - _minTemp));

        return result;
    }

    /// Classifies the image
    private void classifyImage(Bitmap image) {
        imageClassifierHelper.classify(image, 0);
    }

    @Override
    public void onError(String error) {
        Log.e(getPluginName(), error);
    }

    @Override
    public void onResults(List<ImageClassifierHelper.Classification> results, long inferenceTime) {
        // This will generally return 1 or 0 results, because we're thresholding at 0.5. I suppose
        // it's possible it could return 2 if each is exactly .5, but this is highly unlikely

        for (int i=0; i<results.size(); i++) {
            Log.d(getPluginName(), results.toString());
        }
        if (results.isEmpty()) {
            Log.d(getPluginName(), "empty classification");
            return;
        }

        ImageClassifierHelper.Classification c = results.get(0);
        Log.d(getPluginName(), c.label + " " + c.score + " " + c.index);

        if (c.index == ExhaleType.NONE.ordinal() && c.score >= 0.95) {
            if (exhaling && exhaleType != ExhaleType.NONE) {
                endExhale("CNN");
            }
        }
    }
}
