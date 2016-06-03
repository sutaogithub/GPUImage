package com.example.gpuimage.myfilter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.renderscript.Matrix4f;

import com.example.gpuimage.R;
import com.example.gpuimage.utils.MyApplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import jp.co.cyberagent.android.gpuimage.OpenGlUtils;
import jp.co.cyberagent.android.gpuimage.Rotation;

/**
 * Created by zhangsutao on 2016/5/27.
 */
public class GPURectangleFilter extends MyGPUImageFilter{


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
            "    gl_FragColor = v_color * texture2D( u_texture0, rotated);\n" +
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

    //每个点有2个发射点
    private static final int NUMOFSEND=4;
    private static final int HEARTNUM=27;
    private static final int MaxHeartNum = (NUMOFSEND+1)*HEARTNUM;
    private final float MOVE_SPEED=0.05f;
    private final int TEXTURE_NUM=1;
    private float[] angle=new float[MaxHeartNum];
    private boolean[] isSend=new boolean[MaxHeartNum];
    private float[] sendHeartX=new float[MaxHeartNum];
    private float[] sendHeartY=new float[MaxHeartNum];
    private Matrix4f g_orthographicMatrix;
    private final float APPEAR_INTERVAL=0.20f;
    private float interval=0;
    private int numOfAppear=0;
    private final float SPEED=1.2f;
    private final float RECTANGLE_WIDTH=3;
    private final float RECTANGEL_HEIGHT=5;



    private long g_nowTime, g_prevTime;
    private int[] texture=new int[MaxHeartNum];
    private float g_pos[] = new float[MaxHeartNum * 2];
    private float g_col[] = new float[MaxHeartNum * 4];
    private float g_size[] = new float[MaxHeartNum];
    private FloatBuffer mGLPosBuffer;
    private FloatBuffer mGLColBuffer;
    private FloatBuffer mGLSize;

    private int mUsingStarTextureId = OpenGlUtils.NO_TEXTURE;
    private int[] mTexttureStyle =new int[2];
    private Random mRandom;





    public GPURectangleFilter() {
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
        for( int i = 0; i < MaxHeartNum; ++i )
        {
            //坐标
            getCoordinate(i);

            //颜色
            g_col[i * 4 + 0] = 1.0f;
            g_col[i * 4 + 1] = 0f;
            g_col[i * 4 + 2] = 0f;
            g_col[i * 4 + 3] = 1f; //RandomFloat( 0.6f, 1.0f ); // It seems that Doodle Jump snow does not use alpha.
            g_size[i] =70f;

        }

        mGLPosBuffer = ByteBuffer.allocateDirect(g_pos.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        mGLColBuffer = ByteBuffer.allocateDirect(g_col.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLSize = ByteBuffer.allocateDirect(g_size.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        mGLPosBuffer.put(g_pos).position(0);
        mGLColBuffer.put(g_col).position(0);
        mGLSize.put(g_size).position(0);

        if (mUsingStarTextureId == OpenGlUtils.NO_TEXTURE) {
            Bitmap bitmap1= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.heart);
            mTexttureStyle[0] =OpenGlUtils.loadTexture(bitmap1,OpenGlUtils.NO_TEXTURE,false);
            mUsingStarTextureId= mTexttureStyle[0];
        }
    }

    private void getCoordinate(int i) {

        g_pos[i*2]=-RECTANGLE_WIDTH/2;
        g_pos[i*2+1]=RECTANGEL_HEIGHT/2;
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
        interval+=elapsed;
        if(interval>=APPEAR_INTERVAL&&numOfAppear< MaxHeartNum)
        {
            numOfAppear+=(NUMOFSEND+1);
            interval=0;
        }
        float x_vel = 0,y_vel=0;
        for( int i = 0; i < numOfAppear; i+=(NUMOFSEND+1) )
        {
            descideIfSend(i);
            if(g_pos[i*2+1]>=RECTANGEL_HEIGHT/2&&g_pos[i*2]<RECTANGLE_WIDTH/2){
                x_vel=MOVE_SPEED;
                y_vel=0;
            }else
                if(g_pos[i*2]>=0&&g_pos[i*2+1]>-RECTANGEL_HEIGHT/2){
                    x_vel=0;
                    y_vel=-MOVE_SPEED;
                }else
                    if(g_pos[i*2]>-RECTANGLE_WIDTH/2&&g_pos[i*2+1]<=0){
                        x_vel=-MOVE_SPEED;
                        y_vel=0;
                    }else
                        if(g_pos[i*2]<=0&&g_pos[i*2+1]<RECTANGEL_HEIGHT/2){
                            x_vel=0;
                            y_vel=MOVE_SPEED;
                        }


            g_pos[i*2]+=x_vel;
            g_pos[i*2+1]+=y_vel;
            if(g_size[i]<=1f||g_col[i * 4 + 3]<=0f )
            {
                angle[i]=0;
                getCoordinate(i);
                g_size[i] =50f;
                g_col[i * 4 + 3]=1f;
                texture[i]= mTexttureStyle[mRandom.nextInt(TEXTURE_NUM)];
            }
            updateSendPos(i);
        }

        g_prevTime = g_nowTime;

        mGLPosBuffer.clear();
        mGLPosBuffer.put(g_pos).position(0);

        mGLColBuffer.clear();
        mGLColBuffer.put(g_col).position(0);

        mGLSize.clear();
        mGLSize.put(g_size).position(0);
    }

    private void updateSendPos(int i) {
        for(int j=1;j<=NUMOFSEND;j++){
            if(isSend[i+j]){
                g_pos[i*2+j*2]+=sendHeartX[i+1];
                g_pos[i*2+1+j*2]+=sendHeartY[i+1];
                g_size[i+j]-=nextFloat(0.2f,1f);
            }else{
                g_pos[i*2+j*2]=g_pos[i*2];
                g_pos[i*2+1+j*2]=g_pos[i*2+1];
            }
            if( g_size[i+j]<1f){
                g_pos[i*2+j*2]=g_pos[i*2];
                g_pos[i*2+1+j*2]=g_pos[i*2+1];
                g_size[i+j]=70f;
                isSend[i+j]=false;
            }
        }
    }

    private void descideIfSend(int i) {
        for(int j=1;j<=NUMOFSEND;j++){
            if(!(isSend[i+j])) {
                isSend[i + j] =mRandom.nextBoolean();
                if (isSend[i + j]) {
                    sendHeartX[i + j] = nextFloat(-0.01f, 0.01f);
                    sendHeartY[i + j] = nextFloat(-0.01f, 0.01f);
                }
            }
        }
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
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        if (mUsingStarTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mUsingStarTextureId);
            GLES20.glUniform1i(g_u_texture0Handle, 0);
        }
        for(int i=0;i<MaxHeartNum;i+=(NUMOFSEND+1)){
            GLES20.glDrawArrays(GLES20.GL_POINTS,i,1);
            for(int j=1;j<=NUMOFSEND;j++){
                if(isSend[i+j])
                    GLES20.glDrawArrays(GLES20.GL_POINTS,i+j,1);
            }
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
                mUsingStarTextureId
        }, 0);
        mUsingStarTextureId = OpenGlUtils.NO_TEXTURE;
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
