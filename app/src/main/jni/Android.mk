# This is the Android makefile for libyuv for both platform and NDK.
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/3rdparty/libyuv/include

LOCAL_SRC_FILES := \
    libYuvJni.cpp

LOCAL_LDLIBS := -landroid
#LOCAL_LDLIBS += -L$(LOCAL_PATH)/3rdparty/prebuild/armv7-a -lyuv
LOCAL_LDFLAGS += $(LOCAL_PATH)/3rdparty/prebuild/armv7-a/libyuv.a

LOCAL_MODULE := libformatConvert

include $(BUILD_SHARED_LIBRARY)
include $(call all-makefiles-under,$(LOCAL_PATH))

