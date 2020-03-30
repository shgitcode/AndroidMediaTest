package com.shgit.mediasdk.decoder;

import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CRawFrame;

import java.nio.ByteBuffer;

public class CMCVidDec {
    private final String TAG  = "CMCVidDec";
    private String m_cMediaType = null;

    private MediaFormat m_cMediaFormat = null;

    // 存储需解码的音视频数据
    private CDataQueue<CRawFrame> m_cDecDataQueue = null;

    // 解码器
    private CMCDecWrapper m_cMCDec = null;

    private byte[]       m_sCsd = null; // sps and pps

    private boolean      m_bDecStart = false;
    private boolean      m_bIsCreate = false;

    // 属性
    private int m_nFrameRate = 30;


    public int create(Surface cSurface, String cMediaType){

        Log.d(TAG,"create!");

        if (m_bIsCreate) {
            return 0;
        }

        m_bDecStart = false;

        m_cMediaType = cMediaType;

        // 创建解码器
        m_cMCDec = new CMCDecWrapper();
        m_cMCDec.create(cSurface, cMediaType, false);

        m_bIsCreate = true;

        return 0;
    }

    // 传入需解码的数据
    public void setData(CRawFrame data){
        if (m_cMCDec != null) {
            Log.d(TAG,"setData!");
            int delay = 1000/m_nFrameRate;
            Log.d(TAG,"setData delay: "+delay);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cMCDec.setData(data);
        }
    }

    public boolean getOutputEos() {
        if (m_cMCDec != null) {
            Log.d(TAG,"getOutputEos!");
            return m_cMCDec.getOutputEos();
        }
        return false;
    }

    // 通用模式
    public void setMediaFormat(MediaFormat cMediaFormat) {
        Log.d(TAG,"setMediaFormat!");
        m_cMediaFormat = cMediaFormat;

        if (m_cMediaFormat != null) {

            if (m_cMediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                m_nFrameRate = m_cMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            } else {
                m_nFrameRate = 30;
            }
            Log.d(TAG,"setMediaFormat frameRate: "+m_nFrameRate);
        }

        if (m_cMCDec != null) {
            m_cMCDec.setMediaFormat(cMediaFormat);
        }

    }

    // 如果setMediaFormat已设置无需调用下面函数
    public void setDecCsd(byte [] sCsd) {
        Log.d(TAG,"setDecCsd!");
        m_sCsd = new byte[sCsd.length];
        System.arraycopy(sCsd, 0, m_sCsd, 0, sCsd.length);
    }

    // 针对H264
    public void createMediaFormat(int decWidth, int decHeight) {
        Log.d(TAG,"createMediaFormat!");
        if (m_cMediaFormat == null) {
            m_cMediaFormat = MediaFormat.createVideoFormat(m_cMediaType, decWidth, decHeight);
            m_cMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            if (m_sCsd != null) {
                m_cMediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(m_sCsd));
            }
        }
    }

    public int start(){
        Log.d(TAG,"start!");

        if (!m_bIsCreate) {
            return 1;
        }

        if (m_bDecStart) {
            return 0;
        }

        m_bDecStart = true;

        if (m_cMCDec != null) {
            m_cMCDec.configure();
        }

        return 0;
    }


    public void stop(){
        Log.d(TAG,"stop!");

        if (m_cMCDec != null) {
            m_cMCDec.release();
        }
        m_bDecStart = false;
        m_bIsCreate = false;
    }

}
