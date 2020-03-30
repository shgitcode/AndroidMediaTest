//
// Created by yuanzhuyuan on 2020/3/28.
//

#include <stdio.h>
#include <string.h>
#include "libYuvJni.h"
#include "libyuv.h"

// nv21 ===> i420
void nv21ToI420(jbyte *src_nv21_data, jint width, jint height, jbyte *src_i420_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_vu_data = src_nv21_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::NV21ToI420((const uint8 *) src_nv21_y_data, width,
                       (const uint8 *) src_nv21_vu_data, width,
                       (uint8 *) src_i420_y_data, width,
                       (uint8 *) src_i420_u_data, width >> 1,
                       (uint8 *) src_i420_v_data, width >> 1,
                       width, height);
}

void mirrorI420(jbyte *src_i420_data, jint width, jint height, jbyte *dst_i420_data) {
    jint src_i420_y_size = width * height;
    // jint src_i420_u_size = (width >> 1) * (height >> 1);
    jint src_i420_u_size = src_i420_y_size >> 2;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_i420_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    libyuv::I420Mirror((const uint8 *) src_i420_y_data, width,
                       (const uint8 *) src_i420_u_data, width >> 1,
                       (const uint8 *) src_i420_v_data, width >> 1,
                       (uint8 *) dst_i420_y_data, width,
                       (uint8 *) dst_i420_u_data, width >> 1,
                       (uint8 *) dst_i420_v_data, width >> 1,
                       width, height);
}

void i420ToNV12(jbyte *src_i420_data, jint width, jint height, jbyte *src_nv12_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv12_y_data = src_nv12_data;
    jbyte *src_nv12_uv_data = src_nv12_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::I420ToNV12(
            (const uint8 *) src_i420_y_data, width,
            (const uint8 *) src_i420_u_data, width >> 1,
            (const uint8 *) src_i420_v_data, width >> 1,
            (uint8 *) src_nv12_y_data, width,
            (uint8 *) src_nv12_uv_data, width,
            width, height);
}
/*
 * 先进行NV21==>I420==>NV12
 * 依据isvFlip决定是否对I420镜像
*/
JNIEXPORT void JNICALL
jni_yuvNV21ToNV12_vFlip(JNIEnv *env, jclass type,
                       jbyteArray nv21, jint width, jint height,
                       jbyteArray nv12, jboolean isvFlip)
 {
    jbyte *src_nv21 = env->GetByteArrayElements(nv21, NULL);
    jbyte *dst_nv12 = env->GetByteArrayElements(nv12, NULL);
    jbyte *tmp_data = NULL;

    // nv21===>i420
    jbyte *i420_data = new jbyte[sizeof(jbyte) * width * height * 3 / 2];
    nv21ToI420(src_nv21, width, height, i420_data);
    tmp_data = i420_data;

    // vFlip
    jbyte *vFlip_i420 = NULL;
    if(isvFlip){
        vFlip_i420 = new jbyte[sizeof(jbyte) * width * height * 3 / 2];
        mirrorI420(tmp_data, width, height, vFlip_i420);
        tmp_data = vFlip_i420;
    }

    // I420 --- nv12
    jbyte *nv12_data = new jbyte[sizeof(jbyte) * width * height * 3 / 2];
    i420ToNV12(tmp_data, width, height, nv12_data);
    tmp_data = nv12_data;

    jint len = env->GetArrayLength(nv12);
    memcpy(dst_nv12, tmp_data, len);
    tmp_data = NULL;
    env->ReleaseByteArrayElements(nv12, dst_nv12, 0);

    // 释放
    if(i420_data != NULL) delete [] i420_data;
    if(vFlip_i420 != NULL) delete [] vFlip_i420;
    if(nv12_data != NULL) delete [] nv12_data;

    return;
 }

/*
 * 先进行NV21==>I420
 * 依据isvFlip决定是否对I420镜像
*/
JNIEXPORT void JNICALL
jni_yuvNV21ToI420_vFlip(JNIEnv *env, jclass type,
                       jbyteArray nv21, jint width, jint height,
                       jbyteArray i420, jboolean isvFlip)
 {
    jbyte *src_nv21 = env->GetByteArrayElements(nv21, NULL);
    jbyte *dst_i420 = env->GetByteArrayElements(i420, NULL);
    jbyte *tmp_data = NULL;

    // nv21===>i420
    jbyte *i420_data = new jbyte[sizeof(jbyte) * width * height * 3 / 2];
    nv21ToI420(src_nv21, width, height, i420_data);
    tmp_data = i420_data;

    // vFlip
    jbyte *vFlip_i420 = NULL;
    if(isvFlip){
        vFlip_i420 = new jbyte[sizeof(jbyte) * width * height * 3 / 2];
        mirrorI420(tmp_data, width, height, vFlip_i420);
        tmp_data = vFlip_i420;
    }

    jint len = env->GetArrayLength(i420);
    memcpy(dst_i420, tmp_data, len);
    tmp_data = NULL;
    env->ReleaseByteArrayElements(i420, dst_i420, 0);

    // 释放
    if(i420_data != NULL) delete [] i420_data;
    if(vFlip_i420 != NULL) delete [] vFlip_i420;

    return;
 }

static JNINativeMethod gMethods[] ={
	{"yuvNV21ToNV12_vFlip", "([BII[BZ)V", (void*)jni_yuvNV21ToNV12_vFlip},
	{"yuvNV21ToI420_vFlip", "([BII[BZ)V", (void*)jni_yuvNV21ToI420_vFlip},

};

JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void * reserved)
{
	printf("jni yuv onload called!\n");

   // if className write Error can lead to "JNI_ERR returned from JNI_OnLoad in"
   // 必须是/不能是点.
    const char* sClassName = "com/shgit/mediasdk/util/libYuvConvert";
	JNIEnv* env = NULL;
	jint   ret = -1;

	if(vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
		return ret;
	}

	// 获取映射的java类
	jclass cClass = env->FindClass(sClassName);
	if(cClass == NULL){
		printf("cannot get class: %s\n",sClassName);
		return ret;
	}

	// 通过RegisterNatives方法动态注册
	if(env->RegisterNatives(cClass, gMethods, sizeof(gMethods)/sizeof(gMethods[0])) < 0) {
	    printf("For class [ %s ] register native method failed!\n", sClassName);
		return ret;
	}

    env->DeleteLocalRef(cClass);

    printf("jni yuv onload called end!\n");

	return JNI_VERSION_1_4;
}