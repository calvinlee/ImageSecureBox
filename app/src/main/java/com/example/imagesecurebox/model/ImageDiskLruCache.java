package com.example.imagesecurebox.model;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.example.imagesecurebox.util.Md5;
import com.jakewharton.disklrucache.DiskLruCache;
import com.orhanobut.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by calvin on 8/1/16.
 */

public class ImageDiskLruCache {
    private DiskLruCache mDiskLruCache;
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 100;
    private static final int DISK_CACHE_INDEX = 0;
    private Context mContext;

    public ImageDiskLruCache(Context context) {
        mContext = context;
        File diskCacheDir = getDiskCacheDir(".secure_image_cache");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }

        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
            } catch (IOException e) {
                Logger.e("DiskLruCache create failed!");
            }
        }

    }

    public byte[] loadFromCache(String url) {
        if (mDiskLruCache == null) {
            return null;
        }

        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = mDiskLruCache.get(Md5.hashKeyFor(url));
            if (snapshot != null) {
                FileInputStream fis = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024 * 8];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                return bos.toByteArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void addToCache(String url, byte[] data) {
        if (mDiskLruCache == null) {
            return;
        }

        String key = Md5.hashKeyFor(url);
        DiskLruCache.Editor editor = null;
        try {
            editor = mDiskLruCache.edit(key);
            if (editor != null) {
                OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
                outputStream.write(data);
                editor.commit();
                mDiskLruCache.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getDiskCacheDir(String dirName) {
        final String cachePath = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ? mContext.getExternalCacheDir().getPath() : mContext.getCacheDir().getPath();
        return new File(cachePath + File.separator + dirName);
    }

    private long getUsableSpace(File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }
}
