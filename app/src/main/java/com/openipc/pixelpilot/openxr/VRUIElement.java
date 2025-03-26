package com.openipc.pixelpilot.openxr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

import org.khronos.openxr.XrPosef;
import org.khronos.openxr.XrQuaternionf;
import org.khronos.openxr.XrVector3f;

/**
 * VRUIElement represents a UI element in 3D space.
 * It converts Android Views to textures that can be rendered in VR.
 */
public class VRUIElement {
    // Position and orientation in 3D space
    private XrPosef pose;
    
    // Size in meters
    private float width;
    private float height;
    
    // Original Android View
    private View view;
    
    // Bitmap for rendering
    private Bitmap bitmap;
    private boolean needsUpdate;
    
    // Interaction state
    private boolean isGrabbed;
    private boolean isHovered;
    private XrVector3f grabOffset;
    
    // Visibility
    private boolean isVisible;
    
    // Unique identifier
    private String id;
    
    // Callback for interaction events
    private VRUIElementCallback callback;
    
    // Click listener
    private Runnable onClickListener;
    
    // Element name
    private String name;
    
    // Text content
    private String text;
    
    /**
     * Constructor for VRUIElement
     * @param id Unique identifier
     * @param view Android View to render
     * @param width Width in meters
     * @param height Height in meters
     * @param position Initial position
     */
    public VRUIElement(String id, View view, float width, float height, XrVector3f position) {
        this.id = id;
        this.view = view;
        this.width = width;
        this.height = height;
        this.isVisible = true;
        this.needsUpdate = true;
        this.isGrabbed = false;
        this.isHovered = false;
        this.grabOffset = new XrVector3f();
        
        // Initialize pose
        this.pose = new XrPosef();
        this.pose.position = position;
        this.pose.orientation = new XrQuaternionf(0, 0, 0, 1); // Identity quaternion
        
        // Create bitmap for rendering
        updateBitmap();
    }
    
    /**
     * Constructor for VRUIElement with context
     * @param context Application context
     */
    public VRUIElement(Context context) {
        this.isVisible = true;
        this.needsUpdate = true;
        this.isGrabbed = false;
        this.isHovered = false;
        this.grabOffset = new XrVector3f();
        
        // Default size
        this.width = 0.2f;  // 20cm wide
        this.height = 0.05f; // 5cm tall
        
        // Default position (in front of user)
        XrVector3f position = new XrVector3f();
        position.x = 0.0f;
        position.y = 0.0f;
        position.z = -1.0f; // 1 meter in front of user
        
        // Initialize pose
        this.pose = new XrPosef();
        this.pose.position = position;
        this.pose.orientation = new XrQuaternionf(0, 0, 0, 1); // Identity quaternion
        
        // Create a default view
        // In a real implementation, you would create a proper UI element
        this.view = new View(context);
    }
    
    /**
     * Update the bitmap from the Android View
     */
    public void updateBitmap() {
        if (view == null) {
            return;
        }
        
        // Measure and layout the view
        int widthPx = (int) (width * 1000); // Convert meters to pixels (approximate)
        int heightPx = (int) (height * 1000);
        
        view.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
        
        // Create bitmap if needed
        if (bitmap == null || bitmap.getWidth() != widthPx || bitmap.getHeight() != heightPx) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        }
        
        // Draw view to bitmap
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        view.draw(canvas);
        
        needsUpdate = false;
    }
    
    /**
     * Check if the bitmap needs to be updated
     * @return true if the bitmap needs to be updated
     */
    public boolean needsUpdate() {
        return needsUpdate;
    }
    
    /**
     * Mark the bitmap as needing an update
     */
    public void markForUpdate() {
        needsUpdate = true;
    }
    
    /**
     * Get the bitmap for rendering
     * @return Bitmap
     */
    public Bitmap getBitmap() {
        if (needsUpdate) {
            updateBitmap();
        }
        return bitmap;
    }
    
    /**
     * Get the pose in 3D space
     * @return XrPosef
     */
    public XrPosef getPose() {
        return pose;
    }
    
    /**
     * Set the position in 3D space
     * @param position New position
     */
    public void setPosition(XrVector3f position) {
        pose.position = position;
    }
    
    /**
     * Set the orientation in 3D space
     * @param orientation New orientation
     */
    public void setOrientation(XrQuaternionf orientation) {
        pose.orientation = orientation;
    }
    
    /**
     * Get the width in meters
     * @return Width
     */
    public float getWidth() {
        return width;
    }
    
    /**
     * Get the height in meters
     * @return Height
     */
    public float getHeight() {
        return height;
    }
    
    /**
     * Set the visibility
     * @param visible true to show, false to hide
     */
    public void setVisible(boolean visible) {
        isVisible = visible;
    }
    
    /**
     * Check if the element is visible
     * @return true if visible
     */
    public boolean isVisible() {
        return isVisible;
    }
    
    /**
     * Get the unique identifier
     * @return ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Set the callback for interaction events
     * @param callback Callback
     */
    public void setCallback(VRUIElementCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Check if a point in 3D space intersects with this element
     * @param point Point in 3D space
     * @return true if the point intersects
     */
    public boolean intersects(XrVector3f point) {
        // Transform point to local space
        XrVector3f localPoint = transformPointToLocalSpace(point);
        
        // Check if point is within bounds
        return localPoint.x >= -width / 2 && localPoint.x <= width / 2 &&
               localPoint.y >= -height / 2 && localPoint.y <= height / 2 &&
               Math.abs(localPoint.z) < 0.01f; // Small threshold for depth
    }
    
    /**
     * Transform a point from world space to local space
     * @param point Point in world space
     * @return Point in local space
     */
    private XrVector3f transformPointToLocalSpace(XrVector3f point) {
        // This is a simplified transformation - in a real implementation,
        // you would use matrix math to properly transform the point
        XrVector3f result = new XrVector3f();
        
        // Translate
        result.x = point.x - pose.position.x;
        result.y = point.y - pose.position.y;
        result.z = point.z - pose.position.z;
        
        // TODO: Apply inverse rotation using quaternion
        // For simplicity, we're assuming identity rotation here
        
        return result;
    }
    
    /**
     * Handle hover state
     * @param isHovering true if hovering, false otherwise
     */
    public void setHovered(boolean isHovering) {
        if (isHovered != isHovering) {
            isHovered = isHovering;
            
            // Notify callback
            if (callback != null) {
                if (isHovered) {
                    callback.onHoverEnter(this);
                } else {
                    callback.onHoverExit(this);
                }
            }
            
            // Mark for update to reflect hover state
            markForUpdate();
        }
    }
    
    /**
     * Start grabbing the element
     * @param grabPosition Position of the grab in world space
     */
    public void startGrab(XrVector3f grabPosition) {
        isGrabbed = true;
        
        // Calculate grab offset
        grabOffset.x = pose.position.x - grabPosition.x;
        grabOffset.y = pose.position.y - grabPosition.y;
        grabOffset.z = pose.position.z - grabPosition.z;
        
        // Notify callback
        if (callback != null) {
            callback.onGrabStart(this);
        }
    }
    
    /**
     * Update grab position
     * @param newGrabPosition New position of the grab in world space
     */
    public void updateGrab(XrVector3f newGrabPosition) {
        if (!isGrabbed) {
            return;
        }
        
        // Update position based on grab
        XrVector3f newPosition = new XrVector3f();
        newPosition.x = newGrabPosition.x + grabOffset.x;
        newPosition.y = newGrabPosition.y + grabOffset.y;
        newPosition.z = newGrabPosition.z + grabOffset.z;
        
        setPosition(newPosition);
        
        // Notify callback
        if (callback != null) {
            callback.onGrabMove(this);
        }
    }
    
    /**
     * End grabbing the element
     */
    public void endGrab() {
        if (!isGrabbed) {
            return;
        }
        
        isGrabbed = false;
        
        // Notify callback
        if (callback != null) {
            callback.onGrabEnd(this);
        }
    }
    
    /**
     * Check if the element is currently grabbed
     * @return true if grabbed
     */
    public boolean isGrabbed() {
        return isGrabbed;
    }
    
    /**
     * Handle click/select event
     */
    public void click() {
        // Notify callback
        if (callback != null) {
            callback.onClick(this);
        }
        
        // Execute click listener if set
        if (onClickListener != null) {
            onClickListener.run();
        }
        
        // If the view is a LinearLayout, propagate the click to its children
        if (view instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) view;
            for (int i = 0; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                child.performClick();
            }
        } else {
            // Otherwise, just click the view itself
            view.performClick();
        }
        
        // Mark for update to reflect click state
        markForUpdate();
    }
    
    /**
     * Set the click listener
     * @param listener Runnable to execute when clicked
     */
    public void setOnClickListener(Runnable listener) {
        this.onClickListener = listener;
    }
    
    /**
     * Set the element name
     * @param name Element name
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Get the element name
     * @return Element name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Set the text content
     * @param text Text content
     */
    public void setText(String text) {
        this.text = text;
        markForUpdate();
    }
    
    /**
     * Get the text content
     * @return Text content
     */
    public String getText() {
        return text;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }
    
    /**
     * Interface for VRUIElement callbacks
     */
    public interface VRUIElementCallback {
        /**
         * Called when hover enters the element
         * @param element Element being hovered
         */
        void onHoverEnter(VRUIElement element);
        
        /**
         * Called when hover exits the element
         * @param element Element no longer being hovered
         */
        void onHoverExit(VRUIElement element);
        
        /**
         * Called when the element is clicked
         * @param element Element being clicked
         */
        void onClick(VRUIElement element);
        
        /**
         * Called when grabbing starts
         * @param element Element being grabbed
         */
        void onGrabStart(VRUIElement element);
        
        /**
         * Called when the grabbed element is moved
         * @param element Element being moved
         */
        void onGrabMove(VRUIElement element);
        
        /**
         * Called when grabbing ends
         * @param element Element no longer being grabbed
         */
        void onGrabEnd(VRUIElement element);
    }
}
