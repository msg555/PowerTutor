LOCAL_PATH := $(call my-dir)

include $(CLEAN_VARS)

LOCAL_MODULE_FILENAME := bindings
LOCAL_MODULE := bindings
LOCAL_SRC_FILES := bindings.cpp

include $(BUILD_SHARED_LIBRARY)
