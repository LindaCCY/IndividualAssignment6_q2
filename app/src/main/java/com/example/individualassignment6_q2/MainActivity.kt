package com.example.individualassignment6_q2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.individualassignment6_q2.ui.theme.IndividualAssignment6_q2Theme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var magnetometer: Sensor? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Raw sensor data arrays for compass calculation
    private val accelerometerReading = FloatArray(3)  // Gravity vector (x, y, z)
    private val magnetometerReading = FloatArray(3)   // Magnetic field (x, y, z)
    private val rotationMatrix = FloatArray(9)        // 3x3 rotation matrix
    private val orientationAngles = FloatArray(3)     // Azimuth, pitch, roll from rotation matrix

    // Low-pass filter constant for smoothing compass jitter (0-1, lower = smoother)
    private val ALPHA = 0.15f
    private var smoothedAzimuth = 0f

    // State variables using mutableStateOf for automatic UI recomposition
    private var azimuth by mutableStateOf(0f)    // Compass heading (0-360°)
    private var roll by mutableStateOf(0f)       // Left/right tilt in degrees
    private var pitch by mutableStateOf(0f)      // Forward/backward tilt in degrees

    // Simulation mode states for testing without physical movement
    private var useSimulation by mutableStateOf(true)  // Default to simulation for easy testing
    private var simHeading by mutableStateOf(0f)       // Manual heading control
    private var simRoll by mutableStateOf(0f)          // Manual roll control
    private var simPitch by mutableStateOf(0f)         // Manual pitch control

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize all sensors needed for compass and level
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        enableEdgeToEdge()
        setContent {
            IndividualAssignment6_q2Theme {
                CompassAndLevelScreen(
                    heading = if (useSimulation) simHeading else azimuth,
                    roll = if (useSimulation) simRoll else roll,
                    pitch = if (useSimulation) simPitch else pitch,
                    useSimulation = useSimulation,
                    onToggleSimulation = { useSimulation = it },
                    onHeadingChange = { simHeading = it },
                    onRollChange = { simRoll = it },
                    onPitchChange = { simPitch = it }
                )
            }
        }
    }

    // Register all three sensors when app becomes visible (saves battery)
    override fun onResume() {
        super.onResume()
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // Unregister all sensors to save battery when app is not visible
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                // Accelerometer measures gravity direction - essential for both features
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(it.values, 0, accelerometerReading, 0, accelerometerReading.size)
                    updateCompassHeading()  // Combines with magnetometer for true north
                    updateLevelAngles()     // Directly calculates tilt from gravity vector
                }
                // Magnetometer measures Earth's magnetic field - points toward magnetic north
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(it.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    updateCompassHeading()  // Combines with accelerometer for device orientation
                }
                // Gyroscope measures rotation rates (not actively used in current implementation)
                Sensor.TYPE_GYROSCOPE -> {
                    // Gyroscope could provide smoother real-time updates via integration
                    // Currently using accelerometer for absolute tilt angles (more accurate)
                }
            }
        }
    }

    // Calculate compass heading using sensor fusion (magnetometer + accelerometer)
    // This is more accurate than using magnetometer alone
    private fun updateCompassHeading() {
        // Need both sensor readings to calculate orientation
        if (accelerometerReading.all { it != 0f } && magnetometerReading.all { it != 0f }) {
            // Compute 3D rotation matrix from gravity (down) and magnetic field (north)
            // This tells us device orientation relative to Earth
            SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,  // Gravity vector
                magnetometerReading    // Magnetic field vector
            )

            // Extract Euler angles (azimuth, pitch, roll) from rotation matrix
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Azimuth (orientationAngles[0]) is compass heading in radians
            // 0 = North, π/2 = East, π = South, 3π/2 = West
            var degrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            degrees = (degrees + 360) % 360  // Normalize to 0-360 range

            // Apply low-pass filter to smooth compass jitter caused by magnetic interference
            // Handle wraparound at 0°/360° boundary to prevent needle from spinning wildly
            var diff = degrees - smoothedAzimuth
            if (diff > 180) diff -= 360   // Take shorter rotation path
            if (diff < -180) diff += 360  // Take shorter rotation path
            smoothedAzimuth = (smoothedAzimuth + ALPHA * diff + 360) % 360

            azimuth = smoothedAzimuth
        }
    }

    // Calculate roll and pitch angles from accelerometer gravity vector
    // These represent how much the device is tilted from level
    private fun updateLevelAngles() {
        if (accelerometerReading.all { it != 0f }) {
            val x = accelerometerReading[0]  // Left/right axis
            val y = accelerometerReading[1]  // Forward/backward axis
            val z = accelerometerReading[2]  // Up/down axis (pointing to sky when flat)

            // Roll: rotation around X-axis (left/right tilt)
            // Uses atan2(y, z) to find angle of tilt in Y-Z plane
            roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()

            // Pitch: rotation around Y-axis (forward/backward tilt)
            // Uses atan2(-x, sqrt(y²+z²)) to find angle of tilt in X plane
            pitch = Math.toDegrees(atan2(-x.toDouble(), Math.sqrt((y * y + z * z).toDouble()))).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Sensor accuracy changes don't affect our app
    }
}

@Composable
fun CompassAndLevelScreen(
    heading: Float,
    roll: Float,
    pitch: Float,
    useSimulation: Boolean,
    onToggleSimulation: (Boolean) -> Unit,
    onHeadingChange: (Float) -> Unit,
    onRollChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    // Dynamic background color changes based on tilt for visual feedback
    val backgroundColor = getTiltColor(roll, pitch)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween  // Push controls to bottom
        ) {
            // Main content area (compass and level displays)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App title with technical/aviation aesthetic
                Text(
                    text = "COMPASS & LEVEL",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Compass display with rotating needle
                CompassDisplay(heading = heading)

                Spacer(modifier = Modifier.height(24.dp))

                // Bubble level with tilt visualization
                DigitalLevelDisplay(roll = roll, pitch = pitch)

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Testing controls fixed at bottom
            SimulationControls(
                useSimulation = useSimulation,
                heading = heading,
                roll = roll,
                pitch = pitch,
                onToggleSimulation = onToggleSimulation,
                onHeadingChange = onHeadingChange,
                onRollChange = onRollChange,
                onPitchChange = onPitchChange
            )
        }
    }
}

@Composable
fun CompassDisplay(heading: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)  // Glassmorphism effect
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "COMPASS",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Primary heading display - large and prominent
            Text(
                text = "${heading.toInt()}°",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Cardinal/intercardinal direction (N, NE, E, SE, S, SW, W, NW)
            Text(
                text = getDirectionLabel(heading),
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64B5F6)  // Light blue accent
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visual compass rose with animated needle
            Box(
                modifier = Modifier.size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                // Static compass rose with cardinal markers
                CompassRose()

                // Rotating compass needle - rotates opposite to heading so north pointer stays pointing north
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .rotate(-heading)  // Negative rotation keeps needle pointing to true north
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val needleLength = size.width / 2 - 20.dp.toPx()

                    // North pointer (red) - classic compass styling
                    drawLine(
                        color = Color(0xFFEF5350),
                        start = center,
                        end = Offset(center.x, center.y - needleLength),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // South pointer (white) - balances the needle
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = Offset(center.x, center.y + needleLength),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Center pivot dot
                    drawCircle(
                        color = Color.White,
                        radius = 12.dp.toPx(),
                        center = center
                    )
                }
            }
        }
    }
}

@Composable
fun CompassRose() {
    // Static compass background with concentric circles and cardinal markers
    Canvas(modifier = Modifier.size(280.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2 - 20.dp.toPx()

        // Outer circle - main compass boundary
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Inner circle - visual depth and reference
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = radius - 30.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Cardinal direction tick marks at N(0°), E(90°), S(180°), W(270°)
        val directions = listOf(0f, 90f, 180f, 270f)
        directions.forEach { angle ->
            rotate(angle, center) {
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(center.x, center.y - radius + 10.dp.toPx()),
                    end = Offset(center.x, center.y - radius + 30.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun DigitalLevelDisplay(roll: Float, pitch: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DIGITAL LEVEL",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bubble level visualization
            BubbleLevelVisual(roll = roll, pitch = pitch)

            Spacer(modifier = Modifier.height(24.dp))

            // Roll and Pitch readings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Roll (left/right tilt)
                LevelReadingCard(
                    label = "ROLL",
                    value = roll,
                    color = Color(0xFFFF7043)
                )

                // Pitch (forward/backward tilt)
                LevelReadingCard(
                    label = "PITCH",
                    value = pitch,
                    color = Color(0xFF66BB6A)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Level status indicator
            LevelStatusIndicator(roll = roll, pitch = pitch)
        }
    }
}

@Composable
fun BubbleLevelVisual(roll: Float, pitch: Float) {
    // Simulates a traditional bubble level - bubble moves opposite to tilt
    Box(
        modifier = Modifier
            .size(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.3f)),  // Dark level container
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)

            // Draw reference grid for easier bubble positioning
            // Horizontal grid lines
            for (i in 1..4) {
                val y = size.height * i / 5
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Vertical grid lines
            for (i in 1..4) {
                val x = size.width * i / 5
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Center crosshair - target position when device is level
            drawLine(
                color = Color(0xFF4CAF50),  // Green = level
                start = Offset(center.x - 20.dp.toPx(), center.y),
                end = Offset(center.x + 20.dp.toPx(), center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFF4CAF50),
                start = Offset(center.x, center.y - 20.dp.toPx()),
                end = Offset(center.x, center.y + 20.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )

            // Bubble position - inverted from tilt for realistic physics
            // (When you tilt right, bubble moves left, like a real level)
            val scaleFactor = 40f  // Sensitivity of bubble movement
            val bubbleX = center.x + (roll * scaleFactor).coerceIn(-size.width / 3, size.width / 3)
            val bubbleY = center.y + (pitch * scaleFactor).coerceIn(-size.height / 3, size.height / 3)

            // Bubble with radial gradient for 3D appearance
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFEB3B),  // Bright yellow center
                        Color(0xFFFFC107)   // Orange edges
                    )
                ),
                radius = 30.dp.toPx(),
                center = Offset(bubbleX, bubbleY)
            )

            // Highlight spot on bubble for glossy 3D effect
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = 15.dp.toPx(),
                center = Offset(bubbleX - 8.dp.toPx(), bubbleY - 8.dp.toPx())  // Offset for light source
            )
        }
    }
}

@Composable
fun LevelReadingCard(label: String, value: Float, color: Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .width(120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%.1f°".format(value),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun LevelStatusIndicator(roll: Float, pitch: Float) {
    // Determines if device is level within tolerance threshold
    val threshold = 0.5f  // ±0.5° tolerance for "perfectly level"
    val isLevel = abs(roll) < threshold && abs(pitch) < threshold

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Animated status dot - pulses when level is achieved
        val alpha by animateFloatAsState(
            targetValue = if (isLevel) 1f else 0.3f,
            animationSpec = if (isLevel) {
                // Pulsing animation when level - draws attention to success
                infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                // Quick fade when not level
                tween(300)
            },
            label = "alpha"
        )

        // Status indicator dot with color coding
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    if (isLevel)
                        Color(0xFF4CAF50)  // Green = level (goal achieved)
                    else
                        Color(0xFFFF9800).copy(alpha = alpha)  // Orange = tilted (adjust needed)
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Status text matches dot color for clarity
        Text(
            text = if (isLevel) "LEVEL" else "TILTED",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isLevel) Color(0xFF4CAF50) else Color(0xFFFF9800)
        )
    }
}

@Composable
fun SimulationControls(
    useSimulation: Boolean,
    heading: Float,
    roll: Float,
    pitch: Float,
    onToggleSimulation: (Boolean) -> Unit,
    onHeadingChange: (Float) -> Unit,
    onRollChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.4f)  // Darker to separate from main content
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Toggle between real sensors and manual simulation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "SIMULATION MODE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Test without moving device",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = useSimulation,
                    onCheckedChange = onToggleSimulation,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFFB74D),  // Orange accent when active
                        checkedTrackColor = Color(0xFFFFB74D).copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }

            // Manual control sliders - only shown when simulation is enabled
            if (useSimulation) {
                Spacer(modifier = Modifier.height(20.dp))

                // Heading slider (0-360°) - controls compass direction
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HEADING",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${heading.toInt()}°",
                            color = Color(0xFF64B5F6),  // Blue matches compass
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = heading,
                        onValueChange = onHeadingChange,
                        valueRange = 0f..360f,  // Full circle rotation
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF64B5F6),
                            activeTrackColor = Color(0xFF64B5F6).copy(alpha = 0.8f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Roll slider (-10 to 10°) - controls left/right tilt
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ROLL (Left/Right)",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "%.1f°".format(roll),
                            color = Color(0xFFFF7043),  // Orange matches roll card
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = roll,
                        onValueChange = onRollChange,
                        valueRange = -10f..10f,  // ±10° covers normal tilting range
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF7043),
                            activeTrackColor = Color(0xFFFF7043).copy(alpha = 0.8f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pitch slider (-10 to 10°) - controls forward/backward tilt
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PITCH (Forward/Back)",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "%.1f°".format(pitch),
                            color = Color(0xFF66BB6A),  // Green matches pitch card
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = pitch,
                        onValueChange = onPitchChange,
                        valueRange = -10f..10f,  // ±10° covers normal tilting range
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF66BB6A),
                            activeTrackColor = Color(0xFF66BB6A).copy(alpha = 0.8f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    }
}

// Converts compass heading to cardinal/intercardinal direction label
// Divides 360° into 8 sectors of 45° each
fun getDirectionLabel(heading: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    // Add 22.5° offset so North is centered at 0° (337.5° to 22.5°)
    val index = ((heading + 22.5) / 45.0).toInt() % 8
    return directions[index]
}

// Dynamic background that changes color based on device tilt
// Provides visual feedback - green when level, blue when tilted
fun getTiltColor(roll: Float, pitch: Float): Color {
    return when {
        // Significantly tilted - warmer blue indicates active adjustment needed
        abs(roll) > 2 || abs(pitch) > 2 -> Color(0xFF283593)
        // Nearly level (within 0.5°) - green indicates success/goal achieved
        abs(roll) < 0.5 && abs(pitch) < 0.5 -> Color(0xFF1B5E20)
        // Default state - cool blue for general use
        else -> Color(0xFF1A237E)
    }
}