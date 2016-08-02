package com.example.imagesecurebox.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.webkit.URLUtil;

import com.example.imagesecurebox.model.ImageDiskLruCache;
import com.example.imagesecurebox.model.ImageLruCache;
import com.example.imagesecurebox.security.Crypto;
import com.example.imagesecurebox.util.BitmapUtils;
import com.orhanobut.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.example.imagesecurebox.service.SecureImageService.NotifyCallback.NOTIFY_FETCH_DONE;

/**
 * Created by calvin on 8/1/16.
 */

public class SecureImageService {
    private static SecureImageService sInstance;

    private Context mContext;
    private ImageLruCache mMemoryCache;
    private ImageDiskLruCache mDiskLruCache;
    private ThreadPoolExecutor mThreadPoolExecutor = null;
    private List<NotifyCallback> mNotifyCallbacks = Collections.synchronizedList(new ArrayList<NotifyCallback>());

    private SecureImageService(Context context) {
        mContext = context;
        initCache();
        initThreadPool();
    }

    public static SecureImageService getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SecureImageService.class) {
                if (sInstance == null) {
                    sInstance = new SecureImageService(context);
                }
            }
        }
        return sInstance;
    }

    private void initCache() {
        mMemoryCache = new ImageLruCache();
        mDiskLruCache = new ImageDiskLruCache(mContext);
    }

    private void initThreadPool() {
        int number_of_cores = Runtime.getRuntime().availableProcessors();
        mThreadPoolExecutor = new ThreadPoolExecutor(
                number_of_cores,
                number_of_cores * 4,
                1,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<Runnable>(number_of_cores, true));
    }

    private Bitmap getBitmapFromMemCache(String url) {
        return mMemoryCache.loadFromCache(url);
    }

    public void fetchSecureImage(String url) {
        if (!URLUtil.isValidUrl(url)) {
            Logger.e("URL " + url + " is invalid!");
            return;
        }

        checkNetworkConnection();

        SecureImageWorker task = new SecureImageWorker(mContext, url);
        if (mThreadPoolExecutor.getQueue().contains(task)) {
            Logger.d("Task already in the pool, defer submit it..");
        } else {
            Logger.d("Kickoff to show secure image from " + url);
            mThreadPoolExecutor.submit(task);
        }
    }

    private void checkNetworkConnection() {
        // TODO
    }

    private class SecureImageWorker implements Runnable {
        private String mUrl;
        private Context mContext;

        public SecureImageWorker(Context context, String url) {
            mContext = context;
            mUrl = url;
        }

        @Override
        public void run() {
            // 1. Is there already a decrepted bitmap?
            Bitmap bitmap = getBitmapFromMemCache(mUrl);
            if (bitmap == null) {
                bitmap = decryptFromDisk();
            }

            // 2. cache missed, now fetch from network
            if (bitmap == null) {
                bitmap = downloadUrlAsBitmap(mUrl);
                if (bitmap != null) {
                    // 3. convert to webp
                    byte[] webpData = convertToWebp(bitmap);

                    if (webpData != null) {
                        // 4. encrypt the image data and save it to disk
                        encryptImage(webpData);

                        // 5. decrypt
                        bitmap.recycle();
                        bitmap = decryptFromDisk();
                    }
                } else {
                    Logger.e("Failed to download image from " + mUrl);
                }
            }

            if (bitmap != null) {
                for (NotifyCallback callback : mNotifyCallbacks) {
                    callback.onEvent(NOTIFY_FETCH_DONE, mUrl, bitmap);
                }
            } else {
                Logger.e("Failed to fetch bitmap anyway...");
            }

            Logger.d("ThreadPool status:" + mThreadPoolExecutor);
        }

        // 4.0以上系统原生支持webp格式,4.0以下系统需要使用libweb库来添加webp媒体类型支持
        private byte[] convertToWebp(Bitmap bitmap) {
            Logger.d("Converting to webp format...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean success = bitmap.compress(Bitmap.CompressFormat.WEBP, 100, baos);
            if (success) {
                return baos.toByteArray();
            } else {
                Logger.e("Convert image to webp format failed!");
            }
            return null;
        }

        private boolean encryptImage(byte[] data) {
            Logger.d("Encrypting bitmap...");
            try {
                String cipherText = Crypto.encrypt(Crypto.toBase64(data), peekCryptoPassword());
                mDiskLruCache.addToCache(mUrl, cipherText.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        // TODO: handle OOM
        // 使用流加密
        private Bitmap decryptFromDisk() {
            byte[] encryptBytes = mDiskLruCache.loadFromCache(mUrl);
            if (encryptBytes == null) {
                return null;
            }

            try {
                String decrypttext = Crypto.decryptPbkdf2(new String(encryptBytes, "UTF-8"), peekCryptoPassword());
                if (decrypttext != null) {
                    byte[] decryptBytes = Crypto.fromBase64(decrypttext);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decryptBytes, 0, decryptBytes.length);
                    if (bitmap != null) {
                        mMemoryCache.addToCache(mUrl, bitmap);
                        Logger.d("Decrepted bitmap from disk");
                        return bitmap;
                    }

                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return null;
        }

        // 图片加载可使用Fresco库
        private Bitmap downloadUrlAsBitmap(String imageUrl) {
            Logger.d("Fetching bitmap from network...");
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            int reqWidth = dm.widthPixels;
            int reqHeight = dm.heightPixels;
            HttpURLConnection urlConnection = null;
            InputStream in = null;

            try {
                urlConnection = openConnection(imageUrl);
                in = urlConnection.getInputStream();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, options);
                options.inSampleSize = BitmapUtils.calculateInSampleSize(options, reqWidth, reqHeight);
                urlConnection.disconnect();
                in.close();

                urlConnection = openConnection(imageUrl);
                in = urlConnection.getInputStream();
                options.inJustDecodeBounds = false;

                // TODO improve this
                options.inPurgeable = true;
                /*
                try {
                    BitmapFactory.Options.class.getField("inNativeAlloc").setBoolean(options, true);
                } catch (IllegalAccessException e) {
                    // ignore
                }*/

                return BitmapFactory.decodeStream(in, null, options);
            } catch (MalformedURLException e) {
                Logger.e(e, "Invalid URL");
            } catch (IOException e) {
                Logger.e(e, "Failed to init connection");
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        private HttpURLConnection openConnection(String imageUrl) throws IOException {
            final URL url = new URL(imageUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoInput(true);
            urlConnection.setConnectTimeout(30 * 1000);
            urlConnection.setReadTimeout(30 * 1000);
            urlConnection.connect();
            return urlConnection;
        }

        // TODO: 从本地一个二进制文件中获取加密密钥,可选的密钥管理方式还可以包括:
        // 1. 将密钥获取规则打进so库,增加反编译难度
        // 2. 使用隐写术获取密钥
        // 3. 将密钥存在远程服务器上
        private String peekCryptoPassword() {
            try {
                byte[] buffer = new byte[128];
                InputStream is = mContext.getAssets().open("launcher");
                is.read(buffer);
                is.close();
                return Crypto.toBase64(buffer);
            } catch (IOException e) {
                throw new RuntimeException("Failed to get passphase!");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SecureImageWorker that = (SecureImageWorker) o;

            return mUrl != null ? mUrl.equals(that.mUrl) : that.mUrl == null;

        }

        @Override
        public int hashCode() {
            return mUrl != null ? mUrl.hashCode() : 0;
        }
    }


    public interface NotifyCallback {
        int NOTIFY_FETCH_DONE = 1;

        void onEvent(int code, String url, Bitmap bitmap);
    }

    public void registerNotifyCallback(NotifyCallback callback) {
        mNotifyCallbacks.add(callback);
    }

    public void removeNotifyCallback(NotifyCallback callback) {
        mNotifyCallbacks.remove(callback);
    }
}
