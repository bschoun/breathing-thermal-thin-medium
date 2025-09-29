@tool
extends EditorPlugin

# Replace this value with a PascalCase autoload name, as per the GDScript style guide.
'''const AUTOLOAD_NAME = "SeekThermalCamera"

func _enable_plugin():
	# The autoload can be a scene or script file.
	add_autoload_singleton(AUTOLOAD_NAME, "res://addons/SeekThermalGodotAndroidPlugin/seekthermal.gd")


func _disable_plugin():
	remove_autoload_singleton(AUTOLOAD_NAME)'''

# A class member to hold the editor export plugin during its lifecycle.
var export_plugin : AndroidExportPlugin

func _enter_tree():
	# Initialization of the plugin goes here.
	export_plugin = AndroidExportPlugin.new()
	add_export_plugin(export_plugin)


func _exit_tree():
	# Clean-up of the plugin goes here.
	remove_export_plugin(export_plugin)
	export_plugin = null


class AndroidExportPlugin extends EditorExportPlugin:

	var _plugin_name = "SeekThermalGodotAndroidPlugin"

	func _supports_platform(platform):
		if platform is EditorExportPlatformAndroid:
			return true
		return false

	func _get_android_libraries(platform, debug):
		if debug:
			return PackedStringArray([_plugin_name + "/bin/debug/" + _plugin_name + "-debug.aar", _plugin_name + "/bin/debug/seek_android_sdk_4.3.0.2.aar"])
		else:
			return PackedStringArray([_plugin_name + "/bin/release/" + _plugin_name + "-release.aar", _plugin_name + "/bin/debug/seek_android_sdk_4.3.0.2.aar"])

	func _get_android_manifest_activity_element_contents(platform: EditorExportPlatform, debug: bool) -> String:
		var contents = """
		<intent-filter>
			<action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
		</intent-filter>
		<meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" android:resource="@xml/seekware_device_filter" />\n
		"""
		return contents

	func _get_android_dependencies(platform, debug):
		# TODO: if needed, change packages for debug vs release. My packages are the same for each
		return PackedStringArray(["androidx.appcompat:appcompat:1.7.0", 
			"androidx.lifecycle:lifecycle-extensions:2.2.0", 
			"org.opencv:opencv:4.11.0",

			# new
			"org.tensorflow:tensorflow-lite:2.17.0",
			"org.tensorflow:tensorflow-lite-support:0.5.0",

			# old
			#"org.tensorflow:tensorflow-lite-task-vision:0.4.0",
			#"org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.0",

			"org.tensorflow:tensorflow-lite-gpu:2.9.0"])

	func _get_name():
		return _plugin_name
