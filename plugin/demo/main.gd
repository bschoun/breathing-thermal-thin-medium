extends Node2D

@export var texture_rect : TextureRect
@export var status_label : Label
@export var data_label : Label
@export var stats_label : Label
@export var start_stop_button : Button
@export var classes_label : Label
@export var exhaling_label : Label
@export var midrange_label : Label

var w: int
var h: int
var img : Image



func _ready():
	SeekThermal.camera_connected.connect(_on_seek_thermal_camera_opened)
	SeekThermal.camera_disconnected.connect(_on_seek_thermal_camera_disconnected)
	SeekThermal.camera_started.connect(_on_seek_thermal_camera_started)
	SeekThermal.camera_stopped.connect(_on_seek_thermal_camera_stopped)
	SeekThermal.exhaling_changed.connect(_on_seek_thermal_exhaling_changed)
	SeekThermal.new_image.connect(_on_seek_thermal_new_image)
	SeekThermal.new_stats.connect(_on_seek_thermal_new_stats)
	SeekThermal.midrange.connect(_on_midrange)
	
func _on_midrange(value : float):
	midrange_label.text = str(value)
	

func _on_seek_thermal_camera_opened(camera_info : String, width : int, height : int) -> void:
	
	status_label.text = "Camera connected"
	data_label.text = camera_info
	w = width
	h = height
	start_stop_button.text = "Start camera"
	start_stop_button.disabled = false

func _on_seek_thermal_camera_disconnected() -> void:
	status_label.text = "Camera disconnected"
	start_stop_button.text = "Start camera"
	start_stop_button.disabled = true

#func _on_seek_thermal_new_image(image: PackedByteArray) -> void:
	
#	# Create the image from data
#	var img : Image = Image.create_from_data(w, h, false, Image.FORMAT_RGBA8, image)
#	texture_rect.texture = ImageTexture.create_from_image(img)

func _on_seek_thermal_camera_started() -> void:
	status_label.text = "Camera started"
	start_stop_button.text = "Stop camera"

func _on_seek_thermal_camera_stopped() -> void:
	status_label.text = "Camera stopped"
	start_stop_button.text = "Start camera"

func _on_button_toggled(_toggled_on: bool) -> void:
	if start_stop_button.text == "Start camera":
		SeekThermal.start_camera()
		#SeekThermal.start_camera()
	else:
		SeekThermal.stop_camera()
		#SeekThermal.stop_camera()


func _on_option_button_item_selected(index: int) -> void:
	print("Option selected: " + str(index))
	#$SeekThermal.set_color_palette(index)
	SeekThermal.set_color_palette(index)


func _on_flip_x_check_button_toggled(toggled_on: bool) -> void:
	SeekThermal.set_x_flip(toggled_on)
	#SeekThermal.set_x_flip(toggled_on)


func _on_flip_y_check_button_toggled(toggled_on: bool) -> void:
	SeekThermal.set_y_flip(toggled_on)
	#SeekThermal.set_y_flip(toggled_on)


func _on_image_smoothing_toggled(toggled_on: bool) -> void:
	SeekThermal.set_image_smoothing(toggled_on)
	#SeekThermal.set_image_smoothing(toggled_on)


func _on_shutter_toggled(toggled_on: bool) -> void:
	if toggled_on:
		SeekThermal.resume_shutter()
		#SeekThermal.resume_shutter()
	else:
		SeekThermal.suspend_shutter()
		#SeekThermal.suspend_shutter()


func _on_seek_thermal_new_image(image: PackedByteArray) -> void:
	#img = Image.create_from_data(w, h, false, Image.FORMAT_RGBA8, image)
	img = Image.create_from_data(w, w, false, Image.FORMAT_L8, image)
	texture_rect.texture = ImageTexture.create_from_image(img)


func _on_seek_thermal_new_stats(stats: Dictionary) -> void:
	stats_label.text = "Min: (" + str(stats["minX"]) + "," + str(stats["minY"]) + "): %0.2f" % stats["minValue"] + " C\n"
	stats_label.text += "Max: (" + str(stats["maxX"]) + "," + str(stats["maxY"]) + "): %0.2f" % stats["maxValue"] + " C\n"
	#stats_label.text += "Average: %0.2f" % stats["avg"] + " C\n";


func _on_seek_thermal_exhaling_changed(value: bool, exhale_type : String) -> void:
	if value:
		exhaling_label.text = "EXHALING " + exhale_type
		exhaling_label.label_settings.font_color = Color(0.0, 1.0, 0.0, 1.0)
	else:
		exhaling_label.text = "NOT EXHALING"
		exhaling_label.label_settings.font_color = Color(1.0, 0.0, 0.0, 1.0)
		classes_label.text = ""
		classes_label.label_settings.font_color = Color(1.0, 1.0, 1.0, 1.0)
