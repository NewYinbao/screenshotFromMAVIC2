package com.example.djidemo;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class myLog {

    // log to file
    public static Date date = new Date();
    public static String logPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/mylogs/";
    public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US);//日期格式;


    /*
     * 保存日志到文件 mylog/ , 文件名以时间命名
     * 输入两个字符串,标签 和 信息
     */
    public static void saveLogs(String tag, String msg) {

        if (null == logPath) {
            Log.e("error", "logPath == null ，未初始化LogToFile");
            return;
        }

        String fileName = logPath + "/log_" + dateFormat.format(date) + ".txt";//log日志名，使用时间命名，保证不重复
        String log = dateFormat.format(new Date()) + ": " + tag + " " + msg + "\n";//log日志内容，可以自行定制

        //如果父路径不存在
        File file = new File(logPath);
        if (!file.exists()) {
            file.mkdirs();//创建父路径
        }

        FileOutputStream fos = null;//FileOutputStream会自动调用底层的close()方法，不用关闭
        BufferedWriter bw = null;
        try {

            fos = new FileOutputStream(fileName, true);//这里的第二个参数代表追加还是覆盖，true为追加，flase为覆盖
            bw = new BufferedWriter(new OutputStreamWriter(fos));
            bw.write(log);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.close();//关闭缓冲流
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    /*
     * 保存short数组到文件,文件目录mylogs
     * 输入: verts 需要保存的数组
     *      gcodeFile 保存文件名
     *      count 保存的数据长度(short类型)
     * 输出:无
     */
    public static void writeShortToData(short[] verts, String gcodeFile, int count) {
        if (null == logPath) {
            Log.v("error", "logPath == null ，未初始化LogToFile");
            return;
        }
        File file = new File(logPath);
        if (!file.exists()) {
            file.mkdirs();//创建父路径
        }
        gcodeFile = logPath + gcodeFile;
        try {
            RandomAccessFile aFile = new RandomAccessFile(gcodeFile, "rw");
            FileChannel outChannel = aFile.getChannel();
            //one float 4 bytes
            ByteBuffer buf = ByteBuffer.allocate(2 * count);
            buf.clear();
            buf.asShortBuffer().put(verts);
            //while(buf.hasRemaining())
            {
                outChannel.write(buf);
            }
            buf.rewind();
            outChannel.close();
            Log.v("nyb: ", "save data success, byte size is "+(buf.limit()-buf.position()));
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            Log.v("nyb: ", "save data error!"  + ex.getMessage());
        }
    }

    /*
     * 从文件读取short数组,文件夹/mylogs, 文件完整路径为/mylogs/<gcodefile>
     * 输入: gcodeFile 保存文件名
     *      count 读取的数据长度(short类型)
     * 输出: 长度为count的short数组
     */
    public static short[] readShortFromData(String gcodeFile, int Count) {
        short[] verts = new short[Count];

        gcodeFile = logPath + gcodeFile;
        File file = new File(logPath);
        if (!file.exists()) {
            return null;
        }

        try {
            RandomAccessFile rFile = new RandomAccessFile(gcodeFile, "rw");
            FileChannel inChannel = rFile.getChannel();
            ByteBuffer buf_in = ByteBuffer.allocate(Count * 2);
            buf_in.clear();
            inChannel.read(buf_in);

            buf_in.rewind();
            Log.v("nyb: ", "bufin_size = " + (buf_in.limit()-buf_in.position()));
            buf_in.asShortBuffer().get(verts);
            inChannel.close();
            Log.v("nyb: ", "read data success, short size is "+Count);
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            Log.v("nyb: ", "save data error!" + ex.getMessage());
        }
        return verts;
    }
}