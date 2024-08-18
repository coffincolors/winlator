#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/native_window.h>

#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <jni.h>
#include <string.h>

#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "System.out", __VA_ARGS__);
#define HAL_PIXEL_FORMAT_BGRA_8888 5

EGLImageKHR createImageKHR(AHardwareBuffer* hardwareBuffer, int textureId) {
    const EGLint attribList[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};

    AHardwareBuffer_acquire(hardwareBuffer);

    EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(hardwareBuffer);
    if (!clientBuffer) {
        printf("Failed to get native client buffer from hardware buffer.\n");
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        printf("Failed to get default EGL display.\n");
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    EGLImageKHR imageKHR = eglCreateImageKHR(eglDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attribList);
    if (!imageKHR) {
        printf("Failed to create EGLImageKHR.\n");
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    glBindTexture(GL_TEXTURE_2D, textureId);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, imageKHR);
    glBindTexture(GL_TEXTURE_2D, 0);

    return imageKHR;
}

AHardwareBuffer* createHardwareBuffer(int width, int height) {
    AHardwareBuffer_Desc buffDesc = {};
    buffDesc.width = width;
    buffDesc.height = height;
    buffDesc.layers = 1;
    buffDesc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;
    buffDesc.format = HAL_PIXEL_FORMAT_BGRA_8888;

    AHardwareBuffer *hardwareBuffer = NULL;
    int result = AHardwareBuffer_allocate(&buffDesc, &hardwareBuffer);
    if (result != 0 || !hardwareBuffer) {
        printf("Failed to allocate AHardwareBuffer.\n");
        return NULL;
    }

    return hardwareBuffer;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_renderer_GPUImage_createHardwareBuffer(JNIEnv *env, jclass obj, jshort width,
                                                         jshort height) {
    AHardwareBuffer* buffer = createHardwareBuffer(width, height);
    if (!buffer) {
        printf("Failed to create hardware buffer.\n");
        return 0;
    }
    return (jlong)buffer;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_renderer_GPUImage_createImageKHR(JNIEnv *env, jclass obj,
                                                   jlong hardwareBufferPtr, jint textureId) {
    if (hardwareBufferPtr == 0) {
        printf("Invalid hardware buffer pointer.\n");
        return 0;
    }
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    EGLImageKHR imageKHR = createImageKHR(hardwareBuffer, textureId);
    if (!imageKHR) {
        printf("Failed to create EGLImageKHR.\n");
        return 0;
    }
    return (jlong)imageKHR;
}

JNIEXPORT void JNICALL
Java_com_winlator_renderer_GPUImage_destroyHardwareBuffer(JNIEnv *env, jclass obj,
                                                          jlong hardwareBufferPtr) {
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (hardwareBuffer) {
        AHardwareBuffer_unlock(hardwareBuffer, NULL);
        AHardwareBuffer_release(hardwareBuffer);
    }
}

JNIEXPORT jobject JNICALL
Java_com_winlator_renderer_GPUImage_lockHardwareBuffer(JNIEnv *env, jclass obj,
                                                       jlong hardwareBufferPtr) {
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (!hardwareBuffer) {
        printf("Invalid hardware buffer pointer.\n");
        return NULL;
    }

    void *virtualAddr = NULL;
    int result = AHardwareBuffer_lock(hardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, NULL, &virtualAddr);
    if (result != 0) {
        printf("Failed to lock hardware buffer.\n");
        return NULL;
    }

    AHardwareBuffer_Desc buffDesc;
    AHardwareBuffer_describe(hardwareBuffer, &buffDesc);

    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID setStride = (*env)->GetMethodID(env, cls, "setStride", "(S)V");
    if (setStride) {
        (*env)->CallVoidMethod(env, obj, setStride, (jshort)buffDesc.stride);
    } else {
        printf("Failed to find method setStride.\n");
    }

    jlong size = buffDesc.stride * buffDesc.height * 4;
    return (*env)->NewDirectByteBuffer(env, virtualAddr, size);
}

JNIEXPORT void JNICALL
Java_com_winlator_renderer_GPUImage_destroyImageKHR(JNIEnv *env, jclass obj, jlong imageKHRPtr) {
    EGLImageKHR imageKHR = (EGLImageKHR)imageKHRPtr;
    if (imageKHR) {
        EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (eglDisplay != EGL_NO_DISPLAY) {
            eglDestroyImageKHR(eglDisplay, imageKHR);
        } else {
            printf("Failed to get EGL display for destroying EGLImageKHR.\n");
        }
    }
}
