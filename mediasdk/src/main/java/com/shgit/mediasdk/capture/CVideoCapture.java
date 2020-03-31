package com.shgit.mediasdk.capture;

import android.content.Context;
import android.util.Log;

import com.shgit.mediasdk.util.CRawFrame;
import com.shgit.mediasdk.util.CaptureParam;

import androidx.annotation.NonNull;

public class CVideoCapture {
    private final String      TAG = "CVideoCapture";
    private CCameraWrapper    m_cCamCap = null;
    private Context           m_cContext = null;
    private int               m_nDispRotation = 0;
    
    private static volatile CVideoCapture s_cCapture = null;

    private CVideoCapture(){
        Log.d(TAG, "construct!");
        m_cCamCap = new CCameraWrapper();
    }

    public static CVideoCapture getInstance(){
        if(s_cCapture == null){
            synchronized (CVideoCapture.class){
                if(s_cCapture == null){
                    s_cCapture = new CVideoCapture();
                }
            }
        }
        return s_cCapture;
    }

    public CCameraWrapper getCameraWrapper() {
        return m_cCamCap;
    }
    /*
     * 需先调用setContext设置context
     * */
    public int create(int camId, boolean bNeedQueue) {
        Log.d(TAG, "create!");
        if (m_cContext == null) {
            Log.e(TAG, "please call setContext function!");
            return -1;
        }

        if (m_cCamCap == null) {
            Log.e(TAG, "CameraWrapper instance null!");
            return -1;
        }

        m_cCamCap.setContext(m_cContext);
        m_cCamCap.setDispRotation(m_nDispRotation);

        if (m_cCamCap.create(camId, bNeedQueue) != 0){
            Log.e(TAG,"create error! ");
            return -1;
        }
        return 0;
    }

    public void destroy(){
        if(m_cCamCap != null){
            Log.d(TAG, "destroy!");
            m_cCamCap.destroy();
        }
    }

    public int start(CaptureParam tCapParam){
        Log.d(TAG, "start!");

        if(m_cCamCap != null){
            if (m_cCamCap.start(tCapParam) != 0){
                Log.e(TAG,"start error!");
                return -1;
            }
            return 0;
        }


        return -1;
    }

    public void stop(){
        if(m_cCamCap != null){
            Log.d(TAG, "stop!");
            m_cCamCap.stop();
        }
    }

    public boolean isStopCapture(){
        if(m_cCamCap != null){
            Log.d(TAG, "isStopCapture!");
           return m_cCamCap.isStopCapture();
        }
        return true;
    }
    public int switchCamera(){
        Log.d(TAG, "switchCamera!");

        if(m_cCamCap != null){
            if (m_cCamCap.switchCamera() != 0){
                Log.e(TAG,"switchCamera error!");
                return -1;
            }
            return 0;
        }
        return -1;
    }

    public void setDispRotation(int dispRotation) {
        m_nDispRotation = dispRotation;
        if (m_cCamCap != null) {
            m_cCamCap.setDispRotation(dispRotation);
        }
    }

    public void setContext(@NonNull Context context) {
        m_cContext = context.getApplicationContext();
        if (m_cCamCap != null) {
            m_cCamCap.setContext(context);
        }
    }

    public int getCameraCount(){
        if(m_cCamCap != null){
            return m_cCamCap.getCameraCount();
        }

        return -1;
    }

    public int getCameraId(){
        if(m_cCamCap != null){
            return m_cCamCap.getCameraId();
        }

        return -1;
    }

    public void setCapDataQueue(boolean bNeed) {
        if(m_cCamCap != null){
            m_cCamCap.setCapDataQueue(bNeed);
        }
    }

    public void quitDataQueue(){
        if(m_cCamCap != null){
            m_cCamCap.quitDataQueue();
        }
    }

    public CRawFrame getCapFrame(){
        if(m_cCamCap != null){
            return m_cCamCap.getCapFrame();
        }

        return null;
    }

    public boolean haveCapFrame() {
        if(m_cCamCap != null){
            return m_cCamCap.haveCapFrame();
        }

        return false;
    }

    public CCameraWrapper.capInfo getCapInfo() {
        if(m_cCamCap != null){
            return m_cCamCap.getCapInfo();
        }

        return null;
    }

    public void setNeedSaveFile(String fileName, int yuvNum, boolean needSave) {
        if(m_cCamCap != null){
            m_cCamCap.setNeedSaveFile(fileName, yuvNum, needSave);
        }
    }
}
