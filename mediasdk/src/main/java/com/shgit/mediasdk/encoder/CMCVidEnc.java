package com.shgit.mediasdk.encoder;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CFileManage;
import com.shgit.mediasdk.util.CRawFrame;
import com.shgit.mediasdk.util.CVidInfo;
import com.shgit.mediasdk.util.libYuvConvert;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
* 编码为AVC数据
* 存储为vidEnc.h264
* */
public class CMCVidEnc {
    private final String TAG = "CMCVidEnc";
    private final String FILE_NAME = "vidEnc.h264";
    private final int QUEUE_LENGTH = 10;

    // 视频参数
    private int m_nEncWidth  = 1920;
    private int m_nEncHeight = 1080;
    private int m_nFrameRate = 30;
    private int m_nSupportFormat;

    // 原始数据队列
    private CDataQueue<CRawFrame> m_cRawDataQue = null;
    // 已编码数据队列
    private boolean m_bNeedEcodedDataQue = true;
    private CDataQueue<CRawFrame> m_cEcodedDataQue = null;

    // encoder
    private CMCEncWrapper m_cMCEnc = null;
    private MediaFormat m_cMediaFormat = null;

    // file
    private CFileManage m_cWriteFile = null;

    // executor
    private ExecutorService m_cSndThread = null;
    // 发送指标
    private boolean m_bSending = false;
    private Thread m_cEncThread = null;
    // 编码指标
    private boolean m_bEncoding = false;
    private boolean m_bEncStop = false;

    //ffprobe -v quiet -print_format json -show_format -show_streams vidEnc.h264
    // bNeedEcodedQue : 用于保存或解码
    public CMCVidEnc(boolean bNeedEcodedQue) {
        m_cRawDataQue    =  new CDataQueue<>();
        //已编码队列
        m_bNeedEcodedDataQue = bNeedEcodedQue;
        if (m_bNeedEcodedDataQue) {
            m_cEcodedDataQue =  new CDataQueue<>();
        }

        m_bEncoding = false;
        m_bSending = false;
        m_bEncStop = false;
    }

    public void setMCParamter(CVidInfo vidInfo) {
         m_nEncWidth  = vidInfo.getWidth();
         m_nEncHeight = vidInfo.getHeight();
         m_nFrameRate = vidInfo.getFrameRate();
    }

    public int createMCEncoder(){
        int nRetVal = 0;

        Log.d(TAG, "createMCEncoder!");

        m_cMCEnc = new CMCEncWrapper(true);

        m_cRawDataQue.create(QUEUE_LENGTH,"vidRawQue");
        if (m_cEcodedDataQue != null) {
            m_cEcodedDataQue.create(QUEUE_LENGTH,"vidEncQue");
        }

        createEncoderCodec();

        // thread
        m_cEncThread = new Thread(encRawData);
        m_cEncThread.start();

        m_cSndThread = Executors.newSingleThreadExecutor();
        m_cSndThread.submit(SndEncData);

        // file
        m_cWriteFile = new CFileManage();
        m_cWriteFile.createSavedFile(FILE_NAME);

        return nRetVal;
    }

    public void start() {
        m_cMCEnc.start();
        m_bEncoding = true;
        m_bSending = true;
        m_bEncStop = false;
    }

    public boolean isEncStop(){
        return m_bEncStop;
    }

    public void stop(){
        // 编码器
        if (m_cMCEnc != null) {
            m_cMCEnc.stop();
        }

        m_bEncoding = false;
        m_bSending = false;
        m_bEncStop = true;

        release();
    }

    public void release(){

        // 等待编码结束
        while (!m_bEncStop) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // thread
        if (m_cEncThread != null) {
            try {
                m_cEncThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cEncThread = null;
        }

        if (m_cSndThread != null) {
            m_cSndThread.shutdownNow();
            m_cSndThread = null;
        }

        // file
        if (m_cWriteFile != null) {
            m_cWriteFile.closeSavedFile();
        }

        m_bEncoding = false;
    }

    public void quitRawQueue(){
        if (m_cRawDataQue != null) {
            m_cRawDataQue.quit();
        }
    }

    public void quitEncodedQueue(){
        if (m_cEcodedDataQue != null) {
            m_cEcodedDataQue.quit();
        }
    }

    public void destroyMCEncoder() {
        if (m_cRawDataQue != null) {
            m_cRawDataQue.clear();
            m_cRawDataQue = null;
        }

        if (m_cEcodedDataQue != null) {
            m_cEcodedDataQue.clear();
            m_cEcodedDataQue = null;
        }

        if (m_cMCEnc != null) {
            m_cMCEnc.destroy();
        }
    }

    public void setRawData(CRawFrame sRawData){
        Log.d(TAG, "setRawData!");
        m_cRawDataQue.setData(sRawData);
    }

    public CRawFrame getEncodedData(){
        if (m_cEcodedDataQue != null) {
            return m_cEcodedDataQue.getData();
        }
        return null;
    }

    public String getSavedFileName() {
        if (m_cWriteFile != null) {
            return m_cWriteFile.getSavedFileName();
        }

        return null;
    }

    public boolean getOutputEos() {
        return m_cMCEnc.getOutputEos();
    }

    public  MediaFormat getOutputMediaFormat() {
        return m_cMCEnc.getOutputMediaFormat();
    }

    private void createVideoFormat(){
        Log.d(TAG, "createVideoFormat!");

        m_cMediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, m_nEncWidth, m_nEncHeight);

        m_cMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4096000);
        m_cMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        m_cMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, m_nFrameRate);
        m_cMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);   //interval frames  = KEY_FRAME_RATE * KEY_I_FRAME_INTERVAL(seconds)
        //m_cInputFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        // m_cInputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
    }

    // 创建合适编码器
    private void createEncoderCodec() {

        m_cMCEnc.create(MediaFormat.MIMETYPE_VIDEO_AVC);

        createVideoFormat();

        int numCodecs = MediaCodecList.getCodecCount();

        Log.d(TAG, "MediaCodec numCodecs:  " + numCodecs);

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (codecInfo.isEncoder()) {
                Log.d(TAG, "Encoder codec:  " + codecInfo.getName());

                String[] types = codecInfo.getSupportedTypes();
                boolean found = false;

                for (int j = 0; j < types.length && !found; j++) {
                    if (types[j].equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                        Log.d(TAG, "Found codec:  " + codecInfo.getName());
                        found = true;
                    }
                }

                if (!found) {
                    continue;
                }

                if(createSupportCodec(codecInfo) == 0) {
                    break;
                }

            }
        }

    }

    // 合适编码器
    private int createSupportCodec(MediaCodecInfo mcInfo) {
        // Find a color profile that the codec supports
        int ret = 0;

        if (mcInfo == null) {
            return 1;
        }

        MediaCodecInfo.CodecCapabilities capabilities = mcInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);

        Log.d(TAG, " colorFormats length: " + capabilities.colorFormats.length + " == " + Arrays.toString(capabilities.colorFormats));

        for (int i = 0; i < capabilities.colorFormats.length; i++) {

            m_nSupportFormat = 0;

            switch (capabilities.colorFormats[i]) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    //case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                    m_nSupportFormat = capabilities.colorFormats[i];
                    Log.d(TAG, "supported colorFormats:" + capabilities.colorFormats[i]);
                    break;

                default:
                    Log.d(TAG, "other colorFormats: " + capabilities.colorFormats[i]);
                    break;
            }

            if (m_nSupportFormat != 0) {
                Log.d(TAG, " create MediaCodec by colorFormats:" + m_nSupportFormat);
                m_cMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, m_nSupportFormat);

                if(m_cMCEnc != null) {
                    m_cMCEnc.configure(m_cMediaFormat);
                }
            }

            if(m_cMCEnc != null) {
                if (m_cMCEnc.isConfigure()) {
                    return 0;
                }
            }
        }
        return 1;
    }

    // https://blog.csdn.net/qq_34557284/article/details/90902363
    // NV21:YYYY VU
    // NV12:YYYY UV == yuv420sp
    private void NV21ToNV12_vFlip(byte[] nv21, int width, int height, byte[] nv12, boolean isvFlip) {
        libYuvConvert.yuvNV21ToNV12_vFlip(nv21, width, height, nv12, isvFlip);
    }

    private void NV21ToI420_vFlip(byte[] nv21, int width, int height, byte[] i420, boolean isvFlip) {
        libYuvConvert.yuvNV21ToI420_vFlip(nv21, width, height, i420, isvFlip);
    }

    // 编码线程
    Runnable encRawData = new Thread(new Runnable() {
        @Override
        public void run() {
            int isEos = 0;
            long delay = 10;
            int frameNum = 0;

            CRawFrame cRawData = null;
            byte[] sYuvData = null;
            byte[] yuv420sp = null;

            Log.d(TAG, "encRawData！");

            while (true) {

                long startTime = System.nanoTime() / 1000;

                if(m_bEncoding){
                    delay = 0;
                    cRawData = m_cRawDataQue.getData();
                    if(cRawData != null){
                        if (cRawData.m_bIsEos) {
                            isEos = 1;
                        } else {
                            //int len = cRawData.m_sFrame.length; // 7104
                            sYuvData = cRawData.m_sFrame;
                            yuv420sp = new byte[m_nEncWidth * m_nEncHeight *3 /2];

                            if (m_nSupportFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                                NV21ToNV12_vFlip(sYuvData, m_nEncWidth, m_nEncHeight, yuv420sp, cRawData.m_bIsVFlip);
                            } else if (m_nSupportFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                                NV21ToI420_vFlip(sYuvData, m_nEncWidth, m_nEncHeight, yuv420sp, cRawData.m_bIsVFlip);
                            } else {

                            }
                            frameNum++;
                        }

                        if (m_cMCEnc != null) {
                            CRawFrame cYuv = new CRawFrame();
                            cYuv.m_sFrame = yuv420sp;
                            cYuv.m_bIsEos = cRawData.m_bIsEos;
                            cYuv.presentationTimeUs = cRawData.presentationTimeUs;
                            m_cMCEnc.setRawData(cYuv);
                        }

                        delay = 1000/m_nFrameRate - (System.nanoTime()/1000 -  startTime);

                        Log.d(TAG, "encRawData frameNum: "+frameNum+" format: "+m_nSupportFormat);
                    }

                    if (isEos == 1) {// 数据全部取出
                        Log.d(TAG, "encRawData EOS frameNum: "+frameNum);
                        m_bEncStop = true;
                        //break;
                    }

                    if (m_bEncStop) {
                        Log.d(TAG, "encRawData stop! ");
                        break;
                    }

                    try {
                        if (delay <= 0){
                            delay = 5;
                        }

                        Thread.sleep(delay);

                        Log.d(TAG, "encRawData sleep: " + delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }else{
                    if (m_bEncStop) {
                        Log.d(TAG, "encRawData stop! ");
                        break;
                    }

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    });

    Runnable SndEncData = new Thread(new Runnable() {
        @Override
        public void run() {

            boolean bIsEos = false;
            int delay = 10;
            CRawFrame cData = null;

            Log.d(TAG, "SndEncData!");

            while (true) {

                byte[] cEncData = null;

                if(m_bSending){

                    if (m_cMCEnc != null) {

                        if (m_cMCEnc.getOutputEos()) {
                            // 编码结束
                            if (m_cMCEnc.encodedDataQueIsEmpty()) {
                                bIsEos = true;
                                Log.d(TAG, "SndEncData EOS! ");
                            }
                        }

                        cData = new CRawFrame();
                        cData.m_bIsEos = bIsEos;

                        if (!bIsEos) {
                            cEncData = m_cMCEnc.getEncodedData();
                        }

                        // 数据入队列
                        if (m_cEcodedDataQue != null) {
                            cData.m_sFrame = cEncData;
                            m_cEcodedDataQue.setData(cData);
                        }
                    }

                    if(cEncData != null){
                        Log.d(TAG, "SndEncData data size:"+cEncData.length);
                        // 写文件
                        if (m_cWriteFile != null) {
                            m_cWriteFile.writeSavedFile(cEncData, cEncData.length);
                        }
                    }

                    if (bIsEos) {
                        if (m_cWriteFile != null) {
                            m_cWriteFile.closeSavedFile();
                        }
                        m_bEncStop = true;
                        Log.d(TAG, "SndEncData end! ");
                        //break;
                    }

                    if (m_bEncStop) {
                        Log.d(TAG, "SndEncData stop! ");
                        break;
                    }

                    try {
                        if (delay <= 0) {
                            delay = 5;
                        }
                        Thread.sleep(delay);
                        Log.d(TAG, "SndEncData delay; " + delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else{

                    if (m_bEncStop) {
                        Log.d(TAG, "SndEncData stop! ");
                        break;
                    }

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    });

}
