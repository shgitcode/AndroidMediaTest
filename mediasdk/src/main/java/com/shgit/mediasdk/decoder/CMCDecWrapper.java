package com.shgit.mediasdk.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CRawFrame;

import java.nio.ByteBuffer;

/*
 * 传入音视频数据进行解码
 * https://developer.android.google.cn/reference/android/media/MediaCodec
 * https://www.jianshu.com/p/f5a1c9318524
 * */
public class CMCDecWrapper {

    private final String TAG  = "CMCDecWrapper";

    private final int    QUEUE_SIZE = 10;

    // 媒体类型：AAC/AVC
    private String m_cMediaType = null;

    private MediaCodec m_cMediaCodec = null;
    private MediaFormat m_cMediaFormat = null;
    private Surface m_cSurface = null;

    // 存储需解码的音视频数据
    private CDataQueue<CRawFrame> m_cDataQueue = null;
    // 存储已解码数据
    private boolean m_bNeedRawQueue = false;
    private CDataQueue<byte[]> m_cRawQueue = null;

    // 解码线程
    private Thread  m_cDecThread = null;
    // 输入输出
    private ByteBuffer[] m_cInputBuffers = null;
    private ByteBuffer[] m_cOutputBuffers = null;

    // 表示输出端解码结束
    private boolean m_bIsOutputEos = false;
    // 表示输入端结束输入
    private boolean m_bSetInputEos = false;

    // 输入输出数据统计
    private int          m_nInFrameCount = 0;
    private int          m_nOutFrameCount = 0;

    private byte[]       m_sCsd = null; // sps and pps

    private boolean      m_bIsVideo = false;

    private boolean      m_bDecStart = false;
    private boolean      m_bIsThreadStop = false;

    private boolean      m_bIsCreate = false;

    private boolean      m_bIsConfig = false;

    private boolean      m_bNeedRender = false;


    public int create(Surface cSurface, String cMediaType, boolean bNeedRawQue){

        Log.d(TAG,"create, MediaType: "+cMediaType);

        if (m_bIsCreate) {
            return 0;
        }

        if (cMediaType.startsWith("video/")) {
            m_bIsVideo = true;
            if (cSurface != null) {
                m_cSurface = cSurface;
                m_bNeedRender = true;
            }

        } else if (cMediaType.startsWith("audio/")) {
            m_bIsVideo = false;

        } else {
            Log.e(TAG, "MediaType : "+cMediaType +" not support!");
            return 1;
        }

        m_cMediaType = cMediaType;

        m_cDataQueue = new CDataQueue<>();
        m_cDataQueue.create(QUEUE_SIZE,"mcDecQue");

        m_bNeedRawQueue = bNeedRawQue;
        if (m_bNeedRawQueue) {
            m_cRawQueue = new CDataQueue<>();
            m_cRawQueue.create(QUEUE_SIZE,"mcRawDataQue");
        }

        m_bIsConfig = false;
        m_bDecStart = false;
        m_bIsThreadStop = false;

        m_bSetInputEos = false;
        m_bIsOutputEos = false;

        // 创建解码器
        createDecoder();

        // 创建解码线程
        m_cDecThread = new Thread(decRecv);
        m_cDecThread.start();

        m_bIsCreate = true;

        return 0;
    }

    // 传入需解码的数据
    public int setData(CRawFrame cFrame){
        if (!m_bIsCreate) {
            return 1;
        }

        m_cDataQueue.setData(cFrame);

        return 0;
    }

    // 获取已解码的数据
    public byte[] getPcm(){
        if (!m_bIsCreate) {
            return null;
        }

        if (!m_bNeedRawQueue) {
            return null;
        }

        Log.d(TAG, "getPcm Output EOS: "+m_bIsOutputEos);

        if (m_bIsOutputEos) {
            if (m_cRawQueue.isEmpty()) {
                return null;
            }
        }

        return m_cRawQueue.getData();
    }

    // 通用模式
    public void setMediaFormat(MediaFormat cMediaFormat) {
        m_cMediaFormat = cMediaFormat;
    }


    // 如果setMediaFormat已设置无需调用下面三个函数
    public void setCsd(byte [] sCsd) {
        m_sCsd = new byte[sCsd.length];
        System.arraycopy(sCsd, 0, m_sCsd, 0, sCsd.length);
    }

    // 针对H264
    public void createVideoFormat(int decWidth, int decHeight) {
        if (m_cMediaFormat == null) {
            m_cMediaFormat = MediaFormat.createVideoFormat(m_cMediaType, decWidth, decHeight);
            m_cMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            if (m_sCsd != null) {
                m_cMediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(m_sCsd));
            }
        }
    }

    // 针对AAC
    public void createAudioFormat(int sampleRate, int channel) {
        if (m_cMediaFormat == null) {
            m_cMediaFormat = MediaFormat.createAudioFormat(m_cMediaType, sampleRate, channel);
            m_cMediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
            if (m_sCsd != null) {
                m_cMediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(m_sCsd));
            }
        }
    }

    public int configure(){
        Log.d(TAG,"configure, start dec: "+m_bDecStart);

        if (!m_bIsCreate) {
            return 1;
        }

        if (m_bDecStart) {
            return 0;
        }

        configureDecoder();

        m_bDecStart = true;
        m_bIsThreadStop = false;
        m_bIsOutputEos = false;

        return 0;
    }

    public boolean getOutputEos() {
        return m_bIsOutputEos;
    }


    public void release(){
        Log.d(TAG,"release! ");

        m_bIsCreate = false;

        if (m_cDataQueue != null) {
            m_cDataQueue.quit();
        }

        if (m_cRawQueue != null) {
            m_cRawQueue.quit();
        }

        m_bDecStart = false;
        m_bIsThreadStop = true;

        // 线程
        if (m_cDecThread != null) {
            try {
                m_cDecThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cDecThread = null;
        }

        releaseDecoder();

        m_bIsCreate = false;
    }

    public void destroy() {
        if (m_cDataQueue != null) {
            m_cDataQueue.clear();
            m_cDataQueue = null;
        }

        if (m_cRawQueue != null) {
            m_cRawQueue.clear();
            m_cRawQueue = null;
        }
    }

    private void releaseDecoder() {
        if (m_cMediaCodec != null) {
            m_cMediaCodec.stop();
            m_cMediaCodec.release();
            m_cMediaCodec = null;
        }
    }

    private int createDecoder() {

        releaseDecoder();

        Log.d(TAG,"createDecoder : "+m_cMediaType);

        try {
            m_cMediaCodec = MediaCodec.createDecoderByType(m_cMediaType);
        }catch (Exception e){
            Log.e(TAG,"createDecoderByType error");
            return 2;
        }
        return 0;
    }


    private int configureDecoder() {
        Log.d(TAG,"configureDecoder : "+m_bIsConfig);

        if (m_cMediaCodec == null) {
            Log.e(TAG,"m_cMediaCodec instance  null");
            return 1;
        }

        if (m_bIsConfig) {
            return 0;
        }

        m_cMediaCodec.configure(m_cMediaFormat, m_cSurface, null, 0);

        m_bIsConfig = true;

        m_cMediaCodec.start();

        return 0;
    }

    private int inputDecFrame(CRawFrame sData, boolean bIsEos, long timeout) {

        int length = 0;
        int flags = 0;
        long timeUs = 0;

        if (!bIsEos) {
            length = sData.m_sFrame.length;
            timeUs = sData.presentationTimeUs;
        } else {
            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        }

        // 向MC传入解码数据
        ByteBuffer inputBuffer = null;
        int nInputId = m_cMediaCodec.dequeueInputBuffer(timeout);
        Log.d(TAG, "inputDecFrame nInputId：" + nInputId);
        if(nInputId >= 0){
            Log.d(TAG, "inputDecFrame InputCount: " + m_nInFrameCount + "，nInputId： " + nInputId);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                inputBuffer = m_cMediaCodec.getInputBuffer(nInputId);
            } else {
                inputBuffer = m_cInputBuffers[nInputId];
            }

            inputBuffer.clear();

            if (!bIsEos) {
                inputBuffer.put(sData.m_sFrame);
            } else {
                m_bSetInputEos = true;
                Log.d(TAG, "inputDecFrame EOS!");
            }

            m_cMediaCodec.queueInputBuffer(nInputId, 0, length, timeUs, flags);

            m_nInFrameCount++;
        }

        Log.d(TAG, " inputDecFrame end!");
        return 0;

    }

    private void outputRawFrame(long timeout) {
        // 获取解码值
        MediaCodec.BufferInfo cBufferInfo = new MediaCodec.BufferInfo();
        int nOutputId  = m_cMediaCodec.dequeueOutputBuffer(cBufferInfo, timeout);
        if (nOutputId == MediaCodec.INFO_TRY_AGAIN_LATER) {//TIMEOUT
            Log.d(TAG, "outputRawFrame INFO_TRY_AGAIN_LATER");//TODO how to declare this info
            return ;
        } else if (nOutputId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.d(TAG, "outputRawFrame format changed");
            return ;
        } else if (nOutputId < 0) {
            Log.d(TAG, "outputRawFrame outputIndex=" + nOutputId);
            return ;
        } else {

            ByteBuffer outputBuffer = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                outputBuffer = m_cMediaCodec.getOutputBuffer(nOutputId);
            } else {
                outputBuffer = m_cOutputBuffers[nOutputId];
            }

            // 音频数据
            if (m_bNeedRawQueue && (cBufferInfo.size > 0)) {

                Log.d(TAG, "outputRawFrame output (buffer) size: !"+cBufferInfo.size);

                byte[] sOutData = new byte[cBufferInfo.size];
                outputBuffer.get(sOutData);
                //System.arraycopy(cBufferInfo., 0, sOutData, 0, sOutData.length);
                if (m_cRawQueue != null) {
                    m_cRawQueue.setData(sOutData); // 编码数据放入队列
                }

            }

            m_cMediaCodec.releaseOutputBuffer(nOutputId, m_bNeedRender);

            if (cBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM){
                m_bIsOutputEos  = true;
                Log.d(TAG, "outputRawFrame (buffer) Finish !");
            }
            //nOutputBufferIndex = m_cMediaCodec.dequeueOutputBuffer(cBufferInfo, 0);
        }

    }

    // 解码
    private Runnable decRecv = new Thread(new Runnable() {
        @Override
        public void run() {
            boolean bIsEos = false;
            while(true){
                if(m_bDecStart){
                    CRawFrame sData = null;

                    sData = m_cDataQueue.getData();
                    if (sData != null) {
                        bIsEos = sData.m_bIsEos;

                        Log.d(TAG, "decRecv Frame EOS: "+bIsEos);

                        inputDecFrame(sData, sData.m_bIsEos,10);
                    }


                    if (!bIsEos) {
                        outputRawFrame(10);

                    }  else {

                        Log.d(TAG, "decRecv Frame EOS!");

                        while (!m_bIsOutputEos) {

                            if (!m_bSetInputEos) {
                                inputDecFrame(null,true,100);
                            }
                            outputRawFrame(100);
                        }

                        m_bIsThreadStop = true;

                    }

                    if (m_bIsThreadStop) {
                        Log.d(TAG, "decRecv Thread Stop!");
                        break;
                    }

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }else{
                    if (m_bIsThreadStop) {
                        Log.d(TAG, "decRecv Thread Stop!");
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
