package com.example.gpuimage;

import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.File;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.GPUImageSepiaFilter;

public class MainActivity extends AppCompatActivity {

    private GPUImage mGPUImage;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_activity_main);
        File file=new File("/storage/emulated/0/asa.jpg");
        if(file.exists()){
            final Uri imageUri =Uri.fromFile(file);
            mGPUImage = new GPUImage(this);
            mGPUImage.setGLSurfaceView((GLSurfaceView) findViewById(R.id.my_surfaceView));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mGPUImage.setImage(imageUri); // this loads image on the current thread, should be run in a thread

                }
            }).start();
//            mGPUImage.setFilter(new GPUImageSepiaFilter());
        }


//        // Later when image should be saved saved:
//        mGPUImage.saveToPictures("GPUImage", "ImageWithFilter.jpg", null);

    }
}
