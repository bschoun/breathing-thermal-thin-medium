extends Node
class_name SeekThermal

var _plugin_name = "SeekThermalGodotAndroidPlugin"
var _android_plugin			# Reference to the plugin

signal camera_connected
signal camera_disconnected
signal new_image(stats: Dictionary, data : PackedByteArray)

func _ready() -> void:
	
	if Engine.has_singleton(_plugin_name):
		_android_plugin = Engine.get_singleton(_plugin_name)
		_android_plugin.connect("camera_opened", _on_camera_opened)
		_android_plugin.connect("camera_closed", _on_camera_closed)
		_android_plugin.connect("new_image", _on_new_image)
	else:
		printerr("Couldn't find plugin " + _plugin_name)

func _on_camera_opened(camera_info : String, width : int, height: int) -> void:
	print("camera connected")
	camera_connected.emit(camera_info, width, height)
	
func _on_camera_closed() -> void:
	print("camera disconnected")
	camera_disconnected.emit()

func _on_new_image(stats : Dictionary, data : PackedFloat32Array) -> void:
	#info_text = _android_plugin.getCameraInfoText()
	new_image.emit(stats, data)
