package com.shgit.app;

import android.graphics.PixelFormat;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;

import com.shgit.mediasdk.decoder.CMCVidDec;
import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CMediaExtractor;
import com.shgit.mediasdk.util.CRawFrame;

import androidx.appcompat.app.AppCompatActivity;

public class LocalVideoDecActivity extends AppCompatActivity {
    private final String TAG = "LocalVidDec";
    private final int QUEUE_LENGTH = 10;

    private String m_cFileName = Environment.getExternalStorageDirectory().getAbsolutePath()  +"/test/locVid.mp4";
    private String m_cMiniName = Environment.getExternalStorageDirectory().getAbsolutePath()  +"/test/mini.mp4";

    // 同步得到m_cMediaFormat
    // Condition
    private Object m_cObject = null;

    // 解码渲染View
    private SurfaceView m_cVidDecView = null;
    private SurfaceView m_cMiniDecView = null;

    // 视频的格式
    private MediaFormat m_cMediaFormat = null;

    // 解码器
    private CMCVidDec m_cVidDec = null;
    private CMediaExtractor m_cVidExtractor = null;

    // 解码线程
    private Thread m_cVidDecThread = null;
    private boolean m_bStartDec = false;

    // 小窗口解码器
    private CMCVidDec m_cMiniDec = null;
    private CMediaExtractor  m_cMiniExtractor = null;

    // 解码线程
    private Thread m_cMiniDecThread = null;
    private boolean m_bStartMiniDec = false;

    // 用于小窗口解码数据
    private CDataQueue<CRawFrame> m_cDataQue = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_local_vid_dec);

        // 上层显示
        m_cMiniDecView = findViewById(R.id.miniPreview);
        m_cMiniDecView.setZOrderOnTop(true);
        //m_cMiniDecView.setZOrderMediaOverlay(true);
        //m_cMiniDecView.getHolder().setFormat(PixelFormat.TRANSPARENT);

        m_cVidDecView  = findViewById(R.id.decPreview);
        m_cVidDecView.setZOrderOnTop(false);
        m_cVidDecView.getHolder().setFormat(PixelFormat.TRANSPARENT);

        super.onCreate(savedInstanceState);

        m_cDataQue = new CDataQueue<>();
        m_cDataQue.create(QUEUE_LENGTH, "locVidDecQue");

        //同步器
        m_cObject = new Object();

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        startPlayVid();
    }

    @Override
    protected  void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        stopPlayVid();
    }


    private  void startPlayVid() {
        startPlayDecVid();
        startPlayMiniVid();
    }

    private  void stopPlayVid() {
        stopPlayDecVid();
        stopPlayMiniVid();
    }

    // 播放视频
    private void startPlayDecVid() {

        Log.d(TAG, "startPlayDecVid! ");

        //stopPlayDecVid();

        if (m_cVidExtractor == null) {
            // 视频提取器
            m_cVidExtractor = new CMediaExtractor();
        }

        if (m_cVidDec == null) {
            // 视频解码器
            m_cVidDec = new CMCVidDec();
        }

        m_bStartDec = true;

        // 视频解码线程
        m_cVidDecThread = new Thread(decVideo);
        m_cVidDecThread.start();
    }

    private void stopPlayDecVid() {

        Log.d(TAG, "stopPlayDecAud ! ");

        m_bStartDec = false;

        if (m_cVidDecThread != null) {
            try {
                m_cVidDecThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cVidDecThread = null;
        }

        if (m_cVidExtractor != null) {
            m_cVidExtractor.stop();
            m_cVidExtractor = null;
        }

        if (m_cVidDec != null) {
            m_cVidDec.stop();
            m_cVidDec = null;
        }
    }

    // 播放视频
    private void startPlayMiniVid() {

        Log.d(TAG, "startPlayMiniVid! ");

        //stopPlayMiniVid();

        if (m_cMiniExtractor == null) {
            // 视频提取器
            m_cMiniExtractor = new CMediaExtractor();
        }

        if (m_cMiniDec == null) {
            // 视频解码器
            m_cMiniDec = new CMCVidDec();
        }

        m_bStartMiniDec = true;

        // 视频解码线程
        m_cMiniDecThread = new Thread(decMiniVideo);
        m_cMiniDecThread.start();

    }

    private void stopPlayMiniVid() {

        Log.d(TAG, "stopPlayMiniVid ! ");

        m_bStartMiniDec = false;

        if (m_cMiniDecThread != null) {
            try {
                m_cMiniDecThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cMiniDecThread = null;
        }

        if (m_cMiniExtractor != null) {
            m_cMiniExtractor.stop();
            m_cMiniExtractor = null;
        }
        if (m_cMiniDec != null) {
            m_cMiniDec.stop();
            m_cMiniDec = null;
        }

        if (m_cDataQue != null) {
            m_cDataQue.clear();
            m_cDataQue = null;
        }
    }


    // 解码
    private Runnable decVideo = new Runnable() {
        @Override
        public void run() {

            if (m_cFileName == null) {
                Log.d(TAG, "Media file null!");
                return;
            }

            boolean bIsEos = false;

            // 提取器
            m_cVidExtractor.create(true, m_cFileName, false);
            m_cMediaFormat = m_cVidExtractor.getMediaFormat();

            // 唤醒
            synchronized (m_cObject) {
                m_cObject.notify();
            }

            // 解码器
            m_cVidDec.create(m_cVidDecView.getHolder().getSurface(), m_cMediaFormat.getString(MediaFormat.KEY_MIME));
            m_cVidDec.setMediaFormat(m_cMediaFormat);

            // start
            m_cVidExtractor.start();
            m_cVidDec.start();

            while (m_bStartDec) {

                CRawFrame cPcmData = null;

                if (!bIsEos) {

                    if (m_cVidExtractor != null) {
                        cPcmData = m_cVidExtractor.getData();
                    }

                    if (cPcmData != null) {
                        Log.d(TAG, "decVideo Data EOS: "+cPcmData.m_bIsEos);

                        if (m_cDataQue != null) {
                            m_cDataQue.setData(cPcmData);
                        }

                        if (m_cVidDec != null) {
                            m_cVidDec.setData(cPcmData);
                        }

                        if (cPcmData.m_bIsEos) {
                            bIsEos = true;
                            Log.d(TAG, "decVideo setdata EOS!");
                        }
                    }
                }

                {
                    if (bIsEos) {

                        if (m_cVidDec != null) {
                            if (m_cVidDec.getOutputEos()) {
                                m_bStartDec = false;
                                Log.d(TAG, "decVideo get EOS!");
                            }
                        } else {
                            m_bStartDec = false;
                            Log.d(TAG, "decVideo get EOS!");
                        }
                    }

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }

        }
    };

    // 解码
    private Runnable decMiniVideo = new Runnable() {
        @Override
        public void run() {
            boolean bIsEos = false;
            /*
            if (m_cMiniName == null) {
                Log.d(TAG, "Media file null!");
                return;
            }

            // 提取器：再次创建一个提取器失败
            m_cMiniExtractor.create(true, m_cMiniName, true);
            MediaFormat cMediaFormat = m_cMiniExtractor.getMediaFormat();

            // 解码器
            m_cMiniDec.create(m_cMiniDecView.getHolder().getSurface(), cMediaFormat.getString(MediaFormat.KEY_MIME));
            m_cMiniDec.setMediaFormat(cMediaFormat);

            // start
            m_cMiniExtractor.start();
            m_cMiniDec.start();
             */

            //等待m_cMediaFormat
            synchronized (m_cObject) {
                try {
                    m_cObject.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // 解码器
            m_cMiniDec.create(m_cMiniDecView.getHolder().getSurface(), m_cMediaFormat.getString(MediaFormat.KEY_MIME));
            m_cMiniDec.setMediaFormat(m_cMediaFormat);

            // start
            m_cMiniDec.start();

            while (m_bStartMiniDec) {

                CRawFrame cPcmData = null;

                if (!bIsEos) {

                    if (m_cDataQue != null) {
                        cPcmData = m_cDataQue.getData();
                    }

                    /*
                    if (m_cMiniExtractor != null) {
                        cPcmData = m_cMiniExtractor.getData();
                    }
                     */

                    if (cPcmData != null) {
                        Log.d(TAG, "decVideo Mini Data EOS: "+cPcmData.m_bIsEos);

                        if (m_cMiniDec != null) {
                            m_cMiniDec.setData(cPcmData);
                        }

                        if (cPcmData.m_bIsEos) {
                            bIsEos = true;
                            Log.d(TAG, "decVideo Mini setdata EOS!");
                        }
                    }
                }

                {
                    if (bIsEos) {

                        if (m_cMiniDec != null) {
                            if (m_cMiniDec.getOutputEos()) {
                                m_bStartMiniDec = false;
                                Log.d(TAG, "decVideo Mini get EOS!");
                            }
                        } else {
                            m_bStartMiniDec = false;
                            Log.d(TAG, "decVideo Mini get EOS!");
                        }
                    }

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }

        }
    };
}
