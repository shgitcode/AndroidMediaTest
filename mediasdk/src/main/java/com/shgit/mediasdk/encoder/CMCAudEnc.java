package com.shgit.mediasdk.encoder;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CFileManage;
import com.shgit.mediasdk.util.CRawFrame;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CMCAudEnc {
    private final String TAG = "CMCAudEnc";
    private final int QUEUE_LENGTH = 10;

    // audio
    private int m_nSampleRate = 44100;
    private int m_nChannel = 2;
    private int m_nBitRate = 96000;
    private int m_nAudioBufferSize = 8192;

    // 原始数据队列
    private CDataQueue<CRawFrame> m_cRawDataQue = null;
    // 已编码数据队列
    private boolean m_bNeedEcodedDataQue = true;
    private CDataQueue<CRawFrame> m_cEcodedDataQue = null;

    // encoder
    private CMCEncWrapper m_cMCEnc = null;
    private boolean m_bEncoding = false;
    private MediaFormat m_cMediaFormat = null;

    // file
    private CFileManage m_cWriteFile = null;

    // executor
    private ExecutorService m_cSndThread = null;
    private Thread m_cEncThread = null;
    private boolean m_bEncStop = false;

    //ffprobe -v quiet -print_format json -show_format -show_streams audEnc.aac
    public CMCAudEnc(boolean bNeedEcodedQue) {

        m_cRawDataQue    =  new CDataQueue<>();
        m_bNeedEcodedDataQue = bNeedEcodedQue;
        if (m_bNeedEcodedDataQue) {
            m_cEcodedDataQue =  new CDataQueue<>();
        }

        m_bEncoding = false;
        m_bEncStop = false;
    }

    public int createMCEncoder(){
        int nRetVal = 0;

        Log.d(TAG, "createAudMCEncoder !");

        m_cMCEnc = new CMCEncWrapper(false);

        m_cRawDataQue.create(QUEUE_LENGTH,"audRawDataQue");
        if (m_cEcodedDataQue != null) {
            m_cEcodedDataQue.create(QUEUE_LENGTH,"audEncDataQue");
        }

        nRetVal = m_cMCEnc.create(MediaFormat.MIMETYPE_AUDIO_AAC);

        createAudioFormat();

        // thread
        m_cEncThread = new Thread(encRawData);
        m_cEncThread.start();

        m_cSndThread = Executors.newSingleThreadExecutor();
        m_cSndThread.submit(SndEncData);

        // file
        m_cWriteFile = new CFileManage();
        m_cWriteFile.createSavedFile("audEnc.aac");

        return nRetVal;
    }

    public void startMCEncoder() {
        m_cMCEnc.start();
        m_bEncoding = true;
        m_bEncStop = false;
    }

    public boolean isAudEncStop(){
        return m_bEncStop;
    }

    public void stopMCEncoder(){

        // 等待编码结束
        while (!m_bEncStop) {
            try {
                Thread.sleep(5);
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

        // 编码器
        if (m_cMCEnc != null) {
            m_cMCEnc.stop();
            m_cMCEnc = null;
        }

        // file
        if (m_cWriteFile != null) {
            m_cWriteFile.closeSavedFile();
        }

        m_bEncoding = false;
    }

    public void setRawData(CRawFrame sRawData){
        Log.d(TAG, "AudMCEncoder setRawData!");
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

    private void createAudioFormat(){
        Log.d(TAG, "createAudioFormat !");
        if (m_cMediaFormat == null) {
            m_cMediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, m_nSampleRate, m_nChannel);
            int bitRate = m_nSampleRate * 2 * m_nChannel;
            m_cMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
            //m_cMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, m_nBitRate);//比特率
            m_cMediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            m_cMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, m_nAudioBufferSize);

            m_cMediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, m_nChannel);
            m_cMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, m_nSampleRate);
        }

        if(m_cMCEnc != null) {
            m_cMCEnc.configure(m_cMediaFormat);
        }

    }

    // 编码线程
    Runnable encRawData = new Thread(new Runnable() {
        @Override
        public void run() {
            int isEos = 0;
            int delay = 10;
            int totalDealy = 0;
            int frameNum = 0;

            CRawFrame cRawData = null;

            Log.d(TAG, "AudMCEncoder Flag : " + m_bEncoding);

            while (true) {

                long startTime = System.nanoTime() / 1000;

                if(m_bEncoding){

                    cRawData = m_cRawDataQue.getData();

                    if(cRawData != null){
                        delay = 0;
                        if (cRawData.m_bIsEos) {
                            isEos = 1;
                        } else {
                            int len = cRawData.m_sFrame.length; // 7104
                            delay = 1000 * (len / 2 / m_nChannel) / m_nSampleRate; // 40
                            totalDealy += delay;
                        }

                        frameNum++;
                        Log.d(TAG, "AudMCEncoder encode frameNum: "+frameNum+" delay : " + delay);

                        if (m_cMCEnc != null) {
                            m_cMCEnc.setRawData(cRawData);
                        }
                    }

                    if (isEos == 1) {// 数据全部取出
                        Log.d(TAG, "AudMCEncoder encode frameNum: "+frameNum+" encoder EOS! ");
                        break;
                    }

                    try {
                        if (delay <= 0){
                            delay = 5;
                        }

                        Thread.sleep(delay);
                        Log.d(TAG, "AudMCEncoder sleep: " + delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Log.d(TAG, "AudMCEncoder total cost: "+((System.nanoTime() / 1000) - startTime));
                }else{
                    try {
                        Thread.sleep(100);
//                        Log.d(TAG, "m_bStartEncFlag; " + m_bStartEncFlag);
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

            Log.d(TAG, "Enc Flag : " + m_bEncoding);

            while (true) {

                byte[] cAudData = null;

                if(m_bEncoding){

                    if (m_cMCEnc != null) {

                        if (m_cMCEnc.getOutputEos()) {
                            // 编码结束
                            if (m_cMCEnc.encodedDataQueIsEmpty()) {
                                bIsEos = true;
                            }
                        }

                        if (!bIsEos) {
                            cAudData = m_cMCEnc.getEncodedData();
                        } else {
                            if (m_cEcodedDataQue != null) {
                                CRawFrame cPcmData = new CRawFrame();
                                cPcmData.m_bIsEos = true;
                                m_cEcodedDataQue.setData(cPcmData);
                            }
                            Log.d(TAG, "AAC encoder EOS! ");
                        }

                    }

                    if(cAudData != null){
                        Log.d(TAG, "AAC encoder data size:"+cAudData.length);

                        convertToAACAndSave(cAudData);

                        int len = cAudData.length;
                        delay = 1000 * (len / 2 / m_nChannel) / m_nSampleRate;
                        Log.d(TAG, "AAC encoder send delay: " + delay);
                    }

                    if (bIsEos) {
                        if (m_cWriteFile != null) {
                            m_cWriteFile.closeSavedFile();
                        }
                        m_bEncStop = true;
                        Log.d(TAG, "AAC encoder end! ");
                        break;
                    }

                    try {
                        if (delay <= 0) {
                            delay = 5;
                        }
                        Thread.sleep(delay);
                        Log.d(TAG, "AAC encoder delay; " + delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }else{
                    try {
                        Thread.sleep(10);
//                        Log.d(TAG, "m_bStartEncFlag; " + m_bStartEncFlag);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    });

    private void addADTStoPacket(int sampleRateType, byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int chanCfg = 2; // CPE

        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (sampleRateType << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    // ffprobe -v quiet -print_format json -show_format -show_streams -show_frames audEnc11.aac
    // ffmpeg -f s16le -ar 44100 -ac 2 -i audio.pcm out.aac
    private void convertToAACAndSave(byte[] bitData){

        byte[] cAudData;
        int outPacketSize;

        outPacketSize = bitData.length + 7;//7为ADTS头部的大小
        Log.d(TAG, "AAC encoder send size: " + outPacketSize);
        cAudData = new byte[outPacketSize];
        // 44100 --- sampleRateType:4
        addADTStoPacket(4, cAudData, outPacketSize);//添加ADTS
        System.arraycopy(bitData, 0, cAudData, 7, bitData.length);

        // 编码后数据存文件，暂不存队列
        if (m_cWriteFile != null) {
            m_cWriteFile.writeSavedFile(cAudData, cAudData.length);
        }

        if (m_cEcodedDataQue != null) {
            CRawFrame cPcmData = new CRawFrame();
            cPcmData.m_sFrame = cAudData;
            cPcmData.m_bIsEos = false;
            m_cEcodedDataQue.setData(cPcmData);
        }

    }
}
