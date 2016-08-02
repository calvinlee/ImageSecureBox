package com.example.imagesecurebox.activity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.os.Handler;

import com.example.imagesecurebox.R;
import com.example.imagesecurebox.service.SecureImageService;
import com.example.imagesecurebox.service.SecureImageService.NotifyCallback;
import com.orhanobut.logger.Logger;

/**
 * Created by calvin on 8/1/16.
 */
public class MainActivity extends AppCompatActivity {
    private static final String EXTRA_HIGH_RESOLUTION_IMAGE = "http://img0.paimaihui.net/images/guardian/2012/06/18/1663-1.jpg";
    private static final String REGULAR_RESOLUTION_IMAGE = "http://tanqisen.github.io/images/Lena.jpg";

    private SecureImageService mSecureImageService;
    private ImageView mSecureImageView;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NotifyCallback.NOTIFY_FETCH_DONE:
                    Bitmap bitmap = (Bitmap) msg.obj;
                    mSecureImageView.setImageBitmap(bitmap);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    private SecureImageService.NotifyCallback mCallback = new SecureImageService.NotifyCallback() {
        @Override
        public void onEvent(int code, String url, Bitmap bitmap) {
            Logger.d("onEvent, code=" + code);
            mHandler.obtainMessage(code, bitmap).sendToTarget();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSecureImageView = (ImageView) findViewById(R.id.secure_image);


        mSecureImageService = SecureImageService.getInstance(getApplicationContext());
        // 可用EventBus来进行组件间通信
        mSecureImageService.registerNotifyCallback(mCallback);
        mSecureImageService.fetchSecureImage(REGULAR_RESOLUTION_IMAGE);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        mSecureImageService.removeNotifyCallback(mCallback);
        super.onDestroy();
    }
}
