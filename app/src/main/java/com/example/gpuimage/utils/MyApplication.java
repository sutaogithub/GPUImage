package com.example.gpuimage.utils;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;

/**
 * Created by zhangsutao on 2016/5/23.
 */
public class MyApplication extends Application{

    public static Context mContext;
    @Override
    public void onCreate() {
        super.onCreate();
        mContext=getApplicationContext();
    }
    public static Resources getResource(){
        return  mContext.getResources();
    }
}
