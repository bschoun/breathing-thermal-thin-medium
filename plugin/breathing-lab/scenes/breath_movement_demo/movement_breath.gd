@tool
class_name XRToolsMovementBreath
extends XRToolsMovementProvider

## Movement provider order
@export var order : int = 15

## Movement mode
enum TurnMode {
	DEFAULT,	## Use turn mode from project/user settings
	SNAP,		## Use snap-turning
	SMOOTH		## Use smooth-turning
}

enum ExhaleState {NONE, GALE, WAFT}

## Movement mode property
@export var turn_mode : TurnMode = TurnMode.DEFAULT

## Smooth turn speed in radians per second
@export var smooth_turn_speed : float = 2.0

## Seconds per step (at maximum turn rate)
@export var step_turn_delay : float = 0.2

## Step turn angle in degrees
@export var step_turn_angle : float = 20.0

## Movement speed
@export var max_speed : float = 3.0

# Turn step accumulator
var _turn_step : float = 0.0

var exhale_state : ExhaleState = ExhaleState.NONE

var stats : Dictionary = {}

func _ready():
	super()

	# Subscribe to the camera events
	SeekThermal.camera_connected.connect(_on_camera_connected) # When we connect the camera, we want to start it
	SeekThermal.camera_disconnected.connect(_on_camera_disconnected)
	SeekThermal.camera_started.connect(_on_camera_started)
	SeekThermal.camera_stopped.connect(_on_camera_stopped)
	SeekThermal.exhaling_changed.connect(_on_exhaling_changed)
	SeekThermal.new_stats.connect(_on_new_stats)
	
	# If the camera has already been connected but it isn't started, start it
	if SeekThermal.get_state() == 2: # Camera is connected/opened, but not started
		SeekThermal.start_camera()
	
# Perform jump movement
func physics_movement(delta: float, player_body: XRToolsPlayerBody, _disabled: bool):

	var deadzone = 0.1
	if _snap_turning():
		deadzone = XRTools.get_snap_turning_deadzone()

	# Gale and waft are responsible for movement. If we're not doing that, stop moving and return
	if exhale_state == ExhaleState.NONE:
		# Return, we're done here
		return
		
	# If we don't have the stats to tell us left/right/forward, return
	if !stats.has("maxX"):
		return
	# Read the left/right joystick axis
	#var left_right := _controller.get_vector2(input_action).x
	var left_right = 0
	var dz_input_action = 0.0
	
	# Backwards movement
	if exhale_state == ExhaleState.WAFT:
		_turn_step = 0.0
		dz_input_action = -1.0
	
	# Forward motion and snap turning
	if exhale_state == ExhaleState.GALE:
		
		# Forward motion
		if (stats["maxX"] > 130 and stats["maxX"] < 190):
			_turn_step = 0.0
			dz_input_action = 1.0 # TODO: Can we make this dependent on maybe the max temp or something like speed?

		# Snap turning
		else:
		
			if stats["maxX"] <= 130:
				# Snap turn left
				left_right = -1
				
			elif stats["maxX"] >= 190:
				# Snap turn right
				left_right = 1

			# Handle smooth rotation
			if !_snap_turning():
				left_right -= deadzone * sign(left_right)
				player_body.rotate_player(smooth_turn_speed * delta * left_right)
				return

			# Disable repeat snap turning if delay is zero
			if step_turn_delay == 0.0 and _turn_step < 0.0:
				return

			# Update the next turn-step delay
			_turn_step -= abs(left_right) * delta
			if _turn_step >= 0.0:
				return

			# Turn one step in the requested direction
			if step_turn_delay != 0.0:
				_turn_step = step_turn_delay
			player_body.rotate_player(deg_to_rad(step_turn_angle) * sign(left_right))

	# Move player forward or backward
	player_body.ground_control_velocity.y += dz_input_action * max_speed

	# Clamp ground control
	var length := player_body.ground_control_velocity.length()
	if length > max_speed:
		player_body.ground_control_velocity *= max_speed / length
	
	
func _on_camera_connected(camera_info : String, width : int, height : int) -> void:
	print("Camera connected!")
	# Start the camera
	SeekThermal.start_camera()

# Save the statistics
func _on_new_stats(_stats: Dictionary) -> void:
	stats = _stats
	
func _on_camera_disconnected():
	print("Camera disconnected!")
	
func _on_camera_started():
	print("Camera started")
	
func _on_camera_stopped():
	print("Camera stopped")
	
func _on_exhaling_changed(value: bool, exhale_type : String) -> void:
	if (value):
		print("Exhaling: " + exhale_type)
		
		if (exhale_type == "GALE"):
			exhale_state = ExhaleState.GALE
		elif exhale_type == "WAFT":
			exhale_state = ExhaleState.WAFT
	else:
		print("Not exhaling")
		exhale_state = ExhaleState.NONE

# Test if snap turning should be used
func _snap_turning():
	match turn_mode:
		TurnMode.SNAP:
			return true

		TurnMode.SMOOTH:
			return false

		_:
			return XRToolsUserSettings.snap_turning
