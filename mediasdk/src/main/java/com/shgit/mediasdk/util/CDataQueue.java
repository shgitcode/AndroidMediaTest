package com.shgit.mediasdk.util;

import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/** 泛型：https://www.jianshu.com/p/986f732ed2f1
 *  采样阻塞队列ArrayBlockingQueue存储数据
 *  ArrayBlockingQueue源码解析: https://www.jianshu.com/p/ed2b56bcba3d
 */
public class CDataQueue <T> {
    private final static String TAG = "CDataQueue";
    private final int QUEUE_LENGTH = 10;
    private int m_nDataQueueCapacity = QUEUE_LENGTH;
    // 队列名称
    private String m_sQueueName;
    // 队列
    private ArrayBlockingQueue<T> m_sDataQueue;
    private boolean m_bIsCreate = false;
    // 直接退出
    private boolean m_bQuit = false;

    // 为了便于控制需要加条件变量
    // 当主动停止事务时防止阻塞
    private ReentrantLock m_cControlLock;  //所有访问的保护锁
    private Condition m_cControlHdl;

    public void create(int queueCapacity, String queName) {
        if (m_bIsCreate) {
            return;
        }

        Log.d(TAG, "createQueueData: "+queName);

        clear();

        m_nDataQueueCapacity = queueCapacity;
        m_sQueueName = queName;

        m_sDataQueue = new ArrayBlockingQueue<T>(m_nDataQueueCapacity);

        // 控制===避免等待
        m_cControlLock = new ReentrantLock();
        m_cControlHdl = m_cControlLock.newCondition();

        m_bQuit = false;
        m_bIsCreate = true;
    }

    // 阻塞方式put === 非阻塞方式offer
    public void setData(T data) {
        Log.d(TAG, "setData: "+ m_sQueueName);

        if (!m_bIsCreate) {
            return;
        }

        // 满了-阻塞
        if (m_sDataQueue.size() == m_nDataQueueCapacity) {
           await();
        }

        m_cControlLock.lock();
        if (m_bQuit) {
            m_cControlLock.unlock();
            return;
        }
        m_cControlLock.unlock();

        // 放入数据
        try {
            m_sDataQueue.put(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        signal();
    }

    // 阻塞方式take === 非阻塞方式poll
    public T getData() {
        Log.d(TAG, "getData: " +m_sQueueName);

        T data = null;

        if (!m_bIsCreate) {
            return data;
        }

        try {
            // 空了--阻塞
            if (m_sDataQueue.isEmpty()) {
                await();
            }

            //
            m_cControlLock.lock();
            if (m_bQuit) {
                m_cControlLock.unlock();
                return data;
            }
            m_cControlLock.unlock();

            data = m_sDataQueue.take();

            signal();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return data;
    }

    public void clear() {
        Log.d(TAG, "clear: "+ m_sQueueName);

        if (m_cControlLock != null) {
            quit();
        }

        if (m_sDataQueue != null) {
            m_sDataQueue.clear();
        }

        m_sDataQueue = null;

        m_bIsCreate = false;
        m_nDataQueueCapacity = QUEUE_LENGTH;

        m_bQuit = false;
    }


    public boolean isEmpty() {
        boolean bIsE = true;

        if (!m_bIsCreate) {
            return bIsE;
        }

        if (m_sDataQueue == null) {
            return bIsE;
        }

        return m_sDataQueue.isEmpty();
    }

    // 直接退出
    public void quit() {
        m_cControlLock.lock();
        m_bQuit = true;
        m_cControlLock.unlock();

        signal();

        m_bIsCreate = false;
    }

    private void signal(){
        m_cControlLock.lock();
        m_cControlHdl.signal();
        m_cControlLock.unlock();
    }

    private void await(){
        m_cControlLock.lock();
        try {
            m_cControlHdl.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            m_cControlLock.unlock();
        }
    }
}