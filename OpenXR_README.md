# PixelPilot OpenXR Implementation

This document outlines the changes made to convert the PixelPilot Android app to work with OpenXR for Meta Quest 3, enabling a fully immersive VR experience with floating UI elements and a curved video display that follows the user's head movement.

## Overview of Changes

The original PixelPilot app has been enhanced with OpenXR support, allowing it to run as a true VR application on Meta Quest 3 rather than just a flat app. The key enhancements include:

1. **Curved Video Display**: The video feed is now rendered on a curved surface that wraps around the user, providing a more immersive viewing experience.

2. **Floating UI Elements**: All UI elements (telemetry data, controls, etc.) now float in 3D space around the user, positioned for optimal visibility and interaction.

3. **Hand Tracking 2.1**: Added support for Meta Quest Hand Tracking 2.1, allowing users to interact with UI elements using natural hand gestures with improved accuracy and lower latency.

4. **Head Tracking**: The video display and UI elements follow the user's head movement, ensuring they're always in a comfortable viewing position.

5. **Passthrough Mode**: Added support for toggling between immersive mode (black background) and passthrough mode, allowing users to see their surroundings while using the app.

## Implementation Details

### 1. OpenXR Integration

- Added OpenXR dependencies to the project
- Created an `OpenXRManager` class to handle OpenXR session management
- Implemented proper lifecycle management for XR sessions

### 2. Curved Video Rendering

- Created a `CurvedVideoRenderer` class that renders the video feed onto a curved mesh
- Implemented OpenGL shaders for rendering the video texture
- Added head tracking to update the video position based on the user's head movement

### 3. 3D UI System

- Created a `VRUIElement` class to represent UI elements in 3D space
- Implemented a `VRUIManager` to handle the conversion of 2D UI elements to 3D
- Added support for positioning UI elements in a comfortable arc around the user

### 4. Hand Tracking 2.1

- Implemented a `HandTrackingManager` to handle hand tracking using OpenXR extensions
- Added support for Meta Quest Hand Tracking 2.1 with improved accuracy and lower latency
- Configured for high-frequency tracking (90Hz) for more responsive interactions
- Implemented advanced gesture recognition including:
  - Pinch gestures for selecting UI elements
  - Grab gestures for moving and manipulating objects
  - Point gestures for interacting with distant elements
- Added support for hand mesh rendering for visual feedback

### 5. Passthrough Mode

- Implemented a `PassthroughManager` to handle Meta Quest passthrough functionality
- Added support for toggling between immersive mode (black background) and passthrough mode
- Created a UI control for toggling passthrough mode
- Implemented proper background rendering for both modes

### 6. Activity Integration

- Created an `OpenXRVideoActivity` that extends the original `VideoActivity`
- Updated the AndroidManifest.xml to use the new activity
- Implemented proper rendering and update loops for OpenXR

## File Structure

- `app/src/main/java/com/openipc/pixelpilot/openxr/`
  - `OpenXRManager.java`: Manages OpenXR session and rendering
  - `CurvedVideoRenderer.java`: Renders video to a curved surface
  - `VRUIElement.java`: Represents a UI element in 3D space
  - `VRUIManager.java`: Manages 3D UI elements
  - `HandTrackingManager.java`: Handles hand tracking and gestures with Hand Tracking 2.1 support
  - `PassthroughManager.java`: Manages Meta Quest passthrough functionality
  - `OpenXRVideoActivity.java`: Main activity for OpenXR integration

## Usage Instructions

### Running the App

1. Install the app on your Meta Quest 3
2. Launch the app
3. The app will automatically start in VR mode with the curved video display and floating UI elements

### Interacting with UI Elements

- **Viewing UI Elements**: Look around to see UI elements positioned in 3D space
- **Selecting UI Elements**: Point at an element with your index finger and pinch to select
- **Moving UI Elements**: Pinch and hold an element, then move your hand to reposition it
- **Adjusting Video Curvature**: Use the settings menu to adjust the curvature of the video display

### Using Passthrough Mode

- **Toggling Passthrough**: Find the "Toggle Passthrough" button in the UI and select it to switch between immersive mode and passthrough mode
- **When to Use Passthrough**: 
  - Use passthrough mode when you need to be aware of your surroundings
  - Use passthrough mode when setting up your drone or equipment
  - Use immersive mode (no passthrough) for maximum focus on the video feed
- **Performance Note**: Passthrough mode may have a slight impact on performance, so if you experience any lag, try switching back to immersive mode

## Technical Notes

### Performance Considerations

- The app is optimized for the Meta Quest 3's hardware capabilities
- Video decoding is done efficiently to maintain high frame rates
- UI elements are rendered using texture atlases to reduce draw calls

### Known Limitations

- The app requires OpenXR support and hand tracking capabilities
- Some advanced features may not be available on older VR headsets
- The app requires a minimum of Android API level 26

## Future Enhancements

- Add support for controller input as an alternative to hand tracking
- Implement spatial audio for a more immersive experience
- Add more customization options for UI element positioning
- Optimize rendering for better battery life

## Troubleshooting

If you encounter issues with the OpenXR implementation:

1. Ensure your Meta Quest 3 firmware is up to date
2. Check that hand tracking is enabled in your Meta Quest settings
3. Restart the app if the video display or UI elements are not rendering correctly
4. If hand tracking is unreliable, try adjusting the lighting conditions in your environment
