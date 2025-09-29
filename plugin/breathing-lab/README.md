# Breathing Lab: Breath-Controlled Virtual Reality using Thermal Thin-Medium Imaging

This Godot project is a demonstration of a custom breathing sensor for VR that uses thermal imaging and a thin medium to detect exhale gestures. It is based on the Godot XR Tools sample project, and is the sample application described in the paper "Breath-Controlled Virtual Reality using Thermal Thin-Medium Imaging" by Schoun et. al. 

This demonstration currently showcases how to use exhale gestures to navigate through a virtual environment. In the future, we plan to add demonstrations showing interactions with virtual objects and dynamically changing a virtual environment based on breathing rate.

NOTE: Using this project in the intended manner requires 3D printing your own sensor apparatus, as well as purchasing a Seek Thermal development kit. 

## Building the sensor

Requirements:

1. [Seek Thermal S314SPX Mosaic camera module](https://shop.thermal.com/Mosaic-Core-Starter-Kit-320x240-57HFOV-FF?srsltid=AfmBOop_xFjnca6o9YhoencLK02_uAtVW-eR9xHiWrwjtgn0CZhiOxuz)
2. [Seek Thermal Flex to USB-C board](https://shop.thermal.com/accessories-4658)
3. A short USB-C male-to-male cable. 
4. 3D print of the sensor attachment (recommended PETG with 100% infill)

Remove the camera module from the starter kit board, and remove all silicone cushions from the camera. Attach the flex to USB-C board to the flex cable. The camera module will be inserted into the 3D printed enclosure first, followed by a 3D printed spacer, then the USB-C board, then another spacer, the coprocessor board, and then finally the lid.

## How to Use

You will probably need to install the OpenXR plugin for this project to work. You can obtain the OpenXR plugin [here](https://github.com/GodotVR/godot_openxr/releases) or download it from the asset library within Godot.

You can export the project for Android and then plug the built sensor into the headset with the USB-C cable. If the application is not already open, the headset will ask if you'd like to open the application.

## Licensing

Code in this repository is licensed under the MIT license.
Images are licensed under CC0 unless otherwise specified.

See the full license inside of the addons folder.
