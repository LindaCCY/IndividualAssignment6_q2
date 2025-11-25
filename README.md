# Compass & Digital Level App
An Android app that combines a magnetic compass and digital level using device sensors.

## Features
1. Compass: Displays magnetic heading using magnetometer and accelerometer
2. Digital Level: Shows device tilt with roll and pitch values using gyroscope
   - Rotating compass needle visualization
   - Real-time sensor data updates
   - Interactive and fun UI

## How It Works
### Compass
- Uses magnetometer to detect magnetic field
- Uses accelerometer for device orientation
- Calculates true heading in degrees 
- Displays rotating compass needle on canvas

### Digital Level
- Uses gyroscope to detect device rotation
- Calculates roll 
- Calculates pitch 
- Shows live angle measurements

## Setup
1. Clone the repository
2. Open in Android Studio
3. Build and run on a physical device (sensors required)
