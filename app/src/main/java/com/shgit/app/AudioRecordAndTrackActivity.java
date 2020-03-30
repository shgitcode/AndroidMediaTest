package com.shgit.app;

import android.app.Service;
import android.media.AudioManager;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.shgit.mediasdk.capture.CAudioRecord;
import com.shgit.mediasdk.decoder.CMCAudDec;
import com.shgit.mediasdk.encoder.CMCAudEnc;
import com.shgit.mediasdk.play.CAudioTrack;
import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CFileManage;
import com.shgit.mediasdk.util.CMediaExtractor;
import com.shgit.mediasdk.util.CRawFrame;

import androidx.appcompat.app.AppCompatActivity;

public class AudioRecordAndTrackActivity extends AppCompatActivity {
    private final String TAG = "AudioRecordTrack";
    private final int QUEUE_LENGTH = 10;

    private final int HANDLE_READ_FROM_PCM_FILE = 1;
    private final int HANDLE_READ_FROM_AAC_FILE = 2;

    private Button m_cCapPcmAudButton;
    private Button m_cCapPcmEncAudButton;
    private Button m_cPlyRawAudButton;
    private Button m_cPlyDecAudButton;

    // 存储需播放的PCM
    private CDataQueue<CRawFrame> m_cPlyPcmQue = null;
    // 播放采用Thread
    private Thread m_cPlyRawAudThread = null;

    // control
    private boolean m_bIsPlyRawAudBut = false;
    private boolean m_bIsPlyRawAud = false;
    private boolean m_bIsReadPcmAud = false;

    private boolean m_bIsPlyDecAudBut = false;
    private boolean m_bIsReadDecAud = false;
    private boolean m_bIsDecAudio = false;

    private boolean m_bIsCapPcmAudBut = false;

    private boolean m_bIsCapPcmEncAudBut = false;
    private boolean m_bIsSndCapToEnc = false;
    // 主动停止
    private boolean m_bIsStopPlyRawAud = false;
    private boolean m_bIsStopPlyDecAud = false;

    // 采集与播放实例
    private CAudioRecord m_cAudRecord = null;
    private CAudioTrack m_cAudPly = null;
    private int      m_nHandlerEvent = 0;

    // 播放读PCM文件
    // 从文件读取数据填入m_cPlyPcmQue队列
    private Thread m_cReadRawAudThread = null;
    private CFileManage m_cReadPcmFile = null;
    private byte[] m_sAudBuf = null;

    // 编码
    private CMCAudEnc m_cAudEnc = null;
    private Thread m_cAudEncThread = null;
    private String m_cEncFileName = null;

    // 解码
    private CMCAudDec m_cAudDec = null;
    private CMediaExtractor m_cAudExtractor = null;

    // 提取音频进行解码
    private Thread m_cAudDecThread = null;
    // 解码数据存入m_cPlyPcmQue队列
    private Thread m_cReadDecAudThread = null;

    // 从PCM文件读取pcm，并存入m_cPlyPcmQue队列
    private Thread m_cAudReadPcmThread = null;
    private String m_cPcmFileName = null;

    // Handler
    private Handler m_cEventHandler = null;
    private HandlerThread m_cHandlerThread = null;

    // 音频管理
    private AudioManager m_cAudManager = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Enter onCreate");

        setContentView(R.layout.activity_audio_record_track);

        m_cPlyRawAudButton = findViewById(R.id.bRawAudPly);
        m_cCapPcmAudButton = findViewById(R.id.bAudCap);
        m_cCapPcmEncAudButton = findViewById(R.id.bAudCapEnc);
        m_cPlyDecAudButton = findViewById(R.id.bDecAudPly);

        // audioManager
        m_cAudManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);


        // handler
        m_cHandlerThread = new HandlerThread("handlerThread");
        m_cHandlerThread.start();

        m_cEventHandler = new Handler(m_cHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "handleMessage : "+msg.what);
                switch(msg.what){
                    // 来自PCM文件
                    case HANDLE_READ_FROM_PCM_FILE:
                        try {
                            //延时操作
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        stopPlayRawAud();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                m_cPlyRawAudButton.setText("startPlyPcm");
                            }
                        });

                        break;

                    // 来自DEC文件
                    case HANDLE_READ_FROM_AAC_FILE:
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        stopPlayDecAud();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                m_cPlyDecAudButton.setText("startPlyDec");
                            }
                        });

                        break;
                    default:
                        break;
                }
            }
        };
    }


    @Override
    protected  void onDestroy(){
        super.onDestroy();

        Log.d(TAG, "Enter onDestroy");

        if (m_cHandlerThread != null) {
            m_cHandlerThread.quit();
            m_cHandlerThread = null;
        }

    }

    // 音频采集
    public  void captureAudio(View view) {
        if (m_bIsCapPcmAudBut) {
            stopAudCap();
            m_cCapPcmAudButton.setText("startCap");
        } else {
            startAudCap();
        }
    }

    // 音频采集编码
    public  void captureEncAudio(View view) {
        if (m_bIsCapPcmEncAudBut) {
            stopEncAudCap();
            m_cCapPcmEncAudButton.setText("startCapEnc");
        } else {
            startEncAudCap();
        }
    }

    // 播放直接采集的PCM
    public void playRawAudio(View view) {

        if (m_bIsPlyRawAudBut) {
            stopPlayRawAud();
            m_cPlyRawAudButton.setText("startPlyRaw");
        } else {
            startPlayRawAud();
        }

    }

    // 播放已编码的音频
    public void playDecAudio(View view) {

        if (m_bIsPlyDecAudBut) {
            stopPlayDecAud();
            m_cPlyDecAudButton.setText("startPlyDec");
        } else {
            startPlayDecAud();
        }

    }

    public void adjustVolumeRaise(View view) {
        // 提高音量
        if (m_cAudManager != null){
            // FLAG_SHOW_UI 显示音量图像
            m_cAudManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        }

    }

    public void adjustVolumeLower(View view) {
        // 降低音量
        if (m_cAudManager != null){
            m_cAudManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        }

    }


    // 播放来自文件的PCM
    private void startPlayRawAud(){
        m_cPlyRawAudButton.setText("stopPlyRaw");

        stopPlayRawAud();

        m_bIsPlyRawAudBut = true;
        m_bIsPlyRawAud = true;
        m_bIsReadPcmAud = true;
        m_bIsStopPlyRawAud = false;
        m_bIsStopPlyDecAud = false;

        m_nHandlerEvent = HANDLE_READ_FROM_PCM_FILE;

        if (m_cAudPly == null) {
            m_cAudPly = new CAudioTrack();
            m_cAudPly.startAudioTrack();
        }

        // 队列
        if (m_cPlyPcmQue == null) {
            m_cPlyPcmQue = new CDataQueue<>();
            m_cPlyPcmQue.create(QUEUE_LENGTH, "playPcmQue");
        }

        // 音频播放线程
        m_cPlyRawAudThread = new Thread(playAudio);
        m_cPlyRawAudThread.start();


        // 从PCM文件读取pcm，并存入m_cPlyPcmQue队列
        m_cAudReadPcmThread = new Thread(readPcm);
        m_cAudReadPcmThread.start();

    }

    private void stopPlayRawAud(){

        m_bIsPlyRawAudBut = false;
        m_bIsStopPlyRawAud = true;

        Log.d(TAG, "enter stopPlayRawAud ! ");

        // 线程
        if (m_cAudReadPcmThread != null) {
            try {
                m_cAudReadPcmThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cAudReadPcmThread = null;
        }

        if (m_cPlyRawAudThread != null) {
            try {
                m_cPlyRawAudThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cPlyRawAudThread = null;
        }

        // 队列
        if (m_cPlyPcmQue != null) {
            m_cPlyPcmQue.clear();
            m_cPlyPcmQue = null;
        }

        if (m_cAudPly != null) {
            m_cAudPly.stopAudioTrack();
            m_cAudPly = null;
        }

        m_bIsPlyRawAud = false;
        m_bIsReadPcmAud = false;

        Log.d(TAG, "stopPlayRawAud end! ");
    }

    // 播放来自解码器的PCM
    private void startPlayDecAud() {
        m_cPlyDecAudButton.setText("stopPlyDec");

        stopPlayDecAud();

        m_bIsPlyDecAudBut = true;
        m_bIsPlyRawAud = true;
        m_bIsReadDecAud = true;
        m_bIsDecAudio = true;
        m_bIsStopPlyDecAud = false;
        m_bIsStopPlyRawAud = false;

        Log.d(TAG, "enter startPlayDecAud ! ");

        m_nHandlerEvent = HANDLE_READ_FROM_AAC_FILE;

        if (m_cPlyPcmQue == null) {
            m_cPlyPcmQue = new CDataQueue<>();
            m_cPlyPcmQue.create(QUEUE_LENGTH, "playPcmQue");
        }

        if (m_cAudExtractor == null) {
            // 音频提取器
            m_cAudExtractor = new CMediaExtractor();
        }

        if (m_cAudDec == null) {
            // 音频解码器
            m_cAudDec = new CMCAudDec();
        }

        if (m_cAudPly == null) {
            // 音频播放
            m_cAudPly = new CAudioTrack();
            m_cAudPly.startAudioTrack();
        }

        // 音频播放线程
        m_cPlyRawAudThread = new Thread(playAudio);
        m_cPlyRawAudThread.start();

        // 音频解码线程
        m_cAudDecThread = new Thread(decAudio);
        m_cAudDecThread.start();

        //  从解码器读取pcm，并存入m_cPlyPcmQue队列
        m_cReadDecAudThread = new Thread(ReadDecAud);
        m_cReadDecAudThread.start();

        Log.d(TAG, "enter startPlayDecAud ! ");
    }

    private void stopPlayDecAud() {

        m_bIsPlyDecAudBut = false;
        m_bIsStopPlyDecAud = true;

        Log.d(TAG, "enter stopPlayDecAud ! ");

        if (m_cAudDecThread != null) {
            try {
                m_cAudDecThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cAudDecThread = null;
        }

        if (m_cReadDecAudThread != null) {
            try {
                m_cReadDecAudThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cReadDecAudThread = null;
        }

        if (m_cPlyRawAudThread != null) {
            try {
                m_cPlyRawAudThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cPlyRawAudThread = null;
        }

        // 队列
        if (m_cPlyPcmQue != null) {
            m_cPlyPcmQue.clear();
            m_cPlyPcmQue = null;
        }

        if (m_cAudExtractor != null) {
            m_cAudExtractor.stop();
            m_cAudExtractor = null;
        }

        if (m_cAudDec != null) {
            m_cAudDec.stop();
            m_cAudDec = null;
        }

        if (m_cAudPly != null) {
            m_cAudPly.stopAudioTrack();
            m_cAudPly = null;
        }

        m_bIsPlyRawAud = false;
        m_bIsReadDecAud = false;
        m_bIsDecAudio = false;

        Log.d(TAG, "stopPlayDecAud end ! ");
    }

    // 音频采集
    private void startAudCap() {
        m_cCapPcmAudButton.setText("stopCap");

        stopAudCap();

        m_bIsCapPcmAudBut = true;

        // 音频采集
        if (m_cAudRecord == null) {
            m_cAudRecord = new CAudioRecord(false);
            m_cAudRecord.StartAudioRecord(0, 0);
        }

    }

    private void stopAudCap() {

        // 采集器
        if (m_cAudRecord != null) {
            // 采集文件
            m_cPcmFileName = m_cAudRecord.getSavedFileName();
            Log.d(TAG, "Audio Record Pcm file: "+m_cPcmFileName);
            m_cAudRecord.StopAudioRecord();
            m_cAudRecord.clearDataQue();
            m_cAudRecord = null;
        }

        m_bIsCapPcmAudBut = false;

        Log.d(TAG, "Audio Record End! ");
    }

    // 音频采集编码
    private void startEncAudCap() {
        m_cCapPcmEncAudButton.setText("stopCapEnc");

        stopEncAudCap();

        m_bIsCapPcmEncAudBut = true;
        m_bIsSndCapToEnc = true;

        // 音频采集
        if (m_cAudRecord == null) {
            m_cAudRecord = new CAudioRecord(true);
            m_cAudRecord.StartAudioRecord(0, 0);
        }

        // 音频编码
        if (m_cAudEnc == null) {
            m_cAudEnc = new CMCAudEnc(false);
            m_cAudEnc.createMCEncoder();
            m_cAudEnc.startMCEncoder();
        }

        // 传递采集数据用于编码
        if(m_cAudEncThread == null){
            m_cAudEncThread = new Thread(sndRawToEnc);
            m_cAudEncThread.start();
        }

    }

    private void stopEncAudCap() {

        Log.d(TAG, "Enter stopEncAudCap End! ");
        // 采集器
        if (m_cAudRecord != null) {
            // 采集文件
            m_cPcmFileName = m_cAudRecord.getSavedFileName();
            Log.d(TAG, "Audio Record Pcm file: "+m_cPcmFileName);
            m_cAudRecord.StopAudioRecord();
        }

        // 编码线程
        if (m_cAudEncThread != null) {
            try {
                m_cAudEncThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cAudEncThread = null;
        }

        Log.d(TAG, "stopEncAudCap clear m_cAudRecord!");

        // 清除采集队列
        if (m_cAudRecord != null) {
            m_cAudRecord.clearDataQue();
            m_cAudRecord = null;
        }

        // 编码器
        if (m_cAudEnc != null) {
            // 编码文件
            m_cEncFileName = m_cAudEnc.getSavedFileName();
            Log.d(TAG, "Audio Record Encoder file: "+m_cEncFileName);
            m_cAudEnc.stopMCEncoder();
            m_cAudEnc = null;
        }

        m_bIsCapPcmEncAudBut = false;
        m_bIsSndCapToEnc = false;

        Log.d(TAG, "stopEncAudCap End! ");
    }


    //传递原始数据
    private Runnable sndRawToEnc = new Runnable(){

        @Override
        public void run() {

            boolean bIsEos = false;

            while (m_bIsSndCapToEnc) {
                CRawFrame cPcmData = null;

                long startTime = System.nanoTime() / 1000;

                if (m_cAudRecord != null) {
                    // 采集结束
                    if(m_cAudRecord.isStop()&&m_cAudRecord.pcmDataQueIsEmpty()) {
                        Log.d(TAG, "sndRawToEnc m_cAudRecord stop!");

                        bIsEos = true;

                        if (m_cAudEnc != null) {
                            cPcmData = new CRawFrame();
                            cPcmData.m_bIsEos = true;
                            m_cAudEnc.setRawData(cPcmData);
                        }
                    }

                    if (!bIsEos) {
                        cPcmData = m_cAudRecord.getPCMData();
                    }

                }

                if (cPcmData != null) {
                    if (!cPcmData.m_bIsEos) {
                        Log.d(TAG, "sndRawToEnc send to encoder: "+cPcmData.m_sFrame.length);
                    }

                    if (m_cAudEnc != null) {
                        m_cAudEnc.setRawData(cPcmData);
                    }

                    if (cPcmData.m_bIsEos) {
                        Log.d(TAG, "sndRawToEnc send EOS to encoder！ ");
                        bIsEos = true;
                    }
                }

                Log.d(TAG, "sndRawToEnc cost: "+((System.nanoTime() / 1000) - startTime));

                if (bIsEos) {

                    if (m_cAudEnc != null) {

                        while (!m_cAudEnc.isAudEncStop()) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                    Log.d(TAG, "sndRawToEnc Aud encoder stop！");
                    break;
                }

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    };

    // 读取PCM文件，数据存入m_cPlyPcmQue
    private Runnable readPcm = new Runnable() {
        @Override
        public void run() {

            if (m_cPcmFileName != null) {
                Log.d(TAG, "readPcm file:"+ m_cPcmFileName);

                if (m_cReadPcmFile != null) {
                    m_cReadPcmFile.closeExistFile();
                    m_cReadPcmFile = null;
                }

                m_cReadPcmFile = new CFileManage();
                m_cReadPcmFile.openExistFile(m_cPcmFileName);

                m_sAudBuf = new byte[1024];
            } else {
                Log.d(TAG, "readPcm file null!");
                return;
            }

            while (m_bIsReadPcmAud) {
                int dataLen = 0;
                byte [] audData;
                CRawFrame cPcmData = null;

                while ((dataLen = m_cReadPcmFile.readExistFile(m_sAudBuf))> 0) {

                    if(m_bIsStopPlyRawAud) {
                        Log.d(TAG, "readPcm directly stop!");
                        dataLen = 0;
                        break;
                    }

                    cPcmData = new CRawFrame();
                    audData = new byte[dataLen];

                    System.arraycopy(m_sAudBuf, 0, audData, 0, dataLen);

                    Log.d(TAG, "readPcm read size:"+dataLen);

                    cPcmData.m_bIsEos = false;
                    cPcmData.m_sFrame = audData;

                    m_cPlyPcmQue.setData(cPcmData);
                }

                // EOS
                if (dataLen <= 0) {
                    cPcmData = new CRawFrame();
                    cPcmData.m_bIsEos = true;
                    m_cPlyPcmQue.setData(cPcmData);

                    Log.d(TAG, "readPcm read EOS!");

                    if (m_cReadPcmFile != null) {
                        m_cReadPcmFile.closeSavedFile();
                        m_cReadPcmFile = null;
                    }

                    m_bIsReadPcmAud = false;

                    break;
                }
            }
        }
    };

    // 从m_cPlyPcmQue获取音频数据播放
    private Runnable playAudio = new Runnable() {
        @Override
        public void run() {

            int delay = 10;
            int isEos = 0;

            while (m_bIsPlyRawAud) {
                int dataLen = 0;
                CRawFrame cPcmdata = null;

                if(m_bIsStopPlyRawAud || m_bIsStopPlyDecAud) {
                    Log.d(TAG, "playAudio directly stop!");
                    break;
                }

                cPcmdata = m_cPlyPcmQue.getData();

                if (!cPcmdata.m_bIsEos) {
                    dataLen = cPcmdata.m_sFrame.length;
                    delay = ((dataLen*1000)/2/2/44100); // ms

                    Log.d(TAG, "playAudio length: "+dataLen+" delay: "+delay);

                } else {
                    isEos = 1;
                }

                if (m_cAudPly != null) {
                    m_cAudPly.setAudioDataSource(cPcmdata.m_sFrame, isEos);
                }

                // 音频播放结束
                if (isEos == 1) {

                    Log.d(TAG, "playAudio end EOS! ");

                    if (m_cAudPly != null) {
                        while (!m_cAudPly.isTrackEos()) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    m_bIsPlyRawAud = false;

                    sendEventMessage(m_nHandlerEvent);

                    break;

                }

                try {
                    if (delay > 0) {
                        Thread.sleep(delay);
                    } else {
                        Thread.sleep(10);
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    };

    // 解码AAC
    private Runnable decAudio = new Runnable() {
        @Override
        public void run() {

            String fileName = m_cEncFileName;

            if (fileName == null) {
                Log.d(TAG, "decAudio file null!");
                return;
            }

            m_cAudExtractor.create(false, fileName, false);
            MediaFormat cMediaFormat = m_cAudExtractor.getMediaFormat();

            m_cAudDec.create();
            m_cAudDec.setMediaFormat(cMediaFormat);

            // start
            m_cAudExtractor.start();
            m_cAudDec.start();

            while (m_bIsDecAudio) {

                CRawFrame cPcmData = null;

                if (m_bIsStopPlyDecAud) {
                    cPcmData = new CRawFrame();
                    cPcmData.m_bIsEos = true;
                    Log.d(TAG, "decAudio read data!");
                } else {
                    if (m_cAudExtractor != null) {
                        cPcmData = m_cAudExtractor.getData();
                    }
                }

                if (cPcmData != null) {

                    Log.d(TAG, "decAudio instance Eos: "+cPcmData.m_bIsEos);

                    if (m_cAudDec != null) {
                        m_cAudDec.setAudData(cPcmData);
                    }

                    if (cPcmData.m_bIsEos) {
                        m_bIsDecAudio = false;
                        Log.d(TAG, "decAudio instance EOS!");
                        break;
                    }

                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

            }

        }
    };

    private Runnable ReadDecAud = new Runnable() {
        @Override
        public void run() {

            while (m_bIsReadDecAud) {
                CRawFrame cPcmData = null;

                if (m_bIsStopPlyDecAud) {
                    cPcmData = new CRawFrame();
                    cPcmData.m_bIsEos = true;
                    Log.d(TAG, "ReadDecAud stop read data!");
                } else{
                    if (m_cAudDec != null) {
                        cPcmData = m_cAudDec.getPcm();
                    }
                }

                //Log.d(TAG, "ReadDecAud read data!");

                if (cPcmData != null) {
                    Log.d(TAG, "ReadDecAud read EOS : "+cPcmData.m_bIsEos);

                    m_cPlyPcmQue.setData(cPcmData);

                    if (cPcmData.m_bIsEos) {
                        Log.d(TAG, "ReadDecAud read EOS!");
                        m_bIsReadDecAud = false;
                        break;
                    }
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }


            }
        }
    };

    private void sendEventMessage(int event) {
        Message msg = Message.obtain();
        msg.what = event; //消息的标识
        m_cEventHandler.sendMessage(msg);
    }
}
