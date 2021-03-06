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
 * Created by zhangsutao on 2016/6/23.
 */
public abstract class GPUImageDynamicFilter extends MyGPUImageFilter{

    public static final String VERTEX_SHADER = "" +
            "attribute vec4 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute float a_pointSize;\n" +
            "uniform mat4 u_mvpMatrix;\n" +
            "attribute float a_Rotation;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoord;\n" +
            "varying float v_Rotation;\n" +

            "void main()\n" +
            "{\n" +
            "    gl_Position = a_position * u_mvpMatrix;\n" +
            "    v_color = a_color;\n" +
            "    gl_PointSize = a_pointSize;\n" +
            "    v_Rotation = a_Rotation;\n" +
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
            "if(rotated.x>1.0)" +
            "   rotated.x=1.0;" +
            "if(rotated.x<0.0)" +
            "   rotated.x=0.0;"+
            "if(rotated.y<0.0)" +
            "   rotated.y=0.0;"+
            "if(rotated.y>1.0)" +
            "   rotated.y=1.0;"+
            "    gl_FragColor = v_color * texture2D( u_texture0, rotated);\n" +
            "}\n";


    private int ProgramId;
    protected int g_a_positionHandle;
    protected int g_a_colorHandle;
    protected int g_a_pointSizeHandle;
    protected int g_u_mvpMatrixHandle;
    protected int g_a_rotationHandle;
    protected int g_u_texture0Handle;
    private static final float ViewMaxX = 2;
    private static final float ViewMaxY = 3;


    private static final int MaxStarNum = 20;
    private float RANGE_X_STAR=1f,RANGE_Y_STAR=1.5F;
    private float[] move_speed=new float[MaxStarNum];
    private float ALPHA_CHANGE_SPEED=0.003F;
    private float SIZE_CHANGE_SPEED=0.1F;
    private final float RANDOM_FACTOR=-10F;
    private final int TEXTURE_NUM=4;
    private boolean[] isClockWise=new boolean[MaxStarNum];
    private boolean[] isBigger=new boolean[MaxStarNum];
    private int numOfAppear=1;
    private float interval=0;
    private final float TimeTillTurn=0.5f;

    protected abstract float getRANGE_X_STAR();
    protected abstract float getRANGE_Y_STAR();
    protected abstract float getALPHA_CHANGE_SPEED();
    protected abstract float getSIZE_CHANGE_SPEED();
    protected abstract float getSize();

    private Matrix4f g_orthographicMatrix;

    private long g_nowTime, g_prevTime;
    private int[] texture=new int[MaxStarNum];
    private float g_pos[] = new float[MaxStarNum * 2];
    private float g_vel[] = new float[MaxStarNum * 2];
    private float g_col[] = new float[MaxStarNum * 4];
    private float g_size[] = new float[MaxStarNum];
    private float g_ratio[] = new float[MaxStarNum];
    private float g_rotation[]=new float[MaxStarNum];
    //    private float g_timeSinceLastTurn[] = new float[MaxStarNum];
    private FloatBuffer mGLPosBuffer;
    private FloatBuffer mGLVelBuffer;
    private FloatBuffer mGLColBuffer;
    private FloatBuffer mGLSize;
    private FloatBuffer mGLRotation;

    private int mUsingStarTextureId = OpenGlUtils.NO_TEXTURE;
    private int[] mTexttureStyle =new int[TEXTURE_NUM];
    private Random mRandom;



    private float g_timeSinceLastTurn[] = new float[MaxStarNum];


    public GPUImageDynamicFilter() {
        g_orthographicMatrix = new Matrix4f();
        g_orthographicMatrix.loadOrtho(-ViewMaxX, +ViewMaxX, -ViewMaxY, +ViewMaxY, -1.0f, 1.0f);
        RANGE_X_STAR=getRANGE_X_STAR();
        RANGE_Y_STAR=getRANGE_Y_STAR();
        ALPHA_CHANGE_SPEED=getALPHA_CHANGE_SPEED();
        SIZE_CHANGE_SPEED=getSIZE_CHANGE_SPEED();
    }

    @Override
    public void onInit() {
        super.onInit();

        mRandom = new Random();
        ProgramId = OpenGlUtils.loadProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        // Vertex shader variables
        g_a_positionHandle = GLES20.glGetAttribLocation(ProgramId, "a_position");
        g_a_colorHandle = GLES20.glGetAttribLocation(ProgramId, "a_color");
        g_a_pointSizeHandle = GLES20.glGetAttribLocation(ProgramId, "a_pointSize");
        g_a_rotationHandle = GLES20.glGetAttribLocation(ProgramId, "a_Rotation");
        g_u_mvpMatrixHandle = GLES20.glGetUniformLocation(ProgramId, "u_mvpMatrix");
        g_u_texture0Handle = GLES20.glGetUniformLocation(ProgramId, "u_texture0");
        g_nowTime = SystemClock.uptimeMillis();
        g_prevTime = g_nowTime;
        for( int i = 0; i < MaxStarNum; ++i )
        {
            //坐标
            getCoordinate(i);
            g_ratio[i]= (float) Math.sqrt(g_pos[i * 2 + 0]* g_pos[i * 2 + 0]+ g_pos[i * 2 + 1]* g_pos[i * 2 + 1]);
            //颜色0.93333333f;0.78823529f;0f
            g_col[i * 4 + 0] = 1f;
            g_col[i * 4 + 1] = 1f;
            g_col[i * 4 + 2] = 1f;
            g_col[i * 4 + 3] = 1f;
            g_size[i] =getSize();
            if(g_size[i]>110f)
                isBigger[i]=false;
            else
                isBigger[i]=true;
            g_rotation[i]=0;
            g_timeSinceLastTurn[i] = nextFloat(RANDOM_FACTOR, 0.0f);
            isClockWise[i]=mRandom.nextBoolean();
            move_speed[i]=nextFloat((float) (Math.PI/5000),(float) (Math.PI/3000));
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
        mGLRotation = ByteBuffer.allocateDirect(g_rotation.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        mGLPosBuffer.put(g_pos).position(0);
        mGLVelBuffer.put(g_vel).position(0);
        mGLColBuffer.put(g_col).position(0);
        mGLSize.put(g_size).position(0);
        mGLRotation.put(g_rotation).position(0);

        if (mUsingStarTextureId == OpenGlUtils.NO_TEXTURE) {
            Bitmap bitmap1= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.music1);
            Bitmap bitmap2= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.music2);
            Bitmap bitmap3= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.music3);
            Bitmap bitmap4= BitmapFactory.decodeResource(MyApplication.getResource(), R.raw.music4);
            mTexttureStyle[0] =OpenGlUtils.loadTexture(bitmap1,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[1] =OpenGlUtils.loadTexture(bitmap2,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[2] =OpenGlUtils.loadTexture(bitmap3,OpenGlUtils.NO_TEXTURE,true);
            mTexttureStyle[3] =OpenGlUtils.loadTexture(bitmap4,OpenGlUtils.NO_TEXTURE,false);
            mUsingStarTextureId= mTexttureStyle[0];
            for( int i = 0; i < MaxStarNum; ++i ){
                texture[i]= mTexttureStyle[mRandom.nextInt(TEXTURE_NUM)];
            }
        }
    }

    private void getCoordinate(int i) {
        int rand=mRandom.nextInt(2);
        switch (rand){
            case 0:
                g_pos[i * 2 + 0]=mRandom.nextBoolean()?nextFloat(ViewMaxX-RANGE_X_STAR, ViewMaxX):nextFloat(-ViewMaxX, -ViewMaxX+RANGE_X_STAR );
                g_pos[i * 2 + 1]=nextFloat( -ViewMaxY,ViewMaxY);
                break;
            case 1:
                g_pos[i * 2 + 0]=nextFloat(-ViewMaxX+RANGE_X_STAR, ViewMaxX-RANGE_X_STAR);
                g_pos[i * 2 + 1]=mRandom.nextBoolean()?nextFloat( -ViewMaxY,-ViewMaxY+RANGE_Y_STAR):nextFloat( ViewMaxY-RANGE_Y_STAR,ViewMaxY);
                break;
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
        interval+=elapsed;
        for( int i = 0; i < numOfAppear; ++i )
        {
            if( interval >= TimeTillTurn )
            {
                interval=0;
                if(numOfAppear< MaxStarNum)
                    numOfAppear++;
            }
            g_timeSinceLastTurn[i] += elapsed;
            if( g_timeSinceLastTurn[i] >= 0 )
            {
                //alpha
                g_col[i * 4 + 3]-=ALPHA_CHANGE_SPEED;
            }
            //优化后
            double angle=Math.asin(g_pos[i * 2 + 1]/ g_ratio[i]);
            if(g_pos[i * 2 + 0]<0)
                if(angle<0)
                    angle=-Math.PI-angle;
                else
                    angle=Math.PI-angle;
            if(isClockWise[i]){
                angle+=move_speed[i];
                g_rotation[i]+=Math.toRadians(1);
            }
            else{
                angle-=move_speed[i];
                g_rotation[i]-=Math.toRadians(1);
            }

            float new_x= (float) (g_ratio[i]*Math.cos(angle));
            float new_y= (float) (g_ratio[i]*Math.sin(angle));
            g_pos[i * 2 + 0] =new_x;
            g_pos[i * 2 + 1] =new_y;

            //size
            if(isBigger[i])
                g_size[i]+=SIZE_CHANGE_SPEED;
            else
                g_size[i]-=SIZE_CHANGE_SPEED;
            if( g_size[i]>=200f||g_size[i]<=1f||g_col[i * 4 + 3]<=0f )
            {
                getCoordinate(i);
                g_ratio[i]= (float) Math.sqrt(g_pos[i * 2 + 0]* g_pos[i * 2 + 0]+ g_pos[i * 2 + 1]* g_pos[i * 2 + 1]);
                g_size[i] =nextFloat(80f,120f);
                if(g_size[i]>110f)
                    isBigger[i]=false;
                else
                    isBigger[i]=true;
                g_col[i * 4 + 3]=nextFloat(0.7f,1.0f);
                g_timeSinceLastTurn[i] = nextFloat(RANDOM_FACTOR, 0.0f);
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

        mGLRotation.clear();
        mGLRotation.put(g_rotation).position(0);
    }

    private void draw() {
        GLES20.glUseProgram(ProgramId);
        GLES20.glUniformMatrix4fv(g_u_mvpMatrixHandle, 1, false, g_orthographicMatrix.getArray(), 0);

//        GLES20.glUniform1f(g_a_rotationHandle, (float) Math.toRadians(rotation));


        GLES20.glVertexAttribPointer(g_a_positionHandle, 2, GLES20.GL_FLOAT, false, 0, mGLPosBuffer);
        GLES20.glEnableVertexAttribArray(g_a_positionHandle);

        GLES20.glVertexAttribPointer(g_a_colorHandle, 4, GLES20.GL_FLOAT, false, 0, mGLColBuffer);
        GLES20.glEnableVertexAttribArray(g_a_colorHandle);

        GLES20.glVertexAttribPointer(g_a_pointSizeHandle, 1, GLES20.GL_FLOAT, false, 0, mGLSize);
        GLES20.glEnableVertexAttribArray(g_a_pointSizeHandle);

        GLES20.glVertexAttribPointer(g_a_rotationHandle,1,GLES20.GL_FLOAT,false,0,mGLRotation);
        GLES20.glEnableVertexAttribArray(g_a_rotationHandle);

        // Blend particles
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        if (mUsingStarTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mUsingStarTextureId);
            GLES20.glUniform1i(g_u_texture0Handle, 0);
        }
        for(int i=0;i<numOfAppear;i++){
            if(mUsingStarTextureId!=texture[i]){
                mUsingStarTextureId=texture[i];
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mUsingStarTextureId);
                GLES20.glUniform1i(g_u_texture0Handle, 0);
            }
            GLES20.glDrawArrays(GLES20.GL_POINTS,i,1);
        }
        GLES20.glDisableVertexAttribArray(g_a_rotationHandle);
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
        GLES20.glDeleteProgram(ProgramId);
        ProgramId = 0;
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
