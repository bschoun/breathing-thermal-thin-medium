package com.bschoun.godot.seekthermal;

import android.graphics.Point;
import android.util.Log;

import com.thermal.seekware.SeekCamera;
import com.thermal.seekware.SeekCameraManager;
import com.thermal.seekware.SeekImage;
import com.thermal.seekware.SeekImageReader;
import com.thermal.seekware.Thermography;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.Dictionary;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import kotlin.UShort;

public class SeekThermalGodotAndroidPlugin extends GodotPlugin implements SeekImageReader.OnImageAvailableListener {

    private SeekImageReader seekImageReader;        // Access to images without rendering

    private java.nio.ByteBuffer data;               // Buffer to hold data

    // Thermography info
    private Thermography thermography;
    private Thermography.Spot minSpot;
    private Thermography.Spot maxSpot;
    private Point minPoint;
    private Point maxPoint;

    private int width;

    private int height;

    private final int _byteOffset = 4;

    public SeekThermalGodotAndroidPlugin(Godot godot) {
        super(godot);

        // Seek thermal camera initialization
        Log.d(getPluginName(), "Initializing Seek Thermal camera...");
        SeekCamera.StateCallback stateCallback = new SeekCamera.StateCallbackAdapter() {
            @Override
            public synchronized void onOpened(SeekCamera sc) {
                Log.d(getPluginName(), "Camera opened");
                String cameraInfoText = sc.toString();
                Log.d(getPluginName(), "Creating Seek Camera capture session...");
                sc.createSeekCameraCaptureSession(false, true, true, seekImageReader);
                width = sc.getCharacteristics().getWidth();
                height = sc.getCharacteristics().getHeight();
                emitSignal("camera_opened", cameraInfoText, width, height);
            }

            @Override
            public synchronized void onClosed(SeekCamera seekCamera) {
                Log.d(getPluginName(), "Camera closed");
                emitSignal("camera_closed");
            }
        };
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
        signals.add(new SignalInfo("camera_opened", String.class, Integer.class, Integer.class));
        signals.add(new SignalInfo("camera_closed"));
        signals.add(new SignalInfo("new_image", Dictionary.class, float[].class));
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

            // Emit signal with statistics and image data
            emitSignal("new_image", stats, floatData);
        });
    }
}
