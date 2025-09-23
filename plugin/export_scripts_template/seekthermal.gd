extends Node
#class_name SeekThermal

var _plugin_name = "SeekThermalGodotAndroidPlugin"
var _android_plugin			# Reference to the plugin

signal camera_connected(camera_info : String, width : int, height : int)
signal camera_disconnected
signal camera_started
signal camera_stopped
signal new_image(image : PackedByteArray)
#signal new_data(stats: Dictionary, data : PackedFloat32Array)
signal new_data(data : PackedFloat32Array)
signal new_stats(stats : Dictionary)
signal new_class(label : String, displayName : String, score : float, index : int)
signal exhaling_changed(value : bool, exhale_type : String)

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
		_android_plugin.connect("new_data", _on_new_data)
		_android_plugin.connect("new_image", _on_new_image)
		_android_plugin.connect("new_stats", _on_new_stats)
		_android_plugin.connect("new_class", _on_new_class)
		_android_plugin.connect("exhaling_changed", _on_exhaling_changed)
	else:
		printerr("Couldn't find plugin " + _plugin_name)

func _on_camera_opened() -> void:
	print("camera connected")
	width = _android_plugin.getWidth()
	height = _android_plugin.getHeight()
	camera_info = _android_plugin.getCameraInfoText()
	camera_connected.emit(camera_info, width, height)

func _on_exhaling_changed(value : bool, exhale_type : String) -> void:
	exhaling_changed.emit(value, exhale_type)

func _on_new_class(label : String, displayName : String, score : float, index : int) -> void:
	new_class.emit(label, displayName, score, index)

func _on_camera_started() -> void:
	print("camera started")
	camera_started.emit()

func _on_camera_stopped() -> void:
	print("camera stopped")
	camera_stopped.emit()
	
func _on_camera_closed() -> void:
	print("camera disconnected")
	camera_disconnected.emit()

func _on_new_image(image : PackedByteArray):
	new_image.emit(image)

#func _on_new_data(stats : Dictionary, data : PackedFloat32Array) -> void:
#	new_data.emit(stats, data)

func _on_new_data(data : PackedFloat32Array) -> void:
	new_data.emit(data)

func _on_new_stats(stats : Dictionary) -> void:
	new_stats.emit(stats)

func get_state() -> int:
	return _android_plugin.getState()

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

func get_camera_count() -> int:
	return _android_plugin.getCameraCount()

func suspend_shutter() -> void:
	_android_plugin.suspendShutter()

func trigger_shutter() -> void:
	_android_plugin.triggerShutter()

func resume_shutter() -> void:
	_android_plugin.resumeShutter()

func is_automatic_shutter() -> bool:
	return _android_plugin.isAutomaticShutter()

func set_image_smoothing(val : bool) -> void:
	_android_plugin.setImageSmoothing(val)

func get_image_smoothing() -> bool:
	return _android_plugin.getImageSmoothing()

func set_emissivity(val : float) -> void:
	_android_plugin.setEmissivity(val)

func get_emissivity() -> float:
	return _android_plugin.getEmissivity()

#func get_image() -> PackedByteArray:
#	return _android_plugin.getImage()

#func get_stats() -> Dictionary:
#	return _android_plugin.getStats()

#func get_data() -> PackedFloat32Array:
#	return _android_plugin.getData()
