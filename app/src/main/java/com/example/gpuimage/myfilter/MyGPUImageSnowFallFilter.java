package com.example.gpuimage.myfilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.renderscript.Matrix4f;
import com.example.gpuimage.R;
import com.example.gpuimage.utils.MyApplication;
import com.example.gpuimage.utils.TextureHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;
import jp.co.cyberagent.android.gpuimage.OpenGlUtils;
import jp.co.cyberagent.android.gpuimage.Rotation;

/**
 * 下雪滤镜
 *
 * Created by oujingwen on 15-11-11.
 */
public class MyGPUImageSnowFallFilter extends MyGPUImageFilter {
    public static final String VERTEX_SHADER = "" +
            "attribute vec4 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute float a_pointSize;\n" +
            "uniform mat4 u_mvpMatrix;\n" +
            "uniform float u_Rotation;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoord;\n" +
            "varying float v_Rotation;\n" +

            "void main()\n" +
            "{\n" +
            "    gl_Position = a_position * u_mvpMatrix;\n" +
            "    v_color = a_color;\n" +
            "    gl_PointSize = a_pointSize;\n" +
            "    v_Rotation = u_Rotation;\n" +
            "}\n";
    public static final String FRAGMENT_SHADER = "" +
            "precision mediump float;\n" +
            "varying vec4 v_color;\n" +
            "uniform sampler2D u_texture0;\n" +
            "varying float v_Rotation;\n" +

            "void main()\n" +
            "{\n" +
            "    float mid = 0.5;\n" +
            "    vec2 rotated = vec2(cos(v_Rotation) * (gl_PointCoord.x - mid) + sin(v_Rotation) * (gl_PointCoord.y - mid) + mid,\n" +
            "                        cos(v_Rotation) * (gl_PointCoord.y - mid) - sin(v_Rotation) * (gl_PointCoord.x - mid) + mid);\n" +
            "    gl_FragColor = v_color * texture2D( u_texture0, gl_PointCoord);\n" +
            "}\n";


    private int mSnowfallProgramId;
    protected int g_a_positionHandle;
    protected int g_a_colorHandle;
    protected int g_a_pointSizeHandle;
    protected int g_u_mvpMatrixHandle;
    protected int g_u_rotationHandle;

    protected int g_u_texture0Handle;

    // The Game's view size or area is 2 units wide and 3 units high.
    private static final float ViewMaxX = 2;
    private static final float ViewMaxY = 3;

    private static final int MaxSnowFlakes = 60;
    private static final int TEXTURE_NUM =9 ;

    // Each snow flake will wait 3 seconds - then turn or change direction.
    private static final float TimeTillTurn = 3.0f;
    private static final float TimeTillTurnNormalizedUnit = 1.0f / TimeTillTurn;

    private Matrix4f g_orthographicMatrix;

    private long g_nowTime, g_prevTime;

    private float g_pos[] = new float[MaxSnowFlakes * 2];
    private float g_vel[] = new float[MaxSnowFlakes * 2];
    private float g_col[] = new float[MaxSnowFlakes * 4];
    private float g_size[] = new float[MaxSnowFlakes];
    private float g_timeSinceLastTurn[] = new float[MaxSnowFlakes];
    private int mUsingStarTextureId = OpenGlUtils.NO_TEXTURE;
    private int[] mTexttureStyle =new int[TEXTURE_NUM];
    private int[] texture=new int[MaxSnowFlakes];


    private FloatBuffer mGLPosBuffer;
    private FloatBuffer mGLVelBuffer;
    private FloatBuffer mGLColBuffer;
    private FloatBuffer mGLSize;

    private int mSnowtTextureId = OpenGlUtils.NO_TEXTURE;

    private Random mRandom;

    public MyGPUImageSnowFallFilter() {
        g_orthographicMatrix = new Matrix4f();
        g_orthographicMatrix.loadOrtho(-ViewMaxX, +ViewMaxX, -ViewMaxY, +ViewMaxY, -1.0f, 1.0f);
    }

    @Override
    public void onInit() {
        super.onInit();

        mRandom = new Random();

        mSnowfallProgramId = OpenGlUtils.loadProgram(VERTEX_SHADER, FRAGMENT_SHADER);

        // Vertex shader variables
        g_a_positionHandle = GLES20.glGetAttribLocation(mSnowfallProgramId, "a_position");
        g_a_colorHandle = GLES20.glGetAttribLocation(mSnowfallProgramId, "a_color");
        g_a_pointSizeHandle = GLES20.glGetAttribLocation(mSnowfallProgramId, "a_pointSize");
        g_u_mvpMatrixHandle = GLES20.glGetUniformLocation(mSnowfallProgramId, "u_mvpMatrix");
        g_u_rotationHandle = GLES20.glGetUniformLocation(mSnowfallProgramId, "u_Rotation");

        // Fragment shader variables
        g_u_texture0Handle = GLES20.glGetUniformLocation(mSnowfallProgramId, "u_texture0");

        g_nowTime = SystemClock.uptimeMillis();
        g_prevTime = g_nowTime;

        // This helps as a work around for order-dependency artifacts that can occur when sprites overlap.
//        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        //
        // Snow Flakes...
        //

        for( int i = 0; i < MaxSnowFlakes; ++i )
        {
            //坐标
            g_pos[i * 2 + 0] = nextFloat( -ViewMaxX, ViewMaxX );
            g_pos[i * 2 + 1] = nextFloat(-ViewMaxY, ViewMaxY);
//            Loger.d("Test", "pos[" + i + "]=(" + g_pos[i * 2 + 0] + ", " + g_pos[i * 2 + 1] + ")");

            g_vel[i * 2 + 0] = nextFloat(-0.012f, 0.012f); // Flakes move side to side
            g_vel[i * 2 + 1] = nextFloat(-0.01f, -0.001f); // Flakes fall down

            //颜色
            g_col[i * 4 + 0] = 1.0f;
            g_col[i * 4 + 1] = 1.0f;
            g_col[i * 4 + 2] = 1.0f;
            g_col[i * 4 + 3] = 1.0f; //RandomFloat( 0.6f, 1.0f ); // It seems that Doodle Jump snow does not use alpha.

            g_size[i] = nextFloat(20.0f, 40.0f);

            // It looks strange if the flakes all turn at the same time, so
            // lets vary their turn times with a random negative value.
            g_timeSinceLastTurn[i] = nextFloat(-5.0f, 0.0f);
        }

        mGLPosBuffer = ByteBuffer.allocateDirect(g_pos.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLVelBuffer = ByteBuffer.allocateDirect(g_vel.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLColBuffer = ByteBuffer.allocateDirect(g_col.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLSize = ByteBuffer.allocateDirect(g_size.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        mGLPosBuffer.put(g_pos).position(0);
        mGLVelBuffer.put(g_vel).position(0);
        mGLColBuffer.put(g_col).position(0);
        mGLSize.put(g_size).position(0);

        if (mSnowtTextureId == OpenGlUtils.NO_TEXTURE) {
            Bitmap bitmap= BitmapFactory.decodeResource(MyApplication.getResource(),R.raw.snow3);
//            mSnowtTextureId = OpenGlUtils.loadTexture(CameraApp.getApplication().getResources(),
//                    R.raw.snow3, OpenGlUtils.NO_TEXTURE, false);
            mSnowtTextureId=OpenGlUtils.loadTexture(bitmap,OpenGlUtils.NO_TEXTURE,false);
        }
        if (mUsingStarTextureId == OpenGlUtils.NO_TEXTURE) {
            Bitmap bitmap1= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura1);
            Bitmap bitmap2= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura2);
            Bitmap bitmap3= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura3);
            Bitmap bitmap4= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura4);
            Bitmap bitmap5= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura5);
            Bitmap bitmap6= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura6);
            Bitmap bitmap7= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura7);
            Bitmap bitmap8= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura8);
            Bitmap bitmap9= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.sakura9);

            mTexttureStyle[0] = TextureHelper.loadTexture(bitmap1, OpenGlUtils.NO_TEXTURE, true);
            mTexttureStyle[1] =TextureHelper.loadTexture(bitmap2,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[2] =TextureHelper.loadTexture(bitmap3,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[3] =TextureHelper.loadTexture(bitmap4,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[4] =TextureHelper.loadTexture(bitmap5,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[5] =TextureHelper.loadTexture(bitmap6,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[6] =TextureHelper.loadTexture(bitmap7,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[7] =TextureHelper.loadTexture(bitmap8,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[8] =TextureHelper.loadTexture(bitmap9,OpenGlUtils.NO_TEXTURE,true);
            mUsingStarTextureId= mTexttureStyle[0];
            for( int i = 0; i < MaxSnowFlakes; ++i ){
                texture[i]= mTexttureStyle[mRandom.nextInt(TEXTURE_NUM)];
            }
        }
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
    }

    @Override
    public void onDraw(int textureId, FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
        super.onDraw(textureId, cubeBuffer, textureBuffer);
        update();
        draw();
    }

    private void update() {
        g_nowTime = SystemClock.uptimeMillis();
        float elapsed = (g_nowTime - g_prevTime) / 1000.0f;

        for( int i = 0; i < MaxSnowFlakes; ++i )
        {
            // Keep track of how long it has been since this flake turned
            // or changed direction.
            g_timeSinceLastTurn[i] += elapsed;

            if( g_timeSinceLastTurn[i] >= TimeTillTurn )
            {
                // Change or invert direction!
                g_vel[i * 2 + 0] = -(g_vel[i * 2 + 0]);
                g_timeSinceLastTurn[i] = nextFloat(-5.0f, 0.0f);
            }

            // Speed up the flake up as it leaves the last turn and prepares for next turn.
            float turnVelocityModifier = g_timeSinceLastTurn[i] * TimeTillTurnNormalizedUnit;

            // Apply some velocity to simulate gravity and wind.
            g_pos[i * 2 + 0] += (g_vel[i * 2 + 0] * turnVelocityModifier); // Side to side
            g_pos[i * 2 + 1] += g_vel[i * 2 + 1]; // Gravity

            // But, if the snow flake goes off the bottom or strays too far
            // left or right - respawn it back to the top.
            if( g_pos[i * 2 + 1] < -(ViewMaxY + 0.2f) ||
                    g_pos[i * 2 + 0] < -(ViewMaxX + 0.2f) || g_pos[i * 2 + 0] > (ViewMaxX + 0.2f) )
            {
                g_pos[i * 2 + 0] = nextFloat( -ViewMaxX, ViewMaxX );
                g_pos[i * 2 + 1] = 3.1f;
                texture[i]= mTexttureStyle[mRandom.nextInt(TEXTURE_NUM)];
            }
        }

        g_prevTime = g_nowTime;

        mGLPosBuffer.clear();
        mGLPosBuffer.put(g_pos).position(0);

        mGLVelBuffer.clear();
        mGLVelBuffer.put(g_vel).position(0);

        mGLColBuffer.clear();
        mGLColBuffer.put(g_col).position(0);

        mGLSize.clear();
        mGLSize.put(g_size).position(0);
    }

    private void draw() {
        GLES20.glUseProgram(mSnowfallProgramId);
        GLES20.glUniformMatrix4fv(g_u_mvpMatrixHandle, 1, false, g_orthographicMatrix.getArray(), 0);
        GLES20.glUniform1f(g_u_rotationHandle, (float) Math.toRadians(mergeRotaion()));

        GLES20.glVertexAttribPointer(g_a_positionHandle, 2, GLES20.GL_FLOAT, false, 0, mGLPosBuffer);
        GLES20.glEnableVertexAttribArray(g_a_positionHandle);

        GLES20.glVertexAttribPointer(g_a_colorHandle, 4, GLES20.GL_FLOAT, false, 0, mGLColBuffer);
        GLES20.glEnableVertexAttribArray(g_a_colorHandle);

        GLES20.glVertexAttribPointer(g_a_pointSizeHandle, 1, GLES20.GL_FLOAT, false, 0, mGLSize);
        GLES20.glEnableVertexAttribArray(g_a_pointSizeHandle);

        // Blend particles
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        if (mSnowtTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSnowtTextureId);
            GLES20.glUniform1i(g_u_texture0Handle, 0);
        }

//        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, MaxSnowFlakes);
        if (mUsingStarTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mUsingStarTextureId);
            GLES20.glUniform1i(g_u_texture0Handle, 0);
        }
        for(int i=0;i<MaxSnowFlakes;i++){
            if(mUsingStarTextureId!=texture[i]){
                mUsingStarTextureId=texture[i];
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mUsingStarTextureId);
                GLES20.glUniform1i(g_u_texture0Handle, 0);
            }
            GLES20.glDrawArrays(GLES20.GL_POINTS,i,1);
        }

        GLES20.glDisableVertexAttribArray(g_a_positionHandle);
        GLES20.glDisableVertexAttribArray(g_a_colorHandle);
        GLES20.glDisableVertexAttribArray(g_a_pointSizeHandle);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    @Override
    public void onOutputSizeChanged(int width, int height) {
        super.onOutputSizeChanged(width, height);
    }

    @Override
    protected void onDrawArraysPre() {
        super.onDrawArraysPre();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GLES20.glDeleteTextures(1, new int[]{
                mSnowtTextureId
        }, 0);
        mSnowtTextureId = OpenGlUtils.NO_TEXTURE;
        GLES20.glDeleteProgram(mSnowfallProgramId);
        mSnowfallProgramId = 0;
    }


    private float nextFloat(float min, float max) {
        float distance = max - min;
        float r = mRandom.nextFloat();
        return min + distance * r;
    }

    public void setRotation(final Rotation rotation, final boolean flipHorizontal, final boolean flipVertical) {
        boolean changed = mRotation != rotation || flipHorizontal != mFlipHorizontal || flipVertical != mFlipVertical;
        super.setRotation(rotation, flipHorizontal, flipVertical);
        if (changed) {
            updateTextureCoord();
        }
    }

    @Override
    public void setFlipVertical(boolean flipVertical) {
        boolean changed = flipVertical != mFlipVertical;
        super.setFlipVertical(flipVertical);
        if (changed) {
            updateTextureCoord();
        }
    }

    @Override
    public void setFlipHorizontal(boolean flipHorizontal) {
        boolean changed = flipHorizontal != mFlipHorizontal;
        super.setFlipHorizontal(flipHorizontal);
        if (changed) {
            updateTextureCoord();
        }
    }

    @Override
    public void setRotation(Rotation rotation) {
        boolean changed = mRotation != rotation;
        super.setRotation(rotation);
        if (changed) {
            updateTextureCoord();
        }
    }

    private void updateTextureCoord() {
        g_orthographicMatrix.loadIdentity();
        g_orthographicMatrix.loadOrtho(-ViewMaxX, +ViewMaxX, -ViewMaxY, +ViewMaxY, -1.0f, 1.0f);
        if (mFlipHorizontal) {
            g_orthographicMatrix.translate(0.5f, 0.5f, 1f);
            g_orthographicMatrix.scale(-1, 1, 1);
            g_orthographicMatrix.translate(-0.5f, -0.5f, 1f);
        }
        if (mFlipVertical) {
            g_orthographicMatrix.translate(0.5f, 0.5f, 1f);
            g_orthographicMatrix.scale(1, -1, 1);
            g_orthographicMatrix.translate(-0.5f, -0.5f, 1f);
        }
        g_orthographicMatrix.rotate(mRotation.asInt(), 0.5f, 0.5f, 1f);
    }

    public int mergeRotaion() {
        return mRotation.asInt() + (mFlipVertical ? 180 : 0);
    }
}
