#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <stdbool.h>
#include <pthread.h>
#include <sys/ipc.h>
#include <sys/syscall.h>
#include <jni.h>
#include <android/log.h>

#define __u32 uint32_t

#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "System.out", __VA_ARGS__);

static int memfd_create(const char *name, unsigned int flags) {
#ifdef __NR_memfd_create
    return syscall(__NR_memfd_create, name, flags);
#else
    return -1;
#endif
}

JNIEXPORT jint JNICALL
Java_com_winlator_sysvshm_SysVSharedMemory_createMemoryFd(JNIEnv *env, jclass obj, jstring name, jint size) {
    const char *namePtr = (*env)->GetStringUTFChars(env, name, 0);

    int fd = memfd_create(namePtr, MFD_ALLOW_SEALING);
    (*env)->ReleaseStringUTFChars(env, name, namePtr);

    if (fd < 0) return -1;

    int res = ftruncate(fd, size);
    if (res < 0) {
        close(fd);
        return -1;
    }

    return fd;
}

JNIEXPORT jobject JNICALL
Java_com_winlator_sysvshm_SysVSharedMemory_mapSHMSegment(JNIEnv *env, jobject obj, jint fd, jlong size, jint offset, jboolean readonly) {
    int prot = readonly ? PROT_READ : PROT_WRITE | PROT_READ;
    char *data = mmap(NULL, size, prot, MAP_SHARED, fd, offset);
    if (data == MAP_FAILED) return NULL;
    return (*env)->NewDirectByteBuffer(env, data, size);
}

JNIEXPORT void JNICALL
Java_com_winlator_sysvshm_SysVSharedMemory_unmapSHMSegment(JNIEnv *env, jobject obj, jobject data, jlong size) {
    char *dataAddr = (*env)->GetDirectBufferAddress(env, data);
    munmap(dataAddr, size);
}
