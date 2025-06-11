extends Node2D

@export var texture_rect : TextureRect
@export var status_label : Label
@export var data_label : Label
@export var stats_label : Label
@export var start_stop_button : Button

var w: int
var h: int
var img : Image

func _on_seek_thermal_camera_connected(camera_info : String, width : int, height : int) -> void:
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
		%SeekThermal.start_camera()
	else:
		%SeekThermal.stop_camera()


func _on_option_button_item_selected(index: int) -> void:
	print("Option selected: " + str(index))
	$SeekThermal.set_color_palette(index)


func _on_flip_x_check_button_toggled(toggled_on: bool) -> void:
	$SeekThermal.set_x_flip(toggled_on)


func _on_flip_y_check_button_toggled(toggled_on: bool) -> void:
	$SeekThermal.set_y_flip(toggled_on)


func _on_image_smoothing_toggled(toggled_on: bool) -> void:
	$SeekThermal.set_image_smoothing(toggled_on)


func _on_shutter_toggled(toggled_on: bool) -> void:
	if toggled_on:
		$SeekThermal.resume_shutter()
	else:
		$SeekThermal.suspend_shutter()


func _on_seek_thermal_new_image(image: PackedByteArray) -> void:
	img = Image.create_from_data(w, h, false, Image.FORMAT_RGBA8, image)
	texture_rect.texture = ImageTexture.create_from_image(img)


func _on_seek_thermal_new_stats(stats: Dictionary) -> void:
	stats_label.text = "Min: (" + str(stats["minX"]) + "," + str(stats["minY"]) + "): %0.2f" % stats["minValue"] + " C\n"
	stats_label.text += "Max: (" + str(stats["maxX"]) + "," + str(stats["maxY"]) + "): %0.2f" % stats["maxValue"] + " C\n"
	stats_label.text += "Average: %0.2f" % stats["avg"] + " C\n";
