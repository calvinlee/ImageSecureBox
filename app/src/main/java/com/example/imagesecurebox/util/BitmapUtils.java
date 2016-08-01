package com.example.imagesecurebox.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.orhanobut.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * Created by calvin on 8/1/16.
 */
public class BitmapUtils {
    public static int calculateInSampleSize(BitmapFactory.Options options,
                                      int reqWidth, int reqHeight) {
        if (reqWidth == 0 || reqHeight == 0) {
            return 1;
        }

        final int height = options.outHeight;
        final int width = options.outWidth;
        Logger.d("origin, w= " + width + " h=" + height);

        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        Logger.d("sampleSize:" + inSampleSize);
        return inSampleSize;
    }

    public static Bitmap decode(String filePath, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        if (options.inSampleSize <= 1) {
            return BitmapFactory.decodeFile(filePath);
        }
        return BitmapFactory.decodeFile(filePath, options);
    }

    public static void saveAsJpg(OutputStream outputStream, Bitmap bitmap, int rate) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, rate, baos);
        byte[] byteArray = baos.toByteArray();
        try {
            outputStream.write(byteArray);
        } catch (IOException e) {
            Logger.w("save bitmap failed");
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

    }
}
