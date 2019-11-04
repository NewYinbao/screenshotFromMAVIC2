package com.example.djidemo.detection;

import android.content.res.AssetManager;

import java.io.IOException;

public class Yolo3detector extends Detector {

    protected float mObjThresh = 0.1f;

    public Yolo3detector(AssetManager assetManager) throws IOException {
        // yolo_body_mobilenetv2_5_9final
        super(assetManager, "detect8.23gai1.tflite", "my.txt", 224,320);
//        super(assetManager, "yolo_body_mobilenetv2_5" +
//                "_9final.tflite", "my.txt", 224,320);
        mAnchors = new int[]{
//                10,13,  16,30,  33,23,  30,61,  62,45,  59,119,  116,90,  156,198,  373,326
//                12,15, 21,19, 23,28, 33,35, 41,61, 44,45, 59,53, 75,92, 152,179
//                9,8, 12,12, 17,15, 22,24, 36,35, 47,54
//                29,19,  33,27, 48,39, 58,51, 72,56, 93,80        //288,384
//                23,13, 30,19, 37,22, 44,26,56,41, 90,58, 115,88, 176,122, 253,176 //anchorsf1
                10,6, 16,10, 26,16, 41,23, 57,38, 117,72 //320_224

        };

//        mMasks = new int[][]{{3,4,5},{0,1,2}};
//        mOutsize = new int[][]{{12,9}, {24,18}};

        mMasks = new int[][]{{3,4,5},{0,1,2}};
        mOutsize = new int[][]{{10,7}, {20,14}};
        mObjThresh = 0.4f;

//        mMasks = new int[][]{{6,7,8},{3,4,5},{0,1,2}};//3
//        mOutsize = new int[][]{{12,9}, {24,18},{48,36}};
//        mObjThresh = 0.4f;
    }

    @Override
    protected float getObjThresh() {
        return mObjThresh;
    }

}
