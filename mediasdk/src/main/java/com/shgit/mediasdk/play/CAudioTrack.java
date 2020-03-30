package com.shgit.mediasdk.play;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class CAudioTrack {
    private final String TAG = "AUDPLAY";
    private int m_nSampleRate    = 44100;
    private int m_nChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private int m_nAudioFormat   = AudioFormat.ENCODING_PCM_16BIT;
    private int m_nStreamType    = AudioManager.STREAM_MUSIC;
    private int m_nMode          = AudioTrack.MODE_STREAM;
    private int m_nMinBufferSize = 0;
    private AudioTrack m_cAudioTrack = null;
    private AudioTrackThread m_cAudioTrackThread = null;
    private int m_nIsPlaying = 0;
    private boolean m_bIsEos = false;

    public int startAudioTrack() {

        int nRet = 0;

        nRet = createAudioTrack();
        if (nRet != 0) {
            return nRet;
        }

        play();

        return nRet;
    }


    public void stopAudioTrack() {
        release();
    }

    private void play() {
        Log.v(TAG, " AudioTrack play ==:");
        if (m_cAudioTrackThread == null) {
            Log.v(TAG, " AudioTrack play:");
            m_cAudioTrackThread = new AudioTrackThread();
            m_cAudioTrackThread.start();

            while (m_cAudioTrackThread.m_cAudioPlayHandler == null) {
                try {
                    Log.v(TAG, " AudioTrack play sleep");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.v(TAG, " AudioTrack play sucess:");
    }

    public boolean isTrackEos() {
        return m_bIsEos;
    }

    public void pause(int pause) {
        m_nIsPlaying = pause;
    }

    public void setAudioTrackParam(int channelConfig, int sampleRate, int audioFormat){
        m_nSampleRate    = sampleRate;
        m_nChannelConfig = channelConfig;
        m_nAudioFormat   = audioFormat;
    }

    public void setAudioDataSource(byte[] data, int isEos) {
        if (m_cAudioTrackThread.m_cAudioPlayHandler != null) {
            if (m_nIsPlaying == 1) {
                if (data != null) {
                    Log.v(TAG, " AudioTrack setAudioDataSource: length :"+data.length);
                }

                Message message = m_cAudioTrackThread.m_cAudioPlayHandler.obtainMessage();
                message.what = 0x1;
                message.obj = data;
                message.arg1 = isEos;

                m_cAudioTrackThread.m_cAudioPlayHandler.sendMessage(message);
            }
        }
    }

    private void stop() {
        m_nIsPlaying = 0;
        m_cAudioTrackThread = null;
    }


    private void release() {
        stop();
        releaseAudioTrack();
    }


    private int getAudioBufferSize() {
        m_nMinBufferSize = AudioTrack.getMinBufferSize(m_nSampleRate, m_nChannelConfig, m_nAudioFormat);
        if(AudioTrack.ERROR_BAD_VALUE == m_nMinBufferSize || AudioTrack.ERROR == m_nMinBufferSize) {
            Log.e(TAG, "AudioTrack.getMinBufferSize failed.");
            return 1;
        }
        Log.v(TAG, " AudioTrack getAudioBufferSize:"+m_nMinBufferSize);
        return 0;
    }

    private int createAudioTrack() {

        releaseAudioTrack();
        Log.v(TAG, " AudioTrack createAudioTrack:");
        if (getAudioBufferSize() != 0) {
            Log.e(TAG, "getAudioBufferSize failed.");
            return 1;
        }

        if(m_cAudioTrack == null) {

            try {
                m_cAudioTrack = new AudioTrack(m_nStreamType, m_nSampleRate, m_nChannelConfig, m_nAudioFormat,
                        m_nMinBufferSize, m_nMode);
            }catch (IllegalArgumentException e) {
                Log.e(TAG, "AudioTrack:" + e.getMessage());
                return 1;
            }
        }

        if (m_cAudioTrack == null) {
            Log.e(TAG, "create AudioTrack instance error");
            return 1;
        } else if (m_cAudioTrack.getState() != m_cAudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack instance not initialized");

            releaseAudioTrack();

            return 1;
        }

        Log.v(TAG, " AudioTrack createAudioTrack sucess!");
        return 0;
    }

    private void releaseAudioTrack() {
        if(m_cAudioTrack != null) {
            m_cAudioTrack.stop();
            m_cAudioTrack.release();
            m_cAudioTrack = null;
        }
    }

    class AudioTrackThread extends Thread {
        public Handler m_cAudioPlayHandler = null;
        @Override
        public void run() {
            m_cAudioTrack.play();
            Log.v(TAG, " AudioTrack AudioTrackThread:");
            Looper.prepare();// Looper初始化

            m_nIsPlaying = 1;
            //Handler初始化 需要注意, Handler初始化传入Looper对象是子线程中缓存的Looper对象
            m_cAudioPlayHandler = new Handler(Looper.myLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == 0x1) {
                        Log.v(TAG, " AudioTrack AudioTrackThread: write");

                        if (msg.arg1 == 1) {
                            m_bIsEos = true;
                            Log.v(TAG, " AudioTrack AudioTrackThread EOS!");
                            return;
                        }
                        m_cAudioTrack.write((byte[]) msg.obj, 0, ((byte[]) msg.obj).length);
                    }
                }
            };
            //死循环
            Looper.loop();
        }
    }

}
