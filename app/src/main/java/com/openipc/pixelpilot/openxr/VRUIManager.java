package com.openipc.pixelpilot.openxr;

import android.content.Context;
import android.util.Log;
import android.view.View;

import com.openipc.pixelpilot.osd.MovableLayout;
import com.openipc.pixelpilot.osd.OSDElement;

import org.khronos.openxr.XrPosef;
import org.khronos.openxr.XrQuaternionf;
import org.khronos.openxr.XrVector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VRUIManager handles the conversion of 2D UI elements to 3D VR UI elements
 * and manages their positioning and interaction in 3D space.
 */
public class VRUIManager implements VRUIElement.VRUIElementCallback, HandTrackingManager.HandTrackingCallback {
    private static final String TAG = "VRUIManager";
    
    // Context
    private Context context;
    
    // List of VR UI elements
    private List<VRUIElement> uiElements;
    
    // Map of element IDs to VR UI elements
    private Map<String, VRUIElement> uiElementMap;
    
    // Head pose for positioning elements relative to the user
    private XrPosef headPose;
    
    // Interaction state
    private VRUIElement hoveredElement;
    private VRUIElement grabbedElement;
    private boolean leftPinching;
    private boolean rightPinching;
    private XrVector3f leftPinchPosition;
    private XrVector3f rightPinchPosition;
    
    // Constants
    private static final float DEFAULT_DISTANCE = 1.0f; // 1 meter from user
    private static final float ELEMENT_SPACING = 0.05f; // 5cm between elements
    private static final float ELEMENT_WIDTH = 0.2f;    // 20cm wide
    private static final float ELEMENT_HEIGHT = 0.05f;  // 5cm tall
    
    /**
     * Constructor for VRUIManager
     * @param context Application context
     */
    public VRUIManager(Context context) {
        this.context = context;
        this.uiElements = new ArrayList<>();
        this.uiElementMap = new HashMap<>();
        this.headPose = new XrPosef();
        this.headPose.position = new XrVector3f(0, 0, 0);
        this.headPose.orientation = new XrQuaternionf(0, 0, 0, 1);
        this.leftPinchPosition = new XrVector3f();
        this.rightPinchPosition = new XrVector3f();
    }
    
    /**
     * Convert OSD elements to VR UI elements
     * @param osdElements List of OSD elements to convert
     */
    public void convertOSDElements(List<OSDElement> osdElements) {
        // Clear existing elements
        for (VRUIElement element : uiElements) {
            element.cleanup();
        }
        uiElements.clear();
        uiElementMap.clear();
        
        // Convert each OSD element to a VR UI element
        float yOffset = 0.3f; // Start 30cm above eye level
        
        for (OSDElement osdElement : osdElements) {
            // Skip elements that are not visible
            if (osdElement.layout.getVisibility() != View.VISIBLE) {
                continue;
            }
            
            // Create position for the element
            XrVector3f position = new XrVector3f();
            position.x = 0.0f; // Centered horizontally
            position.y = yOffset; // Position vertically
            position.z = -DEFAULT_DISTANCE; // 1 meter in front of user
            
            // Create VR UI element
            VRUIElement vrElement = new VRUIElement(
                    osdElement.prefName(),
                    osdElement.layout,
                    ELEMENT_WIDTH,
                    ELEMENT_HEIGHT,
                    position
            );
            
            // Set callback
            vrElement.setCallback(this);
            
            // Add to lists
            uiElements.add(vrElement);
            uiElementMap.put(vrElement.getId(), vrElement);
            
            // Update y offset for next element
            yOffset -= (ELEMENT_HEIGHT + ELEMENT_SPACING);
        }
        
        Log.i(TAG, "Converted " + uiElements.size() + " OSD elements to VR UI elements");
    }
    
    /**
     * Update the head pose
     * @param newHeadPose New head pose
     */
    public void updateHeadPose(XrPosef newHeadPose) {
        headPose = newHeadPose;
        
        // Update element positions to follow head if needed
        updateElementPositions();
    }
    
    /**
     * Update element positions based on head pose
     */
    private void updateElementPositions() {
        // This is a simplified implementation - in a real app, you would use
        // more sophisticated positioning logic to create a curved UI that follows the user
        
        // For now, we'll just position elements in front of the user
        for (VRUIElement element : uiElements) {
            if (element.isGrabbed()) {
                // Skip grabbed elements
                continue;
            }
            
            // Get current position
            XrVector3f position = element.getPose().position;
            
            // Calculate new position based on head pose
            XrVector3f newPosition = new XrVector3f();
            newPosition.x = headPose.position.x + position.x;
            newPosition.y = headPose.position.y + position.y;
            newPosition.z = headPose.position.z + position.z;
            
            // Update element position
            element.setPosition(newPosition);
        }
    }
    
    /**
     * Update interaction based on hand tracking
     * @param leftHandJoints Left hand joint locations
     * @param rightHandJoints Right hand joint locations
     */
    @Override
    public void onHandsUpdated(XrHandJointLocationEXT[] leftHandJoints, XrHandJointLocationEXT[] rightHandJoints) {
        // Check for intersections with UI elements
        if (leftHandJoints != null) {
            // Use index finger tip for interaction
            XrVector3f indexTip = leftHandJoints[XrHandJointEXT.XR_HAND_JOINT_INDEX_TIP_EXT].pose.position;
            checkElementIntersection(indexTip, true);
        }
        
        if (rightHandJoints != null) {
            // Use index finger tip for interaction
            XrVector3f indexTip = rightHandJoints[XrHandJointEXT.XR_HAND_JOINT_INDEX_TIP_EXT].pose.position;
            checkElementIntersection(indexTip, false);
        }
    }
    
    /**
     * Check if a point intersects with any UI element
     * @param point Point to check
     * @param isLeft true if left hand, false if right hand
     */
    private void checkElementIntersection(XrVector3f point, boolean isLeft) {
        // Find the closest intersecting element
        VRUIElement closestElement = null;
        float closestDistance = Float.MAX_VALUE;
        
        for (VRUIElement element : uiElements) {
            if (!element.isVisible()) {
                continue;
            }
            
            if (element.intersects(point)) {
                // Calculate distance to element
                float distance = calculateDistance(point, element.getPose().position);
                
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestElement = element;
                }
            }
        }
        
        // Update hover state
        if (isLeft) {
            if (hoveredElement != closestElement) {
                if (hoveredElement != null) {
                    hoveredElement.setHovered(false);
                }
                
                hoveredElement = closestElement;
                
                if (hoveredElement != null) {
                    hoveredElement.setHovered(true);
                }
            }
        }
        
        // Update grab state if pinching
        if (isLeft && leftPinching) {
            if (grabbedElement == null && closestElement != null) {
                // Start grab
                grabbedElement = closestElement;
                grabbedElement.startGrab(leftPinchPosition);
            } else if (grabbedElement != null) {
                // Update grab
                grabbedElement.updateGrab(leftPinchPosition);
            }
        } else if (!isLeft && rightPinching) {
            if (grabbedElement == null && closestElement != null) {
                // Start grab
                grabbedElement = closestElement;
                grabbedElement.startGrab(rightPinchPosition);
            } else if (grabbedElement != null) {
                // Update grab
                grabbedElement.updateGrab(rightPinchPosition);
            }
        }
    }
    
    /**
     * Calculate distance between two points
     * @param a First point
     * @param b Second point
     * @return Distance in meters
     */
    private float calculateDistance(XrVector3f a, XrVector3f b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Handle left hand pinch
     * @param isPinching true if pinching, false if released
     * @param position Position of the pinch
     */
    @Override
    public void onLeftHandPinch(boolean isPinching, XrVector3f position) {
        leftPinching = isPinching;
        leftPinchPosition = position;
        
        if (!isPinching && grabbedElement != null) {
            // End grab
            grabbedElement.endGrab();
            grabbedElement = null;
        }
    }
    
    /**
     * Handle right hand pinch
     * @param isPinching true if pinching, false if released
     * @param position Position of the pinch
     */
    @Override
    public void onRightHandPinch(boolean isPinching, XrVector3f position) {
        rightPinching = isPinching;
        rightPinchPosition = position;
        
        if (!isPinching && grabbedElement != null) {
            // End grab
            grabbedElement.endGrab();
            grabbedElement = null;
        }
    }
    
    /**
     * Get all VR UI elements
     * @return List of VR UI elements
     */
    public List<VRUIElement> getUIElements() {
        return uiElements;
    }
    
    /**
     * Get a VR UI element by ID
     * @param id Element ID
     * @return VR UI element or null if not found
     */
    public VRUIElement getUIElement(String id) {
        return uiElementMap.get(id);
    }
    
    /**
     * Get a VR UI element by name
     * @param name Element name
     * @return VR UI element or null if not found
     */
    public VRUIElement getElementByName(String name) {
        for (VRUIElement element : uiElements) {
            if (name.equals(element.getName())) {
                return element;
            }
        }
        return null;
    }
    
    /**
     * Add a VR UI element
     * @param element Element to add
     */
    public void addElement(VRUIElement element) {
        if (element == null) {
            return;
        }
        
        // Add to lists
        uiElements.add(element);
        
        // Add to map if it has an ID
        if (element.getId() != null) {
            uiElementMap.put(element.getId(), element);
        }
        
        // Set callback
        element.setCallback(this);
        
        Log.i(TAG, "Added VR UI element: " + element.getName());
    }
    
    /**
     * Set the visibility of a VR UI element
     * @param id Element ID
     * @param visible true to show, false to hide
     */
    public void setElementVisible(String id, boolean visible) {
        VRUIElement element = uiElementMap.get(id);
        if (element != null) {
            element.setVisible(visible);
        }
    }
    
    /**
     * Update all VR UI elements
     */
    public void updateElements() {
        for (VRUIElement element : uiElements) {
            if (element.needsUpdate()) {
                element.updateBitmap();
            }
        }
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        for (VRUIElement element : uiElements) {
            element.cleanup();
        }
        uiElements.clear();
        uiElementMap.clear();
    }
    
    // VRUIElement.VRUIElementCallback implementation
    
    @Override
    public void onHoverEnter(VRUIElement element) {
        // Handle hover enter
        Log.d(TAG, "Hover enter: " + element.getId());
    }
    
    @Override
    public void onHoverExit(VRUIElement element) {
        // Handle hover exit
        Log.d(TAG, "Hover exit: " + element.getId());
    }
    
    @Override
    public void onClick(VRUIElement element) {
        // Handle click
        Log.d(TAG, "Click: " + element.getId());
    }
    
    @Override
    public void onGrabStart(VRUIElement element) {
        // Handle grab start
        Log.d(TAG, "Grab start: " + element.getId());
    }
    
    @Override
    public void onGrabMove(VRUIElement element) {
        // Handle grab move
        // Log.d(TAG, "Grab move: " + element.getId());
    }
    
    @Override
    public void onGrabEnd(VRUIElement element) {
        // Handle grab end
        Log.d(TAG, "Grab end: " + element.getId());
        
        // Save the new position
        // In a real app, you would persist this position
    }
}
