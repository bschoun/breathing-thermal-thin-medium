extends Node
class_name SeekThermal

var _plugin_name = "SeekThermalGodotAndroidPlugin"
var _android_plugin			# Reference to the plugin

signal camera_connected(camera_info : String, width : int, height : int)
signal camera_disconnected
signal camera_started
signal camera_stopped
signal new_image(stats: Dictionary, data : PackedFloat32Array, image : PackedByteArray)

var width : int
var height : int
var camera_info : String


func _ready() -> void:
	
	if Engine.has_singleton(_plugin_name):
		_android_plugin = Engine.get_singleton(_plugin_name)
		_android_plugin.connect("camera_opened", _on_camera_opened)
		_android_plugin.connect("camera_closed", _on_camera_closed)
		_android_plugin.connect("camera_started", _on_camera_started)
		_android_plugin.connect("camera_stopped", _on_camera_stopped)
		_android_plugin.connect("new_image", _on_new_image)
	else:
		printerr("Couldn't find plugin " + _plugin_name)

func _on_camera_opened() -> void:
	print("camera connected")
	width = _android_plugin.getWidth()
	height = _android_plugin.getHeight()
	camera_info = _android_plugin.getCameraInfoText()
	camera_connected.emit(camera_info, width, height)

func _on_camera_started() -> void:
	print("camera started")
	camera_started.emit()

func _on_camera_stopped() -> void:
	print("camera stopped")
	camera_stopped.emit()
	
func _on_camera_closed() -> void:
	print("camera disconnected")
	camera_disconnected.emit()

func _on_new_image(stats : Dictionary, data : PackedFloat32Array, image : PackedByteArray) -> void:
	#info_text = _android_plugin.getCameraInfoText()
	new_image.emit(stats, data, image)

func start_camera() -> void:
	_android_plugin.setColorPalette(0)
	_android_plugin.startCamera()
	
func stop_camera() -> void:
	_android_plugin.stopCamera()
	
func set_color_palette(palette : int):
	_android_plugin.setColorPalette(palette)
	
func set_x_flip(val : bool):
	_android_plugin.setXFlip(val)
	
func set_y_flip(val : bool):
	_android_plugin.setYFlip(val)
