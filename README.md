# Breathing Lab: Breath-Controlled Virtual Reality using Thermal Thin-Medium Imaging

This Godot project is a demonstration of a custom breathing sensor for VR that uses thermal imaging and a thin medium to detect exhale gestures. It is the sample application described in the paper "Breath-Controlled Virtual Reality using Thermal Thin-Medium Imaging" by Schoun et. al. 

This demonstration currently showcases how to use exhale gestures to navigate through a virtual environment. In the future, we plan to add demonstrations showing interactions with virtual objects and dynamically changing a virtual environment based on breathing rate.

NOTE: Using this project in the intended manner requires 3D printing your own sensor apparatus, as well as purchasing a Seek Thermal development kit. 

## Building the sensor
Information and resources coming soon!

## Usage
Coming soon!

## Contents
* A simple demo project to test the breathing apparatus: [`plugin/demo`](plugin/demo)
* The VR demo application, Breathing Lab, which currently has a sample maze navigated through exhale gestures: 
  [`plugin/breathing-lab`](plugin/breathing-lab)
* Source files for the Java logic of the Android plugin: 
  [`plugin/src/main/java`](plugin/src/main/java)

## Usage
**Note:** [Android Studio](https://developer.android.com/studio) is the recommended IDE for modifying this plugin. 
You can install the latest version from https://developer.android.com/studio.

### Building the plugin
- In a terminal window, navigate to the project's root directory and run the following command:
```
./gradlew assemble
```
- On successful completion of the build, the output files can be found in
  [`plugin/demo/addons`](plugin/demo/addons) and [`plugin/breathing-lab/addons](plugin/breathing-lab/addons)

### Testing the Android plugin
You can use the [demo project](plugin/demo/project.godot) or the [Breathing Lab application](plugin/breathing-lab/project.godot) to test.

- Open the demo in Godot (4.5 or higher)
- Navigate to `Project` -> `Project Settings...` -> `Plugins`, and ensure the plugin is enabled
- Install the Godot Android build template by clicking on `Project` -> `Install Android Build Template...`
- Open [`plugin/demo/main.gd`](plugin/demo/main.gd) or [`plugin/breathing-lab/main.gd`](plugin/breathing-lab/main.gd) and update as you wish
- Connect an Android device to your machine and run the demo on it. The [demo project](plugin/demo/project.godot) will run on any Android device, and the [Breathing Lab application](plugin/breathing-lab/project.godot) will run on Meta Quest headsets (though the sensor is designed for the Quest 3)
