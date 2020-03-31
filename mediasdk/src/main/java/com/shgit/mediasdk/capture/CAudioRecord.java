package com.shgit.mediasdk.capture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CFileManage;
import com.shgit.mediasdk.util.CRawFrame;


/**
 *  音频采集：
 *  此处为了适应AAC编码，采样大小适配为AAC一帧
 */
public class CAudioRecord {
    private final String TAG = "CAudioRecord";

    private final int QUEUE_LENGTH = 10;
    private final int FRAMES_PER_BUFFER = 24;

    private int m_nAudioSource = MediaRecorder.AudioSource.MIC;

    // 这些参数要设置
    private int m_nSampleRate  = 44100;
    private int m_nChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private int m_nAudioFormat  = AudioFormat.ENCODING_PCM_16BIT;
    // 缓存数
    private int m_nBufferSizeInBytes = 0;
    private int m_nMaxSizeInBytes = 0;
    private int m_nSamplesPerFrame = 1024 * 2 * m_nAudioFormat;

    private AudioRecord m_cAudRecord = null;
    // 线程
    private boolean m_bIsRecording = false;
    private Thread m_cAudRecordThread = null;
    private byte[] m_sRawDataBuffer = null;

    // file
    private CFileManage m_cWritePcmFile = null;

    // 队列
    private boolean m_bNeedDataQue = false;
    // 录制音频数据
    private CDataQueue<CRawFrame> m_cDataQueue = null;

    // bNeedDataQue:用于开启存储PCM数据(用于编码)
    public CAudioRecord(boolean bNeedDataQue) {
        m_bNeedDataQue = bNeedDataQue;
    }

    public int StartAudioRecord(int nSampleRate, int nChannel){
        Log.d(TAG, " StartAudioRecord!");

        initRecord(nSampleRate, nChannel);

        if (m_bNeedDataQue) {
            m_cDataQueue = new CDataQueue<>();
            m_cDataQueue.create(QUEUE_LENGTH,"audRecordQue");
        }

        m_cWritePcmFile = new CFileManage();
        m_cWritePcmFile.createSavedFile("audRecord.pcm");

        // 开启线程
        startRecordThread();

        return 0;
    }

    public int StopAudioRecord() {
        Log.d(TAG, " StopAudioRecord!");

        stopRecordThread();

        stopRecord();

        if (m_cWritePcmFile != null) {
            m_cWritePcmFile.closeSavedFile();
            m_cWritePcmFile = null;
        }

        return 0;
    }

    // 清空队列
    public int clearDataQue() {
        Log.d(TAG, " clearDataQue!");

        if (m_cDataQueue != null) {
            m_cDataQueue.clear();
            m_cDataQueue = null;
        }

        return 0;
    }

    public String getSavedFileName(){
        if (m_cWritePcmFile != null) {
            return m_cWritePcmFile.getSavedFileName();
        }
        return null;
    }

    public CRawFrame getPCMData() {
        if (m_cDataQueue != null) {
            return m_cDataQueue.getData();
        }
        return null;
    }

    public boolean pcmDataQueIsEmpty() {
        if (m_cDataQueue != null) {
            return m_cDataQueue.isEmpty();
        }

        return true;
    }

    public boolean isStop() {
        return !m_bIsRecording;
    }

    private int initRecord(int nSampleRate, int nChannel) {
        if (nSampleRate != 0) {
            m_nSampleRate    = nSampleRate;
        }

        if (nChannel != 0) {
            m_nChannelConfig = nChannel;
        }

        m_nBufferSizeInBytes = AudioRecord.getMinBufferSize(m_nSampleRate,
                m_nChannelConfig,
                m_nAudioFormat);
        if(AudioRecord.ERROR_BAD_VALUE == m_nBufferSizeInBytes ||
                AudioRecord.ERROR == m_nBufferSizeInBytes) {
            Log.e(TAG, "AudioRecord.getMinBufferSize failed, mBufferSizeInBytes = "
                    + m_nBufferSizeInBytes);
            return 1;
        }

        m_nMaxSizeInBytes = m_nSamplesPerFrame * FRAMES_PER_BUFFER;
        Log.d(TAG, "AudioRecord.getMinBufferSize : " + m_nBufferSizeInBytes+" m_nMaxSizeInBytes: "+m_nMaxSizeInBytes);

        if (m_nMaxSizeInBytes < m_nBufferSizeInBytes)
            m_nMaxSizeInBytes = ((m_nBufferSizeInBytes / m_nSamplesPerFrame) + 1) * m_nSamplesPerFrame * 2;

        Log.d(TAG, "AudioRecord.getMinBufferSize : " + m_nBufferSizeInBytes+" m_nMaxSizeInBytes: "+m_nMaxSizeInBytes+" m_nSamplesPerFrame:"+m_nSamplesPerFrame);

        if(m_cAudRecord == null) {
            int bufferSizeInBytes = m_nMaxSizeInBytes;
            try {
                m_cAudRecord = new AudioRecord(m_nAudioSource,
                        m_nSampleRate,
                        m_nChannelConfig,
                        m_nAudioFormat,
                        bufferSizeInBytes);
            }catch (IllegalArgumentException e) {
                Log.e(TAG, "AudioRecord:" + e.getMessage());
                return 1;
            }
            if (m_cAudRecord == null || m_cAudRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "failed to create AudioRecord instance");
                stopRecord();
                return 1;
            }
        }

        m_sRawDataBuffer = new byte[m_nSamplesPerFrame];
        Log.d(TAG, "AudioRecord create sucess!");
        return 0;
    }

    private void  startRecordThread() {
        m_bIsRecording = true;
        if(m_cAudRecordThread == null) {
            m_cAudRecordThread = new Thread(audioRecord);
            m_cAudRecordThread.start();
        }
    }

    private void  stopRecord() {
        m_bIsRecording = false;
        if(m_cAudRecord != null) {
            m_cAudRecord.stop();
            m_cAudRecord.release();
            m_cAudRecord = null;
        }
    }

    private void stopRecordThread() {
        m_bIsRecording = false;
        try{
            if(m_cAudRecordThread != null) {
                m_cAudRecordThread.join();
                m_cAudRecordThread = null;
            }
        }catch(Throwable t) {

        }
    }

    private Runnable  audioRecord = new Runnable() {
        int frameNum = 0;
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            if(m_cAudRecord != null) {
                m_cAudRecord.startRecording();
                if(m_cAudRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "getRecordingState:" + m_cAudRecord.getRecordingState());
                    return;
                }
            }

            Log.d(TAG, "AudioRecord.startRecording! ");

            while(m_bIsRecording) {
                int numbers = m_cAudRecord.read(m_sRawDataBuffer, 0, m_nSamplesPerFrame);
                if (numbers == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AudioRecord failed:" + numbers);
                    m_bIsRecording = false;
                    break;
                }

                frameNum++;
                Log.d(TAG, "AudioRecord audio record frameNum: "+frameNum+" size:" + numbers);

                byte[] audio = new byte[numbers];
                System.arraycopy(m_sRawDataBuffer, 0, audio, 0, numbers);

                // PCM数据放入队列，等待编码
                if (m_cDataQueue != null) {
                    CRawFrame cPcmData = new CRawFrame();
                    cPcmData.m_sFrame = audio;
                    cPcmData.m_bIsEos = false;
                    cPcmData.presentationTimeUs = System.nanoTime() / 1000;
                    m_cDataQueue.setData(cPcmData);
                }

                if (m_cWritePcmFile != null) {
                    m_cWritePcmFile.writeSavedFile(audio, audio.length);
                }
            }

            // 结束，插入EOS
            if (!m_bIsRecording) {
                if (m_cDataQueue != null) {
                    CRawFrame cPcmData = new CRawFrame();
                    cPcmData.m_bIsEos = true;
                    cPcmData.presentationTimeUs = System.nanoTime() / 1000;
                    frameNum++;
                    Log.d(TAG, "AudioRecord audio FrameNum: "+frameNum+" EOS!");
                    m_cDataQueue.setData(cPcmData);
                }
            }
            Log.d(TAG, "AudioRecord.endRecording! ");
        }
    };
}
