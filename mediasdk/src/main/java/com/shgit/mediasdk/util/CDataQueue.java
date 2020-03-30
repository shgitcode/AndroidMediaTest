package com.shgit.mediasdk.util;

import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;


/** 泛型：https://www.jianshu.com/p/986f732ed2f1
 *  采样阻塞队列ArrayBlockingQueue存储数据
 *
 */

public class CDataQueue <T> {
    private final static String TAG = "CDataQueue";

    private int m_nDataQueueCapacity = 10;
    private String m_sQueueName;

    private ArrayBlockingQueue<T> m_sDataQueue;
    private boolean m_bIsCreate = false;
    private boolean m_bQuit = false;

    public void create(int queueCapacity, String queName) {
        if (m_bIsCreate) {
            return;
        }

        Log.d(TAG, "createQueueData: "+queName);

        clear();

        m_nDataQueueCapacity = queueCapacity;
        m_sQueueName = queName;

        m_sDataQueue = new ArrayBlockingQueue<T>(m_nDataQueueCapacity);
        m_bIsCreate = true;
    }

    public void add(T data) {
        Log.d(TAG, "add: "+ m_sQueueName);
        m_sDataQueue.add(data);
    }

    public void setData(T data) {
        Log.d(TAG, "setData: "+ m_sQueueName);

        try {
            m_sDataQueue.put(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public T getData() {
        Log.d(TAG, "getData: " +m_sQueueName);

        try {
            if (m_sDataQueue.isEmpty()) {
                //return null;
            }
            return m_sDataQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void clear() {
        if (m_sDataQueue != null) {
            m_sDataQueue.clear();
            m_sDataQueue = null;
        }
        m_bIsCreate = false;
        m_bQuit = false;
        m_nDataQueueCapacity = 10;
    }

    public void release() {
        clear();

        m_sDataQueue = null;

        m_bIsCreate = false;
        m_bQuit = false;
        m_nDataQueueCapacity = 10;
    }

    public boolean isEmpty() {
        boolean bIsE = true;

        if (m_sDataQueue == null) {
            return bIsE;
        }

        return m_sDataQueue.isEmpty();
    }

    // 直接退出不进行数据处理
    public void setQuit(boolean bQuit){
        m_bQuit = bQuit;
    }

    public boolean getQuit(){
        return m_bQuit;
    }
}