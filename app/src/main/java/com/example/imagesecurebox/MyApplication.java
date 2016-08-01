package com.example.imagesecurebox;

import android.app.Application;

import com.orhanobut.logger.LogLevel;
import com.orhanobut.logger.Logger;

/**
 * Created by calvin on 8/1/16.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Logger.init("ImageSecureBox")
                .methodCount(3)
                .logLevel(LogLevel.FULL)
                .methodOffset(2);
    }

}
