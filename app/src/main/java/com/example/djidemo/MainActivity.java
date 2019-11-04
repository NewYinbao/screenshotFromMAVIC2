package com.example.djidemo;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.djidemo.detection.Detector;
import com.example.djidemo.detection.NV21ToBitmap;
import com.example.djidemo.detection.Utils;
import com.example.djidemo.detection.Yolo3detector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import dji.common.product.Model;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.thirdparty.afinal.core.AsyncTask;

import static com.example.djidemo.detection.Utils.processBitmap;
import static com.example.djidemo.myLog.saveLogs;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, View.OnClickListener,DJICodecManager.YuvDataCallback {

    private static final String TAG = MainActivity.class.getName();
    //视频编码及回调
    protected DJICodecManager mcodecManager = null;
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;
    //视频显示控件
    protected TextureView mVideoSurface = null;

    protected String temp;
    protected long onReceivedtime = 0;

    //检测相关变量
    private Bitmap bitmap_target;// 检测用的图片
    private boolean detect_flag = false;//检测标志位
    private boolean screenShot_flag = false;
    private Detector detector;//检测器
    private Bitmap resized_image;//预处理图片
    private ArrayList<Detector.Recognition> results = null;//检测结果
    private float scalew, scaleh;//图片显示和处理缩放比例
    private ImageView imageView;//检测结果显示

    private TextView topTextvivewer;//检测结果显示
    private TextView titleTextviewer;//标题
    private TextView detectresultTextviewer;
    private Button bn_startDetect, bn_screenShot, bn_YuvMod;

    private RectF target_location;//目标的位置
    private RectF last_target_location = null ;//上次目标的位置
    private int count = 0;//控制帧数

    private int lostbox = 0;//连续丢失目标的次数
    private int lostbox_threshold = 10;//丢失次数上限
    private int switch_target_threshold = 10;//切换目标时的变化上限
    //定位相关变量
    private int location_flag = 0;//定位标志位
    private TextView relativeposition;//相对位置显示
    private float position_x;//X方向相对位置
    private float position_y;//Y方向相对位置

    /**
     * 通过Handler机制定时刷新
     */
    private Handler mHandler = new Handler(){

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //布局设置为全屏
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        initUI();

        //视频回调
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {
            @Override
            public void onReceive(byte[] bytes, int i) {
                if(mcodecManager!=null){
                    mcodecManager.sendDataToDecoder(bytes,i);
                }
            }
        };

        //如果存储路径不存在
        if (!new File(Environment.getExternalStorageDirectory() + "/DJI_screenShot").exists()) {
            new File(Environment.getExternalStorageDirectory() + "/DJI_screenShot").mkdirs();//创建父路径
        }

        new DetectThread().start();
    }

    @Override
    public void onResume(){
        super.onResume();
        //设置为横屏
        if(getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        //对视频进行初始化，显示到控件上
        initPreviewer();
    }

    @Override
    public void onPause() {
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view) {
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        super.onDestroy();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mcodecManager == null) {
            mcodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mcodecManager != null) {
            mcodecManager.cleanSurface();
            mcodecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }


    /**
     * 初始化UI
     */
    private void initUI(){
        mVideoSurface = (TextureView) findViewById(R.id.video_prevewer_surface);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        //解码模式
        bn_YuvMod = (Button) findViewById(R.id.activity_main_buttonYuv);
        bn_YuvMod.setOnClickListener(this);
        //检测使能按钮初始化
        bn_startDetect = (Button) findViewById(R.id.activity_main_startDetect);
        bn_startDetect.setOnClickListener(this);
        //拍照使能按钮
        bn_screenShot = (Button) findViewById(R.id.activity_main_screen_shot);
        bn_screenShot.setOnClickListener(this);
        //相关信息显示框
        topTextvivewer = (TextView)findViewById(R.id.activity_main_textTop);
        titleTextviewer = (TextView)findViewById(R.id.activity_main_title);
        detectresultTextviewer = (TextView)findViewById(R.id.activity_main_detectRsult);
        //识别结果显示
        imageView = (ImageView) findViewById(R.id.activity_main_showimage);
    }


    /**
     * 通过实现这两个方法，把视频显示、重置到控件mVideoSurface上
     */
    private void initPreviewer(){
        BaseProduct product = VideoDecodingApplication.getProductInstance();

        if(mVideoSurface!=null){
            mVideoSurface.setSurfaceTextureListener(this);
        }
        if(!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)){
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
        }
    }

    private void uninitPreviewer(){
        Camera camera = VideoDecodingApplication.getCameraInstance();
        if(camera!=null){
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(null);
        }
    }

    /**
     * 实现onClick事件
     */
    @Override
    public void onClick(View v){
        try {
            switch (v.getId()) {

                case R.id.activity_main_buttonYuv:
                    handleYUVClick();
                    break;

                case R.id.activity_main_screen_shot:
                    screenShot_flag = !screenShot_flag;
                    if (screenShot_flag){
                        bn_screenShot.setText("screen Shotting..");
                    }
                    else{
                        bn_screenShot.setText("start Screen Shot");
                    }
                    break;

                    case R.id.activity_main_startDetect:
                        detect_flag = !detect_flag;
                        if (detect_flag){
                            bn_startDetect.setText("Detecting..");
                        }
                        else{
                            bn_startDetect.setText("start Detect");

                    break;
                }
                default:
                    break;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /**
     *
     */
    private void handleYUVClick() {
        if (bn_YuvMod.isSelected()) {
            bn_YuvMod.setText("Live Stream");
            bn_YuvMod.setSelected(false);
            mcodecManager.enabledYuvData(false);
            mcodecManager.setYuvDataCallback(null);

            imageView.setVisibility(View.INVISIBLE);

        } else {
            mcodecManager.enabledYuvData(true);//开启yuv回调函数
            mcodecManager.setYuvDataCallback(this);
            bn_YuvMod.setText("YUV image");
            bn_YuvMod.setSelected(true);
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
        //将yuv图片转换成bitmap，然后放到image_target中
        DJILog.d(TAG, "onYuvDataReceived " + dataSize);
        if (yuvFrame != null) {

            final byte[] bytes = new byte[dataSize];
            yuvFrame.get(bytes);
            long startTime = SystemClock.uptimeMillis();
            final Bitmap bitmap = new NV21ToBitmap(this).nv21ToBitmap2(bytes, width, height);

//            final Bitmap bitmap1 = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);;
//            saveLogs("nyb: ", "bytes size is "+bytes.length);
//            saveLogs("nyb: ", "bitmap1 is null?  "+(bitmap1==null));
//            saveLogs("nyb: ", "bitmap is null?  "+(bitmap==null));
            //每两帧跟新一次检测图片
            if (detect_flag)
                bitmap_target = bitmap;
//            saveLogs("nyb: in djivideostream", "MediaFormat.KEY_COLOR_FORMAT is " + MediaFormat.KEY_COLOR_FORMAT);

            //一秒钟保存2张图片
            if(screenShot_flag==true && count% 10 == 0) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        final String path = Environment.getExternalStorageDirectory()
                                              + "/DJI_screenShot" + "/" + System.currentTimeMillis() + ".jpg";//保存文件名

                        try {
                            saveYuvDataToJPEG2(bitmap, width, height,path);
//                            saveLogs("nyb: ", "has saved bitmap ");
//                            Utils.saveYuvDataToJPEG(bytes, width, height);
//                            saverawBitmap(bytes);
                            displayPath(path);
                            if(!detect_flag)
                            {
                                imageView.setImageBitmap(bitmap);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            //显示解码图片
            if (!detect_flag && !screenShot_flag)
            {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }

            if(count >=30)
            {
                count = 0;
            }
            count++;

        }
    }
    /**
     保存yuv数据
     */
    private void saveYuvDataToJPEG2(Bitmap bitmap, int width, int height,String path) throws IOException {
        File file = new File(path);
        FileOutputStream out = null;
        out = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        out.flush();
        out.close();
    }

    /*
     save raw bitmap
     */
    private void saverawBitmap(byte[] bytes){
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        File file = new File(Environment.getExternalStorageDirectory() + "/DJI_screenShot" + "/raw" + System.currentTimeMillis() + ".jpg");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ;
    }
    /**
     * 显示存储路径
     * @param path
     */
    private void displayPath(String path) {
        //path = path + "\n";
        topTextvivewer.setText(path);
    }

    /**
     * 检测线程
     * @param
     */
    private class DetectThread extends Thread {
        public DetectThread() {
        }
        @Override
        public void run() {
            try {
                detector = new Yolo3detector(getAssets());
            } catch (final Exception e) {
                throw new RuntimeException("Error initializing TensorFlow!", e);
            }
            //头盔的画布
            final Paint paint_helmet = new Paint();
            paint_helmet.setColor(Color.RED);
            paint_helmet.setStyle(Paint.Style.STROKE);
            paint_helmet.setStrokeWidth(3.0f);
            paint_helmet.setTextAlign(Paint.Align.LEFT);
            paint_helmet.setTextSize(80);
            //箱子的画布
            final Paint paint_box = new Paint();
            paint_box.setColor(Color.BLACK);
            paint_box.setStyle(Paint.Style.STROKE);
            paint_box.setStrokeWidth(3.0f);
            paint_box.setTextAlign(Paint.Align.LEFT);
            paint_box.setTextSize(80);
            //目标箱子的画布
            final Paint target_box = new Paint();
            target_box.setColor(Color.WHITE);
            target_box.setStyle(Paint.Style.STROKE);
            target_box.setStrokeWidth(3.0f);
            target_box.setTextAlign(Paint.Align.LEFT);
            target_box.setTextSize(80);
            //目标箱子的画布
            final Paint paint_qrcode = new Paint();
            paint_qrcode.setColor(Color.GREEN);
            paint_qrcode.setStyle(Paint.Style.STROKE);
            paint_qrcode.setStrokeWidth(3.0f);
            paint_qrcode.setTextAlign(Paint.Align.LEFT);
            paint_qrcode.setTextSize(80);

            int center_threshold = 240;

            while (true) {
                if (bitmap_target != null && detect_flag) {

                    temp = "re: " + String.valueOf(SystemClock.uptimeMillis() - onReceivedtime) + "ms,";
                    onReceivedtime = SystemClock.uptimeMillis();
                    long startTime = SystemClock.uptimeMillis();

                    final Bitmap tempimage = bitmap_target;

                    scalew = (float) (bitmap_target.getWidth()) / detector.getInputSize()[0];
                    scaleh = (float) (bitmap_target.getHeight()) / detector.getInputSize()[1];
                    bitmap_target = null;

                    resized_image = processBitmap(tempimage, detector.getInputSize());
                    results = detector.RecognizeImage(resized_image);

                    long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    temp += "detect: " + String.valueOf(lastProcessingTimeMs) + "ms\n";

                    resized_image = Bitmap.createBitmap(tempimage);
                    center_threshold = (int)(resized_image.getWidth())/4;
                    final Canvas canvas = new Canvas(resized_image);

                    int num_box = 0;
                    float distance = 0;
                    float distance_target = 0;
                    for (int i=0; i<results.size(); i++) {
                        final Detector.Recognition result = results.get(i);

                        final RectF location = result.getLocation();
                        if (location != null && result.getConfidence() >= 0.3) {

                            location.left = (int) (location.left * scalew);
                            location.right = (int) (location.right * scalew);
                            location.top = (int) (location.top * scaleh);
                            location.bottom = (int) (location.bottom * scaleh);

                            if (result.getTitle().equals("helmet")) {
                                //头盔在图像中的位置
                                canvas.drawRect(location, paint_helmet);//画头盔
                                canvas.drawText(i+"Helmet", location.left, location.top, paint_helmet);
                            }
                            else if (result.getTitle().equals("box")) {
                                distance = (location.left + location.right) / 2 - resized_image.getWidth() / 2;
                                //设置离中间距离的界限
                                if (distance < center_threshold) {
                                    if (num_box == 0)
                                    {
                                        target_location = location;
                                        num_box++;
                                        distance_target = (target_location.left + target_location.right) / 2 - resized_image.getWidth() / 2;
                                    } else if (Math.abs(distance_target) > Math.abs(distance)) {

                                        target_location = location;
                                        num_box++;
                                        distance_target = (target_location.left + target_location.right) / 2 - resized_image.getWidth() / 2;
                                    }

                                }
                                canvas.drawRect(location, paint_box);//画箱子
                                canvas.drawText(i+"Box", location.left, location.top, paint_box);
                            }
                            else if (result.getTitle().equals("qrcode")){
                                canvas.drawRect(location, paint_qrcode);//画头盔
                                canvas.drawText(i+"Qrcode", location.left, location.top, paint_qrcode);
                            }
                        }

                        temp += i + result.getTitle() + location.toString() + '\n';
                    }
                    //经过上面的选择找到最靠近中间的箱子
                    if (num_box > 0) {
                        canvas.drawRect(target_location, target_box);//画目标箱子的位置，即最靠近中间的箱子
                        canvas.drawText("Target", target_location.left, target_location.top, target_box);
                    }

//                    runOnUiThread();
                    final String showstr = temp;
                    final Bitmap showimage = resized_image;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(showimage);
                            detectresultTextviewer.setText(showstr);
                        }
                    });
                }
                else{
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}

