package com.openipc.pixelpilot;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.openipc.msposd.MspOsdManager;
import com.openipc.wfbngrtl8812.WfbNgLink;

/**
 * Integration class that connects the MSPOSD module with the PixelPilot application.
 * This class handles the initialization of the MSPOSD overlay and processes telemetry data
 * received from the WFB-NG link.
 */
public class MspOsdIntegration {
    private static final String TAG = "MspOsdIntegration";
    
    private final Context mContext;
    private final ViewGroup mVideoContainer;
    private final WfbNgLink mWfbLink;
    private MspOsdManager mOsdManager;
    private boolean mIsEnabled = true;
    
    /**
     * Creates a new MspOsdIntegration instance.
     * 
     * @param context The application context
     * @param videoContainer The ViewGroup that contains the video surface
     * @param wfbLink The WfbNgLink instance for receiving telemetry data
     */
    public MspOsdIntegration(Context context, ViewGroup videoContainer, WfbNgLink wfbLink) {
        mContext = context;
        mVideoContainer = videoContainer;
        mWfbLink = wfbLink;
    }
    
    /**
     * Initializes the MSPOSD overlay.
     */
    public void initialize() {
        if (mOsdManager != null) {
            release();
        }
        
        mOsdManager = new MspOsdManager(mContext);
        mOsdManager.attachToView(mVideoContainer);
        
        Log.d(TAG, "MSPOSD overlay initialized");
    }
    
    /**
     * Processes telemetry data received from the WFB-NG link.
     * 
     * @param data The telemetry data as a byte array
     */
    public void processTelemetry(byte[] data) {
        if (mOsdManager != null && mIsEnabled) {
            mOsdManager.processTelemetry(data);
        }
    }
    
    /**
     * Enables or disables the MSPOSD overlay.
     * 
     * @param enabled True to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
        if (mOsdManager != null) {
            mOsdManager.setEnabled(enabled);
        }
    }
    
    /**
     * Checks if the MSPOSD overlay is enabled.
     * 
     * @return True if enabled, false otherwise
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }
    
    /**
     * Releases resources used by the MSPOSD overlay.
     */
    public void release() {
        if (mOsdManager != null) {
            mOsdManager.release();
            mOsdManager = null;
        }
    }
}
