#include "engine.h"
#include "input.h"
#include "math.h"
#include "renderer.h"
#include "stdio.h"
#include "stdlib.h"

#include <string.h>

struct XrEngine xr_module_engine;
struct XrInput xr_module_input;
struct XrRenderer xr_module_renderer;
bool xr_initialized = false;
bool xr_hasFrame = false;
bool xr_usePassthrough = false;
bool xr_vr = false;

#if defined(_DEBUG)
#include <GLES2/gl2.h>
void GLCheckErrors(const char* file, int line) {
	for (int i = 0; i < 10; i++) {
		const GLenum error = glGetError();
		if (error == GL_NO_ERROR) {
			break;
		}
		ALOGE("OpenGL error on line %s:%d %d", file, line, error);
	}
}

void OXRCheckErrors(XrResult result, const char* file, int line) {
	if (XR_FAILED(result)) {
		char errorBuffer[XR_MAX_RESULT_STRING_SIZE];
		xrResultToString(xr_module_engine.Instance, result, errorBuffer);
        ALOGE("OpenXR error on line %s:%d %s", file, line, errorBuffer);
	}
}
#endif

char gManufacturer[128] = {0};

JNIEXPORT void JNICALL Java_com_winlator_cmod_XrActivity_sendManufacturer(JNIEnv *env, jobject thiz, jstring manufacturer) {
    const char *nativeStr = (*env)->GetStringUTFChars(env, manufacturer, 0);
    strncpy(gManufacturer, nativeStr, sizeof(gManufacturer) - 1);
    gManufacturer[sizeof(gManufacturer) - 1] = '\0';
    (*env)->ReleaseStringUTFChars(env, manufacturer, nativeStr);
}

JNIEXPORT void JNICALL Java_com_winlator_cmod_XrActivity_init(JNIEnv *env, jobject obj) {

    // Do not allow second initialization
    if (xr_initialized) {
        return;
    }
    if (strcmp(gManufacturer, "PICO") == 0) {
        memset(&xr_module_engine, 0, sizeof(xr_module_engine));
        xr_module_engine.PlatformFlag[PLATFORM_CONTROLLER_PICO] = true;
        xr_module_engine.PlatformFlag[PLATFORM_EXTENSION_INSTANCE] = true;
        xr_module_engine.PlatformFlag[PLATFORM_EXTENSION_PASSTHROUGH] = true;
        xr_module_engine.PlatformFlag[PLATFORM_EXTENSION_PERFORMANCE] = true;
    }
    if (strcmp(gManufacturer, "META") == 0 || strcmp(gManufacturer, "OCULUS") == 0) {
        memset(&xr_module_engine, 0, sizeof(xr_module_engine));
        xr_module_engine.PlatformFlag[PLATFORM_CONTROLLER_QUEST] = true;
        xr_module_engine.PlatformFlag[PLATFORM_EXTENSION_PASSTHROUGH] = true;
        xr_module_engine.PlatformFlag[PLATFORM_EXTENSION_PERFORMANCE] = true;
        xr_module_engine.PlatformFlag[PLATFORM_VIEWPORT_UNCENTERED] = true;
    }

    // Get Java VM
    JavaVM* vm;
    (*env)->GetJavaVM(env, &vm);

    // Init XR
    xrJava java;
    java.vm = vm;
    java.activity = (*env)->NewGlobalRef(env, obj);
    XrEngineInit(&xr_module_engine, &java, "Winlator", 1);

    // Enter XR
    XrEngineEnter(&xr_module_engine);
    XrInputInit(&xr_module_engine, &xr_module_input);
    XrRendererInit(&xr_module_engine, &xr_module_renderer);
    xr_initialized = true;
    ALOGV("Init called");
}

JNIEXPORT void JNICALL Java_com_winlator_cmod_XrActivity_bindFramebuffer(JNIEnv *env, jobject obj) {
    if (xr_initialized) {
        XrRendererBindFramebuffer(&xr_module_renderer);
    }
}

JNIEXPORT jint JNICALL Java_com_winlator_cmod_XrActivity_getWidth(JNIEnv *env, jobject obj) {
    int w, h;
    XrRendererGetResolution(&xr_module_engine, &xr_module_renderer, &w, &h);
    return w;
}
JNIEXPORT jint JNICALL Java_com_winlator_cmod_XrActivity_getHeight(JNIEnv *env, jobject obj) {
    int w, h;
    XrRendererGetResolution(&xr_module_engine, &xr_module_renderer, &w, &h);
    return h;
}

JNIEXPORT jboolean JNICALL Java_com_winlator_cmod_XrActivity_beginFrame(JNIEnv *env, jobject obj, jboolean immersive, jboolean sbs) {
    if (xr_hasFrame || XrRendererInitFrame(&xr_module_engine, &xr_module_renderer)) {
        // Update controllers state only when necessary
        if (!xr_hasFrame) {
            XrInputUpdate(&xr_module_engine, &xr_module_input);
        }
        xr_hasFrame = false;

        // Set render canvas
        int mode = immersive || xr_vr ? RENDER_MODE_MONO_6DOF : RENDER_MODE_MONO_SCREEN;
        xr_module_renderer.ConfigFloat[CONFIG_CANVAS_DISTANCE] = 5.0f;
        xr_module_renderer.ConfigInt[CONFIG_PASSTHROUGH] = !immersive && !xr_vr && xr_usePassthrough;
        xr_module_renderer.ConfigInt[CONFIG_IMMERSIVE] = immersive && !xr_vr;
        xr_module_renderer.ConfigInt[CONFIG_FRAMESYNC] = xr_vr;
        xr_module_renderer.ConfigInt[CONFIG_MODE] = mode;
        xr_module_renderer.ConfigInt[CONFIG_SBS] = sbs;

        // Recenter on the first frame
        static bool first_frame = true;
        if (first_frame) {
            XrRendererRecenter(&xr_module_engine, &xr_module_renderer);
            first_frame = false;
        }

        // Lock framebuffer
        XrRendererBeginFrame(&xr_module_renderer, 0);

        return true;
    }
    return false;
}

JNIEXPORT void JNICALL Java_com_winlator_cmod_XrActivity_endFrame(JNIEnv *env, jobject obj) {
    XrRendererEndFrame(&xr_module_renderer);
    XrRendererFinishFrame(&xr_module_engine, &xr_module_renderer);

    // Prepare next frame to have input status ready earlier
    if (!xr_hasFrame && XrRendererInitFrame(&xr_module_engine, &xr_module_renderer)) {
        XrInputUpdate(&xr_module_engine, &xr_module_input);
        xr_hasFrame = true;
    }
}

JNIEXPORT jfloatArray JNICALL Java_com_winlator_cmod_XrActivity_getAxes(JNIEnv *env, jobject obj) {
    XrPosef lPose = XrInputGetPose(&xr_module_input, 0);
    XrPosef rPose = XrInputGetPose(&xr_module_input, 1);
    XrVector2f lThumbstick = XrInputGetJoystickState(&xr_module_input, 0);
    XrVector2f rThumbstick = XrInputGetJoystickState(&xr_module_input, 1);
    XrQuaternionf quat = xr_module_renderer.Projections[0].pose.orientation;
    XrVector3f lPosition = xr_module_renderer.Projections[0].pose.position;
    XrVector3f rPosition = xr_module_renderer.Projections[1].pose.position;
    XrVector3f angles = xr_module_renderer.HmdOrientation;

    int count = 0;
    float data[48];
    data[count++] = XrQuaternionfEulerAngles(lPose.orientation).x; //L_PITCH
    data[count++] = XrQuaternionfEulerAngles(lPose.orientation).y; //L_YAW
    data[count++] = XrQuaternionfEulerAngles(lPose.orientation).z; //L_ROLL
    data[count++] = lPose.orientation.x; //L_QX
    data[count++] = lPose.orientation.y; //L_QY
    data[count++] = lPose.orientation.z; //L_QZ
    data[count++] = lPose.orientation.w; //L_QW
    data[count++] = lThumbstick.x; //L_THUMBSTICK_X
    data[count++] = lThumbstick.y; //L_THUMBSTICK_Y
    data[count++] = lPose.position.x; //L_X
    data[count++] = lPose.position.y; //L_Y
    data[count++] = lPose.position.z; //L_Z
    data[count++] = XrQuaternionfEulerAngles(rPose.orientation).x; //R_PITCH
    data[count++] = XrQuaternionfEulerAngles(rPose.orientation).y; //R_YAW
    data[count++] = XrQuaternionfEulerAngles(rPose.orientation).z; //R_ROLL
    data[count++] = rPose.orientation.x; //R_QX
    data[count++] = rPose.orientation.y; //R_QY
    data[count++] = rPose.orientation.z; //R_QZ
    data[count++] = rPose.orientation.w; //R_QW
    data[count++] = rThumbstick.x; //R_THUMBSTICK_X
    data[count++] = rThumbstick.y; //R_THUMBSTICK_Y
    data[count++] = rPose.position.x; //R_X
    data[count++] = rPose.position.y; //R_Y
    data[count++] = rPose.position.z; //R_Z
    data[count++] = angles.x; //HMD_PITCH
    data[count++] = angles.y; //HMD_YAW
    data[count++] = angles.z; //HMD_ROLL
    data[count++] = quat.x; //HMD_QX
    data[count++] = quat.y; //HMD_QY
    data[count++] = quat.z; //HMD_QZ
    data[count++] = quat.w; //HMD_QW
    data[count++] = (lPosition.x + rPosition.x) * 0.5f; //HMD_X
    data[count++] = (lPosition.y + rPosition.y) * 0.5f; //HMD_Y
    data[count++] = (lPosition.z + rPosition.z) * 0.5f; //HMD_Z
    data[count++] = XrVector3fDistance(lPosition, rPosition); //HMD_IPD
    data[count++] = xr_module_renderer.ConfigFloat[CONFIG_VIEWPORT_FOVX]; //HMD_FOVX
    data[count++] = xr_module_renderer.ConfigFloat[CONFIG_VIEWPORT_FOVY]; //HMD_FOVY
    data[count++] = xr_module_renderer.FrameSync; //HMD_SYNC

    jfloat values[count];
    memcpy(values, data, count * sizeof(float));
    jfloatArray output = (*env)->NewFloatArray(env, count);
    (*env)->SetFloatArrayRegion(env, output, (jsize)0, (jsize)count, values);
    return output;
}

JNIEXPORT jbooleanArray JNICALL Java_com_winlator_cmod_XrActivity_getButtons(JNIEnv *env, jobject obj) {
    uint32_t l = XrInputGetButtonState(&xr_module_input, 0);
    uint32_t r = XrInputGetButtonState(&xr_module_input, 1);

    int count = 0;
    bool data[32];
    data[count++] = l & (int)Grip; //L_GRIP
    data[count++] = l & (int)Enter; //L_MENU
    data[count++] = l & (int)LThumb; //L_THUMBSTICK_PRESS
    data[count++] = l & (int)Left; //L_THUMBSTICK_LEFT
    data[count++] = l & (int)Right; //L_THUMBSTICK_RIGHT
    data[count++] = l & (int)Up; //L_THUMBSTICK_UP
    data[count++] = l & (int)Down; //L_THUMBSTICK_DOWN
    data[count++] = l & (int)Trigger; //L_TRIGGER
    data[count++] = l & (int)X; //L_X
    data[count++] = l & (int)Y; //L_Y
    data[count++] = r & (int)A; //R_A
    data[count++] = r & (int)B; //R_B
    data[count++] = r & (int)Grip; //R_GRIP
    data[count++] = r & (int)RThumb; //R_THUMBSTICK_PRESS
    data[count++] = r & (int)Left; //R_THUMBSTICK_LEFT
    data[count++] = r & (int)Right; //R_THUMBSTICK_RIGHT
    data[count++] = r & (int)Up; //R_THUMBSTICK_UP
    data[count++] = r & (int)Down; //R_THUMBSTICK_DOWN
    data[count++] = r & (int)Trigger; //R_TRIGGER

    jboolean values[count];
    memcpy(values, data, count * sizeof(jboolean));
    jbooleanArray output = (*env)->NewBooleanArray(env, count);
    (*env)->SetBooleanArrayRegion(env, output, (jsize)0, (jsize)count, values);
    return output;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_XrActivity_nativeSetUsePT(JNIEnv *env, jobject obj, jboolean enabled) {
    xr_usePassthrough = enabled;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_XrActivity_nativeSetUseVR(JNIEnv *env, jobject obj, jboolean enabled) {
    xr_vr = enabled;
}