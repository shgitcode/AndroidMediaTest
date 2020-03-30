package com.shgit.mediasdk.util;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/*
* 提取音视频
* */
public class CMediaExtractor {
    private final String TAG = "CMediaExtractor";
    private final int QUEUE_LENGTH = 10;

    private boolean m_bIsCreate = false;

    private String m_sFilePath;

    // 采用FD方式设置setDataSource
    private FileDescriptor m_sFd;
    private FileOutputStream m_sFOS = null;

    // 提取器
    private MediaExtractor m_cMediaExtractor = null;

    // 标志
    private boolean m_bIsVidExtractor = false;
    private boolean m_bIsExtracting = false;
    // 提取是否结束
    private boolean m_bIsStop = false;

    // 音视频存储队列
    private CDataQueue<CRawFrame> m_cDataQue = null;
    // 提取视频线程
    private vidExtractThread m_cVidExtractThread = null;
    // 提取音频线程
    private audExtractThread m_cAudExtractThread = null;

    // 视频属性
    private int m_nWidth = 0;
    private int m_nHeight = 0;
    private float m_fVidDuration = 0;
    private int m_nFrameRate = 30;
    private int m_nVidTrackIndex = -1;
    private MediaFormat m_cVidFormat = null;

    // 音频属性
    private int m_nAudTrackIndex = -1;
    private int m_nSampleRate = 0;
    private int m_nChannel = 0;
    private float m_fAudDuration = 0;
    private MediaFormat m_cAudFormat = null;


    /*
     * bVid: 决定提取音频还是视频
     * bUseFd：是否采用FD
     * */
    public void create(boolean bVid, String filePath, boolean bUseFd){
        Log.d(TAG,"create : "+m_bIsCreate);

        if (m_bIsCreate) {
            return;
        }

        m_bIsVidExtractor = bVid;
        m_bIsExtracting = false;

        release();

        if (m_cMediaExtractor == null) {
            m_cMediaExtractor = new MediaExtractor();
        }

        // 数据存放队列
        m_cDataQue = new CDataQueue<>();
        m_cDataQue.create(QUEUE_LENGTH, "mediaExtractorQue");

        setDataSource(filePath, bUseFd);

        // 获取音视频轨道
        getTrackIndex();

        // 获取视频格式信息
        if (m_bIsVidExtractor) {
            getVideoMediaFormat();
        } else {
            getAudioMediaFormat();
        }

        m_bIsCreate = true;
    }

    public void start(){
        if (!m_bIsCreate) {
            return;
        }

        Log.d(TAG,"start: "+m_bIsExtracting);

        if (m_bIsExtracting) {
            return;
        }

        m_bIsExtracting = true;
        m_bIsStop = false;

        // 开启音视频线程
        if (m_bIsVidExtractor) {
            if (m_cVidExtractThread == null) {
                m_cVidExtractThread = new vidExtractThread();
                m_cVidExtractThread.start();
            }

        } else {
            if (m_cAudExtractThread == null) {
                m_cAudExtractThread = new audExtractThread();
                m_cAudExtractThread.start();
            }

        }
    }

    // 获取音视频数据
    public CRawFrame getData() {
        if (!m_bIsCreate) {
            return null;
        }

        return m_cDataQue.getData();
    }

    // 获取音视频媒体信息
    public MediaFormat getMediaFormat() {
        if (!m_bIsCreate) {
            return null;
        }

        // 视频提取
        if (m_bIsVidExtractor) {
            return m_cVidFormat;
        } else {
            return m_cAudFormat;
        }
    }

    // 判断解析是否结束
    public boolean isStop() {
        return m_bIsStop;
    }

    public void stop() {
        if (!m_bIsCreate) {
            return;
        }

        m_bIsExtracting = false;
        m_bIsStop = true;

        Log.d(TAG,"stop: "+m_bIsExtracting);

        if (m_bIsVidExtractor) {
            if (m_cVidExtractThread != null) {
                try {
                    m_cVidExtractThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                m_cVidExtractThread = null;
            }

        } else {
            if (m_cAudExtractThread != null) {
                try {
                    m_cAudExtractThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                m_cAudExtractThread = null;
            }
        }

        if (m_sFOS != null) {
            try {
                m_sFOS.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            m_sFOS = null;
            m_sFd = null;
        }

        release();
    }

    private void release() {
        if (m_cMediaExtractor != null) {
            m_cMediaExtractor.release();
            m_cMediaExtractor = null;
        }
        m_bIsCreate = false;
    }

    // 输入流媒体文件
    private void setDataSource(String filePath, boolean bUseFd) {
        m_sFilePath = filePath;

        Log.d(TAG, "setDataSource : "+filePath);

        if (m_cMediaExtractor == null){
            return;
        }

        if (bUseFd) {
            try {
                m_sFOS = new FileOutputStream(filePath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            try {
                m_sFd = m_sFOS.getFD();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                m_cMediaExtractor.setDataSource(m_sFd);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            try {
                // 好像是否只能创建一个实例，此处需重写
                m_cMediaExtractor.setDataSource(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    // 获取音视频轨道信息
    private  int getTrackIndex() {
        if (m_cMediaExtractor == null) {
            return 1;
        }

        for (int i = 0; i < m_cMediaExtractor.getTrackCount(); i++) {
            MediaFormat mediaFormat = m_cMediaExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

            if (mime.startsWith("video/")) {
                m_nVidTrackIndex = i;
                m_cVidFormat = mediaFormat;
            }

            if (mime.startsWith("audio/")) {
                m_nAudTrackIndex = i;
                m_cAudFormat = mediaFormat;
            }
        }

        Log.d(TAG,"TrackIndex  video : "+m_nVidTrackIndex+" audio: "+m_nAudTrackIndex);

        return 0;
    }

    // 视频媒体信息
    private int getVideoMediaFormat(){

        if (m_cMediaExtractor == null) {
            return 1;
        }

        if (m_nVidTrackIndex == -1) {
            return 1;
        }

        // 获取视频所在轨道
        MediaFormat mediaFormat = m_cMediaExtractor.getTrackFormat(m_nVidTrackIndex);
        m_nWidth  = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        m_nHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            m_nFrameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        } else {
            m_nFrameRate = 30;
        }

        m_fVidDuration = mediaFormat.getLong(MediaFormat.KEY_DURATION) / 1000000;

        Log.d(TAG,"VideoMediaFormat  width : "+m_nWidth+" height: "+m_nHeight + " duration: "+m_fVidDuration);

        return 0;
    }

    private int getAudioMediaFormat(){
        if (m_cMediaExtractor == null) {
            return 1;
        }

        if (m_nAudTrackIndex == -1) {
            return 1;
        }
        // 获取音频所在轨道
        MediaFormat mediaFormat = m_cMediaExtractor.getTrackFormat(m_nAudTrackIndex);
        m_nSampleRate  = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        m_nChannel = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        m_fAudDuration = mediaFormat.getLong(MediaFormat.KEY_DURATION) / 1000000;

        Log.d(TAG,"AudioMediaFormat  sampleRate : "+m_nSampleRate+" channel: "+m_nChannel + " duration: "+m_fAudDuration);
        return 0;
    }

    /*
     * 是否可以同时读取音视频数据
     * */

    private int readVideoTrackData(){
        if (m_cMediaExtractor == null) {
            return 1;
        }

        if (m_nVidTrackIndex == -1) {
            return 1;
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
        //切换到视频轨道
        m_cMediaExtractor.selectTrack(m_nVidTrackIndex);

        while (true) {

            if (m_bIsExtracting) {
                int readSampleCount = m_cMediaExtractor.readSampleData(byteBuffer, 0);

                Log.d(TAG, "video:readSample Count:" + readSampleCount);

                // 提取结束
                if (readSampleCount < 0) {
                    m_bIsStop = true;

                    CRawFrame cPcmData = new CRawFrame();
                    cPcmData.m_sFrame = null;
                    cPcmData.m_bIsEos = true;
                    cPcmData.presentationTimeUs = 0;

                    // 写队列
                    m_cDataQue.setData(cPcmData);

                    Log.d(TAG, "video:readSample EOS!");

                    break;
                }

                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);

                CRawFrame cPcmData = new CRawFrame();
                cPcmData.m_sFrame = buffer;
                cPcmData.m_bIsEos = false;
                cPcmData.presentationTimeUs =  m_cMediaExtractor.getSampleTime();

                Log.d(TAG, "video:readSample time:" + cPcmData.presentationTimeUs);

                // 写队列
                m_cDataQue.setData(cPcmData);
                byteBuffer.clear();

                m_cMediaExtractor.advance();
            } else {

                if (m_bIsStop) {
                    Log.d(TAG, "video: readSample stop!");
                    break;
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }

        return 0;
    }

    private class vidExtractThread extends Thread {

        @Override
        public void run(){

            if (readVideoTrackData() !=0 ) {
                Log.d(TAG, "readVideoTrackData failed!");
                return;
            }

            Log.d(TAG, "readVideoTrackData success");
        }
    }

    private int readAudioTrackData(){
        if (m_cMediaExtractor == null) {
            return 1;
        }

        if (m_nAudTrackIndex == -1) {
            return 1;
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);

        //切换到音频轨道
        m_cMediaExtractor.selectTrack(m_nAudTrackIndex);

        while (true) {
            if (m_bIsExtracting) {
                int readSampleCount = m_cMediaExtractor.readSampleData(byteBuffer, 0);

                Log.d(TAG, "audio:readSample Count:" + readSampleCount);

                // 提取结束
                if (readSampleCount < 0) {
                    m_bIsStop = true;
                    CRawFrame cPcmData = new CRawFrame();
                    cPcmData.m_sFrame = null;
                    cPcmData.m_bIsEos = true;
                    cPcmData.presentationTimeUs = 0;
                    m_cDataQue.setData(cPcmData);

                    Log.d(TAG, "audio:readSample EOS!");

                    break;
                }


                //保存音频信息
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);

                CRawFrame cPcmData = new CRawFrame();
                cPcmData.m_sFrame = buffer;
                cPcmData.m_bIsEos = false;
                cPcmData.presentationTimeUs = m_cMediaExtractor.getSampleTime();

                Log.d(TAG, "audio:readSample time:" + cPcmData.presentationTimeUs);

                m_cDataQue.setData(cPcmData);
                byteBuffer.clear();

                m_cMediaExtractor.advance();
            } else {

                if (m_bIsStop) {
                    Log.d(TAG, "audio: readSample stop!");
                    break;
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        return 0;
    }

    private class audExtractThread extends Thread {

        @Override
        public void run(){

            if (readAudioTrackData() != 0) {
                Log.d(TAG, "readAudioTrackData failed!");
                return;
            }

            Log.d(TAG, "readAudioTrackData success");
        }
    }
}
