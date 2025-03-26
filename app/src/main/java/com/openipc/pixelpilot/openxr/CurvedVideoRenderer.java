package com.openipc.pixelpilot.openxr;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import org.khronos.openxr.XrPosef;
import org.khronos.openxr.XrQuaternionf;
import org.khronos.openxr.XrVector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * CurvedVideoRenderer handles rendering video to a curved surface in VR.
 * It creates a curved mesh that wraps around the user and renders the video
 * texture onto it.
 */
public class CurvedVideoRenderer {
    private static final String TAG = "CurvedVideoRenderer";
    
    // OpenGL shader program
    private int mProgram;
    
    // Vertex and fragment shaders
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 aPosition;" +
            "attribute vec2 aTexCoord;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * aPosition;" +
            "  vTexCoord = aTexCoord;" +
            "}";
    
    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "varying vec2 vTexCoord;" +
            "uniform sampler2D sTexture;" +
            "void main() {" +
            "  gl_FragColor = texture2D(sTexture, vTexCoord);" +
            "}";
    
    // Mesh data
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTexCoordBuffer;
    private ShortBuffer mIndexBuffer;
    private int mVertexCount;
    private int mIndexCount;
    
    // Texture
    private int mTextureID;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    
    // Transformation matrices
    private float[] mModelMatrix = new float[16];
    private float[] mViewMatrix = new float[16];
    private float[] mProjectionMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];
    
    // Curved surface parameters
    private float mRadius = 2.0f;      // 2 meters radius
    private float mArcAngle = 120.0f;  // 120 degrees arc
    private float mHeight = 1.0f;      // 1 meter height
    private int mSegmentsH = 20;       // Horizontal segments
    private int mSegmentsV = 10;       // Vertical segments
    
    // Position and orientation
    private XrPosef mPose;
    
    // Context
    private Context mContext;
    
    /**
     * Constructor for CurvedVideoRenderer
     * @param context Application context
     */
    public CurvedVideoRenderer(Context context) {
        mContext = context;
        
        // Initialize pose
        mPose = new XrPosef();
        mPose.position = new XrVector3f(0, 0, 0);
        mPose.orientation = new XrQuaternionf(0, 0, 0, 1);
        
        // Initialize matrices
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.setIdentityM(mViewMatrix, 0);
        Matrix.setIdentityM(mProjectionMatrix, 0);
        Matrix.setIdentityM(mMVPMatrix, 0);
        
        // Create curved mesh
        createCurvedMesh();
    }
    
    /**
     * Initialize OpenGL resources
     */
    public void init() {
        // Create shader program
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        
        // Create texture
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureID = textures[0];
        
        // Initialize SurfaceTexture
        mSurfaceTexture = new SurfaceTexture(mTextureID);
        mSurfaceTexture.setDefaultBufferSize(1920, 1080); // Set to your video resolution
        mSurface = new Surface(mSurfaceTexture);
    }
    
    /**
     * Create a curved mesh for the video surface
     */
    private void createCurvedMesh() {
        // Calculate vertex count
        mVertexCount = (mSegmentsH + 1) * (mSegmentsV + 1);
        mIndexCount = mSegmentsH * mSegmentsV * 6; // 2 triangles per quad, 3 vertices per triangle
        
        // Allocate buffers
        ByteBuffer bb = ByteBuffer.allocateDirect(mVertexCount * 3 * 4); // 3 floats per vertex
        bb.order(ByteOrder.nativeOrder());
        mVertexBuffer = bb.asFloatBuffer();
        
        ByteBuffer tb = ByteBuffer.allocateDirect(mVertexCount * 2 * 4); // 2 floats per texcoord
        tb.order(ByteOrder.nativeOrder());
        mTexCoordBuffer = tb.asFloatBuffer();
        
        ByteBuffer ib = ByteBuffer.allocateDirect(mIndexCount * 2); // 2 bytes per index (short)
        ib.order(ByteOrder.nativeOrder());
        mIndexBuffer = ib.asShortBuffer();
        
        // Generate vertices and texture coordinates
        float angleStep = (float) Math.toRadians(mArcAngle) / mSegmentsH;
        float heightStep = mHeight / mSegmentsV;
        
        for (int v = 0; v <= mSegmentsV; v++) {
            float y = mHeight / 2 - v * heightStep;
            float tv = (float) v / mSegmentsV;
            
            for (int h = 0; h <= mSegmentsH; h++) {
                float angle = (float) Math.toRadians(-mArcAngle / 2) + h * angleStep;
                float x = mRadius * (float) Math.sin(angle);
                float z = -mRadius * (float) Math.cos(angle);
                float tu = (float) h / mSegmentsH;
                
                // Add vertex
                mVertexBuffer.put(x);
                mVertexBuffer.put(y);
                mVertexBuffer.put(z);
                
                // Add texture coordinate
                mTexCoordBuffer.put(tu);
                mTexCoordBuffer.put(tv);
            }
        }
        
        // Generate indices
        for (int v = 0; v < mSegmentsV; v++) {
            for (int h = 0; h < mSegmentsH; h++) {
                short bottomLeft = (short) (v * (mSegmentsH + 1) + h);
                short bottomRight = (short) (bottomLeft + 1);
                short topLeft = (short) (bottomLeft + (mSegmentsH + 1));
                short topRight = (short) (topLeft + 1);
                
                // First triangle
                mIndexBuffer.put(bottomLeft);
                mIndexBuffer.put(topLeft);
                mIndexBuffer.put(bottomRight);
                
                // Second triangle
                mIndexBuffer.put(bottomRight);
                mIndexBuffer.put(topLeft);
                mIndexBuffer.put(topRight);
            }
        }
        
        // Reset buffer positions
        mVertexBuffer.position(0);
        mTexCoordBuffer.position(0);
        mIndexBuffer.position(0);
    }
    
    /**
     * Create an OpenGL shader program
     * @param vertexShader Vertex shader source
     * @param fragmentShader Fragment shader source
     * @return Program ID
     */
    private int createProgram(String vertexShader, String fragmentShader) {
        // Load shaders
        int vertexShaderHandle = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        int fragmentShaderHandle = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
        
        // Create program
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            // Attach shaders
            GLES20.glAttachShader(program, vertexShaderHandle);
            GLES20.glAttachShader(program, fragmentShaderHandle);
            
            // Link program
            GLES20.glLinkProgram(program);
            
            // Check link status
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        
        return program;
    }
    
    /**
     * Load an OpenGL shader
     * @param type Shader type (vertex or fragment)
     * @param source Shader source code
     * @return Shader handle
     */
    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader != 0) {
            // Set source
            GLES20.glShaderSource(shader, source);
            
            // Compile shader
            GLES20.glCompileShader(shader);
            
            // Check compile status
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + type + ": " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        
        return shader;
    }
    
    /**
     * Update the surface texture
     */
    public void updateTexture() {
        if (mSurfaceTexture != null) {
            mSurfaceTexture.updateTexImage();
        }
    }
    
    /**
     * Get the surface for video rendering
     * @return Surface
     */
    public Surface getSurface() {
        return mSurface;
    }
    
    /**
     * Set the pose of the curved video surface
     * @param pose New pose
     */
    public void setPose(XrPosef pose) {
        mPose = pose;
    }
    
    /**
     * Update the view matrix based on head pose
     * @param headPose Head pose
     */
    public void updateViewMatrix(XrPosef headPose) {
        // Calculate view matrix based on head pose
        // This is a simplified implementation - in a real app, you would use
        // proper quaternion math to create the view matrix
        
        // For now, we'll just use a simple look-at matrix
        float[] eye = new float[3];
        float[] center = new float[3];
        float[] up = new float[3];
        
        // Eye position is the head position
        eye[0] = headPose.position.x;
        eye[1] = headPose.position.y;
        eye[2] = headPose.position.z;
        
        // Look-at point is 1 meter in front of the head
        // This is a simplification - in a real app, you would use the head orientation
        center[0] = eye[0];
        center[1] = eye[1];
        center[2] = eye[2] - 1.0f;
        
        // Up vector
        up[0] = 0.0f;
        up[1] = 1.0f;
        up[2] = 0.0f;
        
        Matrix.setLookAtM(mViewMatrix, 0, eye[0], eye[1], eye[2], center[0], center[1], center[2], up[0], up[1], up[2]);
    }
    
    /**
     * Set the projection matrix
     * @param projectionMatrix Projection matrix
     */
    public void setProjectionMatrix(float[] projectionMatrix) {
        System.arraycopy(projectionMatrix, 0, mProjectionMatrix, 0, 16);
    }
    
    /**
     * Draw the curved video surface
     */
    public void draw() {
        // Use program
        GLES20.glUseProgram(mProgram);
        
        // Get handles
        int positionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        int texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        int mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        int textureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");
        
        // Calculate model-view-projection matrix
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        
        // Set MVP matrix
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0);
        
        // Set vertex attributes
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, mVertexBuffer);
        
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBuffer);
        
        // Set texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);
        GLES20.glUniform1i(textureHandle, 0);
        
        // Draw mesh
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mIndexCount, GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        
        if (mTextureID != 0) {
            int[] textures = { mTextureID };
            GLES20.glDeleteTextures(1, textures, 0);
            mTextureID = 0;
        }
        
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
        }
    }
}
