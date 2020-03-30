package com.shgit.mediasdk.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CRawFrame;

import java.nio.ByteBuffer;

//此处针对AAC
public class CMCAudDec {
    private final String TAG  = "CMCAudDec";
    private final static int TIME_INTERNAL = 30;
    private final int    QUEUE_SIZE = 10;
    private String m_cMediaType = null;

    private MediaCodec m_cMediaCodec = null;
    private MediaFormat m_cMediaFormat = null;
    private Surface m_cSurface = null;

    // 存储需解码的音视频数据
    private CDataQueue<CRawFrame> m_cDecDataQueue = null;
    // 存储已解码数据
    private CDataQueue<CRawFrame> m_cPcmDataQueue = null;

    // 解码线程
    private Thread  m_cDecThread = null;

    // 解码器
    private CMCDecWrapper m_cMCDec = null;
    private int m_nChannel = 2;
    private int m_nSampleRate = 44100;

    private int          m_nOnFrameCount = 0;
    private byte[]       m_sCsd = null; // sps and pps
    private boolean      m_bIsConfig = false;
    private boolean      m_bIsVideo = false;
    private boolean      m_bDecStart = false;
    private boolean      m_bIsCreate = false;
    private boolean      m_bIsThreadStop = false;


    public int create(){

        Log.d(TAG,"create!");

        if (m_bIsCreate) {
            return 0;
        }

        m_cDecDataQueue = new CDataQueue<>();
        m_cDecDataQueue.create(QUEUE_SIZE,"mcDecDataQue");

        m_cPcmDataQueue = new CDataQueue<>();
        m_cPcmDataQueue.create(QUEUE_SIZE,"mcRawDataQue");

        m_bIsConfig = false;
        m_bDecStart = false;
        m_bIsThreadStop = false;

        // 创建解码器
        m_cMCDec = new CMCDecWrapper();
        m_cMCDec.create(null, MediaFormat.MIMETYPE_AUDIO_AAC,true);

        // 创建解码数据接收线程
        m_cDecThread = new Thread(pcmRecv);
        m_cDecThread.start();

        m_bIsCreate = true;

        return 0;
    }

    // 传入需解码的数据
    public void setAudData(CRawFrame data){
        if (m_cMCDec != null) {
            Log.d(TAG,"setAudData!");
            m_cMCDec.setData(data);
        }
    }

    // 获取已解码的数据
    public CRawFrame getPcm(){
        if (m_cPcmDataQueue != null) {
            Log.d(TAG," AAC getPcm CRawFrame!");
            return m_cPcmDataQueue.getData();
        }

        return null;
    }

    // 通用模式
    public void setMediaFormat(MediaFormat cMediaFormat) {
        m_cMediaFormat = cMediaFormat;
        if (m_cMCDec != null) {
            m_cMCDec.setMediaFormat(cMediaFormat);
        }

        m_nChannel = m_cMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        m_nSampleRate = m_cMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    }

    // 如果setMediaFormat已设置无需调用下面函数
    public void setDecCsd(byte [] sCsd) {
        m_sCsd = new byte[sCsd.length];
        System.arraycopy(sCsd, 0, m_sCsd, 0, sCsd.length);
    }

    // 针对AAC
    public void createAudioFormat(int sampleRate, int channel) {
        m_nChannel = channel;
        m_nSampleRate = sampleRate;
        if (m_cMediaFormat == null) {
            m_cMediaFormat = MediaFormat.createAudioFormat(m_cMediaType, sampleRate, channel);
            m_cMediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
            if (m_sCsd != null) {
                m_cMediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(m_sCsd));
            }
        }
    }

    public int start(){
        Log.d(TAG,"start : "+m_bDecStart);

        if (!m_bIsCreate) {
            return 1;
        }

        if (m_bDecStart) {
            return 0;
        }

        m_bDecStart = true;
        m_bIsThreadStop = false;

        if (m_cMCDec != null) {
            m_cMCDec.configure();
        }

        return 0;
    }


    public void stop(){
        Log.d(TAG,"stop : "+m_bDecStart);

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

        if (m_cMCDec != null) {
            m_cMCDec.release();
        }

        m_bIsCreate = false;
    }

    // 解码数据接收
    private Runnable pcmRecv = new Thread(new Runnable() {
        @Override
        public void run() {
            int delay = 10;
            while(true){
                if(m_bDecStart){
                    byte [] sData = null;

                    CRawFrame cData = new CRawFrame();
                    cData.m_bIsEos = false;

                    sData = m_cMCDec.getPcm();
                    if(sData != null){
                        cData.m_sFrame = new byte[sData.length];

                        Log.d(TAG,"AAC Decoder getPcm !" + sData.length);

                        System.arraycopy(sData, 0, cData.m_sFrame, 0, sData.length);
                        m_cPcmDataQueue.setData(cData);
                        int len = sData.length;
                        delay = 1000 * (len / 2 / m_nChannel) / m_nSampleRate;
                    }else {
                        cData.m_bIsEos = true;
                        m_cPcmDataQueue.setData(cData);
                        Log.d(TAG, "AAC Decoder frame Thread Stop=Eos!");
                        break;
                    }

                    try {
                        Log.d(TAG, "AAC Decoder frame Thread Delay!"+delay);
                        if (delay <= 0) {
                            delay = 5;
                        }
                        Thread.sleep(delay);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else{
                    if (m_bIsThreadStop) {
                        Log.d(TAG, "Dec frame Thread Stop!");
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
