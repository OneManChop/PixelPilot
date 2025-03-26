package com.openipc.pixelpilot.openxr;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Surface;

import org.khronos.openxr.OpenXR;
import org.khronos.openxr.XrInstance;
import org.khronos.openxr.XrSession;
import org.khronos.openxr.XrSpace;
import org.khronos.openxr.XrSystemId;
import org.khronos.openxr.XrViewConfigurationType;
import org.khronos.openxr.XrReferenceSpaceType;
import org.khronos.openxr.XrSessionState;
import org.khronos.openxr.XrResult;
import org.khronos.openxr.XrView;
import org.khronos.openxr.XrSwapchain;
import org.khronos.openxr.XrCompositionLayerProjection;
import org.khronos.openxr.XrFrameState;
import org.khronos.openxr.XrPosef;
import org.khronos.openxr.XrVector3f;
import org.khronos.openxr.XrQuaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenXRManager handles the OpenXR session lifecycle and rendering.
 * It provides interfaces for rendering video content in VR and positioning UI elements in 3D space.
 */
public class OpenXRManager {
    private static final String TAG = "OpenXRManager";
    
    // OpenXR instance and session objects
    private XrInstance mInstance;
    private XrSession mSession;
    private XrSystemId mSystemId;
    private XrSpace mAppSpace;
    private XrSpace mViewSpace;
    private XrSessionState mSessionState = XrSessionState.XR_SESSION_STATE_UNKNOWN;
    
    // View configuration
    private XrViewConfigurationType mViewConfigType = XrViewConfigurationType.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    private XrView[] mViews;
    
    // Swapchains for rendering
    private List<XrSwapchain> mSwapchains;
    
    // Callback interfaces
    private VideoRenderCallback mVideoRenderCallback;
    private UIRenderCallback mUIRenderCallback;
    
    // Context and activity references
    private Context mContext;
    private Activity mActivity;
    
    // Hand tracking and passthrough managers
    private HandTrackingManager mHandTrackingManager;
    private PassthroughManager mPassthroughManager;
    
    /**
     * Constructor for OpenXRManager
     * @param context Application context
     * @param activity Activity reference for lifecycle management
     */
    public OpenXRManager(Context context, Activity activity) {
        mContext = context;
        mActivity = activity;
        mSwapchains = new ArrayList<>();
    }
    
    /**
     * Initialize the OpenXR instance and system
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            // Create OpenXR instance
            mInstance = OpenXR.createInstance(
                    "PixelPilot", // Application name
                    "OpenIPC",    // Engine name
                    1,            // Application version
                    1,            // Engine version
                    new String[] { // Required extensions
                            "XR_KHR_android_create_instance",
                            "XR_EXT_hand_tracking",
                            "XR_FB_hand_tracking_mesh",
                            "XR_FB_passthrough",  // Meta Quest passthrough extension
                            "XR_FB_spatial_entity" // Meta Quest spatial anchors
                    }
            );
            
            if (mInstance == null) {
                Log.e(TAG, "Failed to create OpenXR instance");
                return false;
            }
            
            // Get system ID
            mSystemId = mInstance.getSystem(XrViewConfigurationType.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
            
            Log.i(TAG, "OpenXR instance and system initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing OpenXR", e);
            return false;
        }
    }
    
    /**
     * Create an OpenXR session
     * @param surface Android surface for rendering
     * @return true if session creation was successful
     */
    public boolean createSession(Surface surface) {
        try {
            // Create session
            mSession = mInstance.createSession(mSystemId, surface);
            
            if (mSession == null) {
                Log.e(TAG, "Failed to create OpenXR session");
                return false;
            }
            
            // Create reference spaces
            mAppSpace = mSession.createReferenceSpace(XrReferenceSpaceType.XR_REFERENCE_SPACE_TYPE_LOCAL);
            mViewSpace = mSession.createReferenceSpace(XrReferenceSpaceType.XR_REFERENCE_SPACE_TYPE_VIEW);
            
            // Initialize view configuration
            initializeViewConfiguration();
            
            // Create swapchains
            createSwapchains();
            
            // Initialize hand tracking with Hand Tracking 2.1
            mHandTrackingManager = new HandTrackingManager(mInstance, mSession, mAppSpace);
            
            // Initialize passthrough
            mPassthroughManager = new PassthroughManager(mInstance, mSession);
            
            Log.i(TAG, "OpenXR session created successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error creating OpenXR session", e);
            return false;
        }
    }
    
    /**
     * Toggle passthrough mode
     * @return true if the toggle was successful
     */
    public boolean togglePassthrough() {
        if (mPassthroughManager != null) {
            return mPassthroughManager.togglePassthrough();
        }
        return false;
    }
    
    /**
     * Check if passthrough is currently enabled
     * @return true if passthrough is enabled
     */
    public boolean isPassthroughEnabled() {
        if (mPassthroughManager != null) {
            return mPassthroughManager.isPassthroughEnabled();
        }
        return false;
    }
    
    /**
     * Set passthrough mode
     * @param enabled true to enable passthrough, false to disable
     * @return true if the operation was successful
     */
    public boolean setPassthroughEnabled(boolean enabled) {
        if (mPassthroughManager != null) {
            if (enabled) {
                return mPassthroughManager.startPassthrough();
            } else {
                return mPassthroughManager.stopPassthrough();
            }
        }
        return false;
    }
    
    /**
     * Get the hand tracking manager
     * @return HandTrackingManager instance
     */
    public HandTrackingManager getHandTrackingManager() {
        return mHandTrackingManager;
    }
    
    /**
     * Get the passthrough manager
     * @return PassthroughManager instance
     */
    public PassthroughManager getPassthroughManager() {
        return mPassthroughManager;
    }
    
    /**
     * Get the OpenXR instance
     * @return XrInstance
     */
    public XrInstance getInstance() {
        return mInstance;
    }
    
    /**
     * Get the OpenXR session
     * @return XrSession
     */
    public XrSession getSession() {
        return mSession;
    }
    
    /**
     * Get the application space
     * @return XrSpace
     */
    public XrSpace getAppSpace() {
        return mAppSpace;
    }
    
    /**
     * Initialize the view configuration for stereo rendering
     */
    private void initializeViewConfiguration() {
        // Get view configuration properties
        int viewCount = mInstance.getViewConfigurationViewCount(mSystemId, mViewConfigType);
        mViews = new XrView[viewCount];
        
        for (int i = 0; i < viewCount; i++) {
            mViews[i] = new XrView();
            mViews[i].pose = new XrPosef();
            mViews[i].pose.position = new XrVector3f();
            mViews[i].pose.orientation = new XrQuaternionf();
            mViews[i].fov = mInstance.getViewConfigurationFov(mSystemId, mViewConfigType, i);
        }
        
        Log.i(TAG, "View configuration initialized with " + viewCount + " views");
    }
    
    /**
     * Create swapchains for rendering
     */
    private void createSwapchains() {
        // For each view, create a swapchain
        for (int i = 0; i < mViews.length; i++) {
            XrSwapchain swapchain = mSession.createSwapchain(
                    1920, // Width
                    1080, // Height
                    1,    // Sample count
                    1,    // Array size
                    1,    // Mip count
                    0,    // Create flags
                    0     // Usage flags
            );
            mSwapchains.add(swapchain);
        }
        
        Log.i(TAG, "Created " + mSwapchains.size() + " swapchains");
    }
    
    /**
     * Begin an OpenXR frame
     * @return XrFrameState containing frame timing information
     */
    public XrFrameState beginFrame() {
        XrFrameState frameState = new XrFrameState();
        XrResult result = mSession.beginFrame(frameState);
        
        if (result != XrResult.XR_SUCCESS) {
            Log.e(TAG, "Failed to begin frame: " + result);
        }
        
        return frameState;
    }
    
    /**
     * End an OpenXR frame and submit layers for display
     * @param frameState Frame state from beginFrame
     * @param layers Composition layers to submit
     */
    public void endFrame(XrFrameState frameState, XrCompositionLayerProjection[] layers) {
        XrResult result = mSession.endFrame(frameState.predictedDisplayTime, layers);
        
        if (result != XrResult.XR_SUCCESS) {
            Log.e(TAG, "Failed to end frame: " + result);
        }
    }
    
    /**
     * Update the session state
     * @return Current session state
     */
    public XrSessionState updateSessionState() {
        XrSessionState newState = mSession.getSessionState();
        
        if (newState != mSessionState) {
            Log.i(TAG, "Session state changed from " + mSessionState + " to " + newState);
            mSessionState = newState;
            
            // Handle session state changes
            switch (mSessionState) {
                case XR_SESSION_STATE_READY:
                    mSession.beginSession();
                    break;
                case XR_SESSION_STATE_STOPPING:
                    mSession.endSession();
                    break;
            }
        }
        
        return mSessionState;
    }
    
    /**
     * Get the current head pose
     * @return XrPosef representing the head pose
     */
    public XrPosef getHeadPose() {
        return mSession.locateSpace(mViewSpace, mAppSpace, 0).pose;
    }
    
    /**
     * Set the video render callback
     * @param callback Callback for video rendering
     */
    public void setVideoRenderCallback(VideoRenderCallback callback) {
        mVideoRenderCallback = callback;
    }
    
    /**
     * Set the UI render callback
     * @param callback Callback for UI rendering
     */
    public void setUIRenderCallback(UIRenderCallback callback) {
        mUIRenderCallback = callback;
    }
    
    /**
     * Clean up OpenXR resources
     */
    public void cleanup() {
        // Clean up hand tracking and passthrough
        if (mHandTrackingManager != null) {
            mHandTrackingManager.cleanup();
            mHandTrackingManager = null;
        }
        
        if (mPassthroughManager != null) {
            mPassthroughManager.cleanup();
            mPassthroughManager = null;
        }
        
        if (mSession != null) {
            try {
                if (mSessionState == XrSessionState.XR_SESSION_STATE_FOCUSED ||
                    mSessionState == XrSessionState.XR_SESSION_STATE_VISIBLE) {
                    mSession.endSession();
                }
                
                // Destroy spaces
                if (mAppSpace != null) {
                    mAppSpace.destroy();
                    mAppSpace = null;
                }
                
                if (mViewSpace != null) {
                    mViewSpace.destroy();
                    mViewSpace = null;
                }
                
                // Destroy swapchains
                for (XrSwapchain swapchain : mSwapchains) {
                    swapchain.destroy();
                }
                mSwapchains.clear();
                
                // Destroy session
                mSession.destroy();
                mSession = null;
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up session", e);
            }
        }
        
        if (mInstance != null) {
            try {
                mInstance.destroy();
                mInstance = null;
            } catch (Exception e) {
                Log.e(TAG, "Error destroying instance", e);
            }
        }
        
        Log.i(TAG, "OpenXR resources cleaned up");
    }
    
    /**
     * Interface for video rendering callbacks
     */
    public interface VideoRenderCallback {
        /**
         * Called when a video frame should be rendered
         * @param swapchainIndex Index of the swapchain to render to
         * @param viewIndex Index of the view (left or right eye)
         * @param pose Pose of the view
         */
        void onRenderVideoFrame(int swapchainIndex, int viewIndex, XrPosef pose);
    }
    
    /**
     * Interface for UI rendering callbacks
     */
    public interface UIRenderCallback {
        /**
         * Called when UI elements should be rendered
         * @param swapchainIndex Index of the swapchain to render to
         * @param viewIndex Index of the view (left or right eye)
         * @param pose Pose of the view
         * @param headPose Current head pose
         */
        void onRenderUIElements(int swapchainIndex, int viewIndex, XrPosef pose, XrPosef headPose);
    }
}
