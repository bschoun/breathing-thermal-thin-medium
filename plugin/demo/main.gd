extends Node2D

@export var status_label : Label
@export var data_label : Label
@export var stats_label : Label


func _on_seek_thermal_camera_connected(camera_info : String, width : int, height : int) -> void:
	status_label.text = "Camera connected\n" + camera_info#+ "\n" + "(" + str(width) + "," + str(height) + ")"


func _on_seek_thermal_camera_disconnected() -> void:
	status_label.text = "Camera disconnected"


func _on_seek_thermal_new_image(stats: Dictionary, data: PackedFloat32Array) -> void:
	data_label.text = "Data size: " + str(data.size()) + "\n"
	data_label.text += "data[0]: " + str(data[0]) + "\n"
	data_label.text += "data[1]: " + str(data[1]) + "\n"
	data_label.text += "data[2]: " + str(data[2]) + "\n"
	data_label.text += "data[3]: " + str(data[3]) + "\n"
	stats_label.text = "Min: (" + str(stats["minX"]) + "," + str(stats["minY"]) + "): " + str(stats["minValue"]) + " C\n"
	stats_label.text += "Max: (" + str(stats["maxX"]) + "," + str(stats["maxY"]) + "): " + str(stats["maxValue"]) + " C\n"
	stats_label.text += "Average: " + str(stats["avg"]) + " C\n";
