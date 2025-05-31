package com.bschoun.godot.seekthermal;

// Graphics
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;

// Seek Thermal
import com.thermal.seekware.SeekCamera;
import com.thermal.seekware.SeekCameraManager;
import com.thermal.seekware.SeekImage;
import com.thermal.seekware.SeekImageReader;
import com.thermal.seekware.Thermography;

// Godot
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.Dictionary;
import org.godotengine.godot.plugin.UsedByGodot;

// OpenCV
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import kotlin.UShort;
import android.util.Log;

public class SeekThermalGodotAndroidPlugin extends GodotPlugin implements SeekImageReader.OnImageAvailableListener {

    private SeekImageReader seekImageReader;        // Access to images without rendering

    private SeekCamera seekCamera;

    // Data/stats
    private java.nio.ByteBuffer data;               // Buffer to hold data
    private Thermography thermography;
    private Thermography.Spot minSpot;
    private Thermography.Spot maxSpot;
    private Point minPoint;
    private Point maxPoint;

    // Image configurations
    private java.nio.ByteBuffer bitmapBytes = null; // Buffer to hold bitmap
    private Bitmap softwareBitmap = null;           // Software bitmap
    private Bitmap hardwareBitmap = null;           // Hardware bitmap (to be converted to software bitmap)
    private boolean xFlip = false;
    private boolean yFlip = false;

    // Camera info
    private int width;
    private int height;
    private String cameraInfoText;

    // List of color palettes that can be indexed (because you can't cast Java enums to ints?)
    // TODO: maybe there's a better way to do this but I don't care right now
    private SeekCamera.ColorPalette[] colorPallets = {
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

    private CameraState state;

    private final int _byteOffset = 4;

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
            state = CameraState.OPENED;
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

    //region Functions exposed in Godot to control camera and get info
    @UsedByGodot
    public void startCamera() {
        if (state != CameraState.OPENED && state != CameraState.STOPPED) {
            Log.d(getPluginName(), "Invalid camera state, cannot start camera.");
            return;
        }
        seekCamera.createSeekCameraCaptureSession(false, true, true, seekImageReader);
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
        if (palette > colorPallets.length - 1) {
            Log.d(getPluginName(),"Invalid color palette.");
            return;
        }
        SeekCamera.ColorPalette p = colorPallets[palette];
        seekCamera.setColorPalette(p);
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

        // Seek thermal camera initialization
        Log.d(getPluginName(), "Initializing Seek Thermal camera...");
        SeekCameraManager seekCameraManager = new SeekCameraManager(getActivity(), null, stateCallback);
        seekImageReader = new SeekImageReader();
        seekImageReader.setOnImageAvailableListener(this);
        Log.d(getPluginName(), "Seek Thermal camera initialized!");
    }

    @Override
    public String getPluginName() { return "SeekThermalGodotAndroidPlugin"; }

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
        signals.add(new SignalInfo("new_image", Dictionary.class, float[].class, byte[].class));
        return signals;
    }

    private static float[] convertByteArrayToFloatArray(byte[] byteArray) {
        if (byteArray == null || byteArray.length % 2 != 0) {
            throw new IllegalArgumentException("The byte array must not be null and its length must be even.");
        }
        float[] result = new float[byteArray.length/2];
        for (int i=0; i < byteArray.length; i+= 2) {

            // Swapped the << 8 for converting from little endian to big endian-ness
            short val = (short)(((byteArray[i] & 0xFF) | (byteArray[i + 1] & 0xFF) << 8));
            result[i/2] = Thermography.shortToFloatTemperature(val);
        }
        return result;
    }

    private static Mat convertFloatArrayToMat(float[] floatArray, int rows, int cols) {
        Mat mat = new Mat(rows, cols, CvType.CV_32F);
        mat.put(0, 0, floatArray);
        return mat;
    }

    private static Bitmap createFlippedBitmap(Bitmap source, boolean xFlip, boolean yFlip) {
        Matrix matrix = new Matrix();
        matrix.postScale(xFlip ? -1 : 1, yFlip ? -1 : 1, source.getWidth() / 2f, source.getHeight() / 2f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    @Override
    public void onImageAvailable(final SeekImage seekImage) {
        runOnUiThread(() -> {

            // Dictionary will hold min/max statistics
            Dictionary stats = new Dictionary();

            // Get image thermography
            thermography = seekImage.getThermography();

            // Min/max temperature point and value
            minSpot = thermography.getMinSpot();
            minPoint = minSpot.getCenter();
            maxSpot = thermography.getMaxSpot();
            maxPoint = maxSpot.getCenter();

            // Get thermal data statistics
            stats.put("maxX", maxPoint.x);
            stats.put("maxY", maxPoint.y);
            stats.put("maxValue", maxSpot.getTemperature().getValue());
            stats.put("minX", minPoint.x);
            stats.put("minY", minPoint.y);
            stats.put("minValue", minSpot.getTemperature().getValue());
            stats.put("avg", thermography.getThermalData().getAverageTemperature().getValue());

            // Get thermal data from camera
            data = thermography.getThermalData().getBuffer();

            // For some reason the data in the array is offset by 4, at least for the camera that I have. Found through experimentation. Haven't found documentation as to why
            byte[] dataSubset = Arrays.copyOfRange(data.array(), _byteOffset, width*height*UShort.SIZE_BYTES+_byteOffset);

            // Convert byte data to float data
            float[] floatData = convertByteArrayToFloatArray(dataSubset);

            // Get the color bitmap (Bitmap.Config is hardware configuration)
            hardwareBitmap = seekImage.getColorBitmap();
            // Create a software bitmap with the properties of the hardware bitmap
            if (softwareBitmap == null) {
                softwareBitmap = Bitmap.createBitmap(null, hardwareBitmap.getWidth(), hardwareBitmap.getHeight(), Bitmap.Config.ARGB_8888, true, hardwareBitmap.getColorSpace());
            }

            // Copy data from hardware bitmap to software bitmap
            softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true);

            // Flip about x or y if necessary
            softwareBitmap = createFlippedBitmap(softwareBitmap, xFlip, yFlip);

            // Allocate the bitmap bytes if needed
            if (bitmapBytes == null) {
                bitmapBytes = java.nio.ByteBuffer.allocate(hardwareBitmap.getByteCount());
            }
            // Sets read/write position to 0
            bitmapBytes.rewind();

            // Copy bits from bitmap to the bytebuffer
            softwareBitmap.copyPixelsToBuffer(bitmapBytes);

            // Grayscale image from data (keeping this as a template for when I want to process with OpenCV)
            /*Mat mat = convertFloatArrayToMat(floatData, height, width);
            Mat mat_8u = new Mat(height, width, CvType.CV_8U);
            mat.convertTo(mat_8u, CvType.CV_8U);
            byte[] image = new byte[width*height];
            mat_8u.get(0, 0, image);
            // Emit signal with statistics and image data
            emitSignal("new_image", stats, floatData, image);*/
            emitSignal("new_image", stats, floatData, bitmapBytes.array());
        });
    }
}
