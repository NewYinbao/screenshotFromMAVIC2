
package com.example.djidemo.detection;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;

import com.example.djidemo.media.DJIVideoStreamDecoder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Utils {

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth Width of source frame.
     * @param srcHeight Height of source frame.
     * @param dstWidth Width of destination frame.
     * @param dstHeight Height of destination frame.
     * @param applyRotation Amount of rotation to apply from one frame to another.
     *  Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     * cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    public static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }


    public static Bitmap processBitmap(Bitmap source, int[] size) {

        int image_height = source.getHeight();
        int image_width = source.getWidth();
        int out_h = size[1];
        int out_w = size[0];
        //
        Bitmap croppedBitmap = Bitmap.createBitmap(out_w, out_h, Bitmap.Config.ARGB_8888);

        Matrix frameToCropTransformations = getTransformationMatrix(image_width, image_height, out_w, out_h, 0, false);
        Matrix cropToFrameTransformations = new Matrix();
        frameToCropTransformations.invert(cropToFrameTransformations);

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(source, frameToCropTransformations, null);
        return croppedBitmap;
    }

    public static float[][] inMatrix = new float[][]{{(float) 0.001459, 0, (float) -0.72081},{ 0, (float) 0.001469, (float) -0.52317},{ 0, 0, 1}};
    /*
    输入：俯仰角pitch，相对高度height，图像坐标x,y
    输出：body坐标系下的xyz
     */
    public static float[] Image2body(float pitch, float height, float x, float y){
        float[] image_xy1 = new float[]{x,y,1};
        float[] camera_xy1 = new float[]{0,0,0};
        float[] body_xyz = new float[]{0,0,0};
        // image x,y 乘内参矩阵的逆
        for (int i=0;i<3;i++){
            camera_xy1[i] = inMatrix[i][0] * image_xy1[0] + inMatrix[i][1] * image_xy1[1] + inMatrix[i][2] * image_xy1[2];
        }

        // camera坐标系[x,y,z] 对应 body坐标系[y,z,x]
        float tx = camera_xy1[0];
        float ty = camera_xy1[1];
        float tz = camera_xy1[2];
        camera_xy1 = new float[]{tz,tx,ty};

        // camera_xy1 左乘 pitch 旋转阵 绕y旋转-pitch
        // pitch = Math.toRadians(pitch)；
        float[][] RotatePitch = new float[][]{{(float)Math.cos(Math.toRadians(-pitch)), 0,  (float)-Math.sin(Math.toRadians(-pitch))},
                {0,1,0},{(float)Math.sin(Math.toRadians(-pitch)),0 , (float)Math.cos(Math.toRadians(-pitch)) }};
        for (int i=0;i<3;i++){
            body_xyz[i] = RotatePitch[i][0] * camera_xy1[0] + RotatePitch[i][1] * camera_xy1[1] + RotatePitch[i][2] * camera_xy1[2];
        }
        //根据相对高度求body坐标
        float scale = height/body_xyz[2];
        for(int i=0;i<3;i++){
            body_xyz[i] = body_xyz[i] * scale;
        }
        return body_xyz;
    }


    public static void saveYuvDataToJPEG(byte[] yuvFrame, int width, int height){
        if (yuvFrame.length < width * height) {
            //DJILog.d(TAG, "yuvFrame size is too small " + yuvFrame.length);
            return;
        }

        byte[] y = new byte[width * height];
        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];
        byte[] nu = new byte[width * height / 4]; //
        byte[] nv = new byte[width * height / 4];

        System.arraycopy(yuvFrame, 0, y, 0, y.length);
        for (int i = 0; i < u.length; i++) {
            v[i] = yuvFrame[y.length + 2 * i];
            u[i] = yuvFrame[y.length + 2 * i + 1];
        }
        int uvWidth = width / 2;
        int uvHeight = height / 2;
        for (int j = 0; j < uvWidth / 2; j++) {
            for (int i = 0; i < uvHeight / 2; i++) {
                byte uSample1 = u[i * uvWidth + j];
                byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
                byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
                byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
                nu[2 * (i * uvWidth + j)] = uSample1;
                nu[2 * (i * uvWidth + j) + 1] = uSample1;
                nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
                nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
                nv[2 * (i * uvWidth + j)] = vSample1;
                nv[2 * (i * uvWidth + j) + 1] = vSample1;
                nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
                nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
            }
        }
        //nv21test
        byte[] bytes = new byte[yuvFrame.length];
        System.arraycopy(y, 0, bytes, 0, y.length);
        for (int i = 0; i < u.length; i++) {
            bytes[y.length + (i * 2)] = nv[i];
            bytes[y.length + (i * 2) + 1] = nu[i];
        }
        Log.d("yuvdecode:",
                "onYuvDataReceived: frame index: "
                        + DJIVideoStreamDecoder.getInstance().frameIndex
                        + ",array length: "
                        + bytes.length);
        screenShot(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
    }

    /**
     * Save the buffered data into a JPG image file
     */
    public static void screenShot(byte[] buf, String shotDir, int width, int height) {
        File dir = new File(shotDir);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }

        YuvImage yuvImage = new YuvImage(buf,
                ImageFormat.NV21,
                width,
                height,
                null);
        OutputStream outputFile;
        final String path = dir + "/ScreenShot_" + System.currentTimeMillis() + ".jpg";
        try {
            outputFile = new FileOutputStream(new File(path));
        } catch (FileNotFoundException e) {
            Log.e("yuvdecode:", "test screenShot: new bitmap output file error: " + e);
            return;
        }
        if (outputFile != null) {
            yuvImage.compressToJpeg(new Rect(0,
                    0,
                    width,
                    height), 100, outputFile);
        }
        try {
            outputFile.close();
        } catch (IOException e) {
            Log.e("yuvdecode:", "test screenShot: compress yuv image error: " + e);
            e.printStackTrace();
        }
    }





}
