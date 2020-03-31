package com.shgit.mediasdk.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CRawFrame;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CMCEncWrapper {
    private final String TAG = "CMCEncWrapper";
    private final int QUEUE_LENGTH = 10;

    public MediaCodec m_cMediaCodec = null;

    // 输入输出格式
    private MediaFormat m_cInputMediaFormat = null;
    private MediaFormat m_cOutputMediaFormat = null;

    private byte[] m_sOutData;
    private String m_cEncTypeName = null;
    private int m_nEncFrameNum = 0;
    // 编码标志
    private boolean m_bIsEncoding = false;

    // 输入输出
    private ByteBuffer[] m_cInputBuffers = null;
    private ByteBuffer[] m_cOutputBuffers = null;

    // encoded data
    private CDataQueue<byte[]> m_cEncodedQueue = null;
    private boolean m_bIsOutputEos = false; // 表示输出端编码结束
    private boolean m_bSetInputEos = false; // 表示输入端是否成功输入EOS

    // ------video--------
    private boolean m_bIsVideo = false;

    // csd
    public byte[]        m_sConfigbyte;
    private boolean      m_bIsConfig = false;

    // thread
    private HandlerThread m_cEncHldThd = null;


    public  CMCEncWrapper(boolean isVideo){
        Log.d(TAG, "Construction Video: "+isVideo);
        m_bIsVideo   = isVideo;
    }

    public int create(String typeName) {
        Log.d(TAG, "create name: "+typeName);

        m_bIsConfig = false;

        m_cEncodedQueue = new CDataQueue<>();
        m_cEncodedQueue.create(QUEUE_LENGTH, "MCEncoderQue");

        listEncoderName();

        m_cEncTypeName = typeName;

        createMediaCodec();

        return 0;
    }

    public void configure(MediaFormat cMediaFormat){
        m_cInputMediaFormat = cMediaFormat;
        configureMediaCodec();
    }

    public void start(){
        Log.d(TAG, "start");

        m_bIsOutputEos = false;
        m_bSetInputEos = false;

        m_bIsEncoding = true;
    }

    public void stop(){
        Log.d(TAG, "stop");

        if (m_cEncodedQueue != null) {
            m_cEncodedQueue.quit();
        }

        m_bIsEncoding = false;

        if (m_cEncHldThd != null) {
            m_cEncHldThd.quit();
            m_cEncHldThd = null;
        }

        m_bIsOutputEos = false;

        releaseMediaCodec();
    }

    public void destroy() {
        if (m_cEncodedQueue != null) {
            m_cEncodedQueue.clear();
            m_cEncodedQueue = null;
        }
    }

    // 编码结束标志
    public boolean getOutputEos() {
        return m_bIsOutputEos;
    }

    public byte[] getEncodedData() {
        if (m_cEncodedQueue != null) {
            return m_cEncodedQueue.getData();
        }

        return null;
    }

    public boolean encodedDataQueIsEmpty() {
        if (m_cEncodedQueue != null) {
            return  m_cEncodedQueue.isEmpty();
        }

        return true;
    }

    public byte[] getEncodedCsd() {
        return m_sConfigbyte;
    }

    public int setRawData(CRawFrame sRawData){
        if(m_cMediaCodec == null){
            Log.e(TAG, "m_cMediaCodec instance null");
            return -1;
        }

        if(!m_bIsEncoding){
            Log.e(TAG, "m_cMediaCodec is stop");
            return -1;
        }

        Log.d(TAG, " setRawData EOS: "+sRawData.m_bIsEos + " PTS: "+sRawData.presentationTimeUs);

        Message message = m_cEncHandler.obtainMessage();
        message.what = 0x1;
        message.obj = sRawData;
        m_cEncHandler.sendMessage(message);

        return 0;
    }

    public void setInputMediaFormat(MediaFormat cMediaFormat){
        m_cInputMediaFormat = cMediaFormat;
    }

    public MediaFormat getOutputMediaFormat(){
        return m_cOutputMediaFormat;
    }

    public boolean isConfigure() {
        return m_bIsConfig;
    }


    private HandlerThread createHandlerThread(){
        // create thread
        // https://www.jianshu.com/p/9c10beaa1c95
        m_cEncHldThd = new HandlerThread("EncDataThread");

        m_cEncHldThd.start();

        return m_cEncHldThd;
    }

    private int m_nFrameNum = 0;

    private Handler m_cEncHandler = new Handler(createHandlerThread().getLooper()) {
        @Override
        public void handleMessage(Message msg) {
            int nRawLen = 0;
            boolean bIsEos = false;
            int nRetVal = 0;
            long startMs = System.currentTimeMillis();

            switch(msg.what){
                case 0x1:
                    // not EOS
                    m_nFrameNum++;
                    CRawFrame rawFrame = (CRawFrame)msg.obj;
                    bIsEos = rawFrame.m_bIsEos;
                    if (!bIsEos) {
                        nRawLen = rawFrame.m_sFrame.length;
                        Log.d(TAG, "EncHandler Raw length: " +  nRawLen);
                    }

                    Log.d(TAG, "EncHandler Raw FrameNum: " +  m_nFrameNum);

                    nRetVal = inputRawData(rawFrame.m_sFrame, bIsEos, rawFrame.presentationTimeUs,10);
                    if (nRetVal == 0) {
                        if (!bIsEos) {
                            nRetVal = outputEncodedData(10);
                        } else {
                            // 没有输入，取出缓存中所有编码数据
                            Log.d(TAG, "EncHandler handleMessage EOS");
                            while (!m_bIsOutputEos) {
                                if (!m_bSetInputEos) {
                                    inputRawData(null, true, rawFrame.presentationTimeUs,1000);
                                }
                                nRetVal = outputEncodedData(1000);
                            }
                        }

                    }
                    break;
                default:
                    Log.d(TAG, "Handle message :"+msg.what);
                    break;
            }

            long endMs = System.currentTimeMillis();

            Log.d(TAG, "mediacodec encode: "+m_nEncFrameNum+ " cost time: "+ (endMs - startMs)+" enc length: " + nRetVal);

            return;
        }
    };
    // *************private function********************
    private void listEncoderName()
    {
        int numCodecs = MediaCodecList.getCodecCount();
        Log.d(TAG, "found codec numCodecs:  " + numCodecs);
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (codecInfo.isEncoder()) {
                Log.i(TAG, "found codec:  " + codecInfo.getName());
            }
        }
    }

    private int inputRawData(byte[] sRawData, boolean bIsEos, long pts, long timeout){
        if(m_cMediaCodec == null){
            Log.e(TAG, "m_cMediaCodec instance null");
            return -1;
        }

        if (sRawData != null) {
            Log.d(TAG, "inputRawData length: "+sRawData.length+" EOS: "+bIsEos);
        }

        try {
            ByteBuffer inputBuffer = null;
            int nInputId = m_cMediaCodec.dequeueInputBuffer(timeout);
            Log.d(TAG, "inputRawData nInputId: "+nInputId);
            if (nInputId > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    inputBuffer = m_cMediaCodec.getInputBuffer(nInputId);
                } else {
                    inputBuffer = m_cInputBuffers[nInputId];
                }

                inputBuffer.clear();

                if (!bIsEos) {
                    // 如果m_cMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, m_nAudioBufferSize);设置的size小于数据的长度，会报错
                    inputBuffer.put(sRawData);
                    m_cMediaCodec.queueInputBuffer(nInputId, 0, inputBuffer.position(), pts, 0);
                } else {
                    m_cMediaCodec.queueInputBuffer(nInputId, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    m_bSetInputEos = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    private int outputEncodedData(long timeout){
        if(m_cMediaCodec == null){
            Log.e(TAG, "m_cMediaCodec is null");
            return -1;
        }

        int nRetval = 0;

        try {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int nOutputId = m_cMediaCodec.dequeueOutputBuffer(bufferInfo, timeout);
            if (nOutputId == MediaCodec.INFO_TRY_AGAIN_LATER) {//TIMEOUT
                Log.d(TAG, "outputEncodedData INFO_TRY_AGAIN_LATER");//TODO how to declare this info
                return 0;
            } else if (nOutputId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "outputEncodedData output format changed");
                m_cOutputMediaFormat = m_cMediaCodec.getOutputFormat();
                return 0;
            } else if (nOutputId < 0) {
                Log.d(TAG, "outputEncodedData outputIndex=" + nOutputId);
                return 0;
            } else {

                ByteBuffer outputBuffer = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    outputBuffer = m_cMediaCodec.getOutputBuffer(nOutputId);
                } else {
                    outputBuffer = m_cOutputBuffers[nOutputId];
                }

                // 获取编码数据
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                Log.d(TAG, "outputEncodedData bufferInfo.size: " + bufferInfo.size +
                        ", flags: "+ bufferInfo.flags +" pts: "+bufferInfo.presentationTimeUs);

                if(m_bIsVideo&&(bufferInfo.size > 4)) {
                    Log.d(TAG, "outputEncodedData startcode: " + outData[0] + outData[1] +
                            outData[2] + outData[3]);
                }

                if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG){ // BUFFER_FLAG_CODEC_CONFIG
                    m_sConfigbyte = new byte[bufferInfo.size];
                    System.arraycopy(outData, 0, m_sConfigbyte, 0, m_sConfigbyte.length);
                }else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM){
                    m_nEncFrameNum++;
                    Log.d(TAG, "outputEncodedData Encoder EOS!");

                    if (bufferInfo.size > 0) {
                        if (m_cEncodedQueue != null) {
                            m_cEncodedQueue.setData(outData); // 编码数据放入队列
                        }
                    }

                    m_bIsOutputEos = true;
                } else {

                    if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME){ // BUFFER_FLAG_KEY_FRAME
                        if(m_bIsVideo) {
                            // 对于视频每个关键帧前加CSD
                            byte[] keyframe = new byte[bufferInfo.size + m_sConfigbyte.length];
                            System.arraycopy(m_sConfigbyte, 0, keyframe, 0, m_sConfigbyte.length);
                            System.arraycopy(outData, 0, keyframe, m_sConfigbyte.length, outData.length);
                            m_sOutData = keyframe;
                        } else {
                            m_sOutData = outData;
                        }
                    } else {
                        m_sOutData = outData;
                    }

                    nRetval = m_sOutData.length;
                    m_nEncFrameNum++;

                    if (m_cEncodedQueue != null) {
                        m_cEncodedQueue.setData(m_sOutData); // 编码数据放入队列
                    }
                }
                m_cMediaCodec.releaseOutputBuffer(nOutputId, false);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return nRetval;
    }

    private void releaseMediaCodec() {
        Log.d(TAG, "releaseMediaCodec !");
        if (m_cMediaCodec != null) {
            m_cMediaCodec.stop();
            m_cMediaCodec.release();
        }
        m_cMediaCodec = null;
    }

    private void createMediaCodec(){

        Log.d(TAG, "createMediaCodec : "+m_cEncTypeName);

        releaseMediaCodec();

        try {
            m_cMediaCodec = MediaCodec.createEncoderByType(m_cEncTypeName);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void configureMediaCodec(){

        Log.d(TAG, "configureMediaCodec !");

        if (m_cInputMediaFormat == null) {
            return;
        }

        if (m_cMediaCodec != null) {
            m_cMediaCodec.configure(m_cInputMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            m_cMediaCodec.start();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                m_cInputBuffers = m_cMediaCodec.getInputBuffers();
                m_cOutputBuffers = m_cMediaCodec.getOutputBuffers();
            }
        }

        m_bIsConfig = true;
    }
}
