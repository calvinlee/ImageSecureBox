package com.example.imagesecurebox.model;

import android.graphics.Bitmap;
import android.util.LruCache;

import com.example.imagesecurebox.util.Md5;

public class ImageLruCache extends LruCache<String, Bitmap> {
    public static final int CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 1024) / 8;

    public ImageLruCache() {
        super(CACHE_SIZE);
    }

    @Override
    protected int sizeOf(String key, Bitmap value) {
        return value.getByteCount() / 1024;
    }

    public void addToCache(String url, Bitmap bitmap) {
        put(Md5.hashKeyFor(url), bitmap);
    }

    public Bitmap loadFromCache(String url) {
        return get(Md5.hashKeyFor(url));
    }
}
