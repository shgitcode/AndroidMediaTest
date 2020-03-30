package com.shgit.mediasdk.util;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/*
*  1 打开已有文件，并读取内容；
*  2 创建文件，保存内容
* */
public class CFileManage {
    private final static  String TAG = "CFileManage";

    private final int BUF_LEN = 200 * 1024;

    // 输出文件
    private File m_cOutputFile = null;
    private FileOutputStream m_cFOS = null;
    private BufferedOutputStream m_cBOS = null;

    // 输入文件
    private File m_cInputFile = null;
    private FileInputStream m_cFIS = null;
    private BufferedInputStream m_cAudioBis = null;
    // 输入文件名
    public  String m_sSavedPath = null;

    /*
     *  fileName : 文件名：cap.h264 / cap.pcm
     * 创建存储音视频的文件
     * */
    public void createSavedFile(String fileName){

        //System.currentTimeMillis()
        m_sSavedPath = Environment.getExternalStorageDirectory().getAbsolutePath()  +"/test/"+ fileName;

        Log.d(TAG, " save file: " + m_sSavedPath);

        m_cOutputFile = new File(m_sSavedPath);
        if (!m_cOutputFile.getParentFile().exists()) {
            m_cOutputFile.getParentFile().mkdirs();
        }

        try {
            m_cOutputFile.createNewFile();
            m_cFOS = new FileOutputStream(m_cOutputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        m_cBOS = new BufferedOutputStream(m_cFOS, BUF_LEN);
    }


    public int writeSavedFile(byte[] data, int length) {
        if (m_cBOS == null) {
            return 1;
        }

        try {
            m_cBOS.write(data, 0, length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }


    public String getSavedFileName(){
        return m_sSavedPath;
    }

    public void closeSavedFile() {
        try {
            if (m_cBOS != null) {
                m_cBOS.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (m_cBOS != null) {
                try {
                    m_cBOS.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    m_cBOS = null;
                }
            }
        }
        m_cOutputFile = null;
    }

    /*
     *  filePath:需要打开已存储音视频的文件路径名
     * */
    public int openExistFile(String filePath){
        Log.d(TAG, " open file: " + filePath);

        m_cInputFile = new File(filePath);
        if (!m_cInputFile.exists()) {
            Log.d(TAG, " open file [" + filePath+ "] not exist!");
            return 1;
        }

        try {
            m_cFIS = new FileInputStream(m_cInputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public int readExistFile(byte[] sBuf){

        int nRet = -1;

        if (m_cFIS == null) {
            return nRet;
        }

        if (sBuf == null) {
            return nRet;
        }

        try {
            /*
             * nRet: must be grater zero;
             * */
            nRet = m_cFIS.read(sBuf, 0, sBuf.length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return nRet;
    }

    public void closeExistFile(){

        if (m_cFIS != null) {
            try {
                m_cFIS.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                m_cFIS = null;
            }
        }

        m_cInputFile = null;
    }
}
