#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <sys/stat.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include "zygisk.hpp"

#define LOG_TAG "TimelineUnlocker"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 辅助函数：从 /proc/self/maps 动态寻找 libc.so，并获取它的 dev 和 inode
bool get_libc_dev_ino(dev_t *dev, ino_t *inode) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return false;
    
    char line[512];
    bool found = false;
    while (fgets(line, sizeof(line), fp)) {
        // 寻找包含 libc.so 的行
        if (strstr(line, "/libc.so")) {
            char path[256];
            // 提取文件路径
            if (sscanf(line, "%*s %*s %*s %*s %*s %255s", path) == 1) {
                struct stat st;
                // 获取文件的 dev 和 inode
                if (stat(path, &st) == 0) {
                    *dev = st.st_dev;
                    *inode = st.st_ino;
                    found = true;
                    break;
                }
            }
        }
    }
    fclose(fp);
    return found;
}

// 原始函数的指针
static int (*orig_system_property_get)(const char *name, char *value) = nullptr;

// 我们的劫持函数
int my_system_property_get(const char *name, char *value) {
    if (name != nullptr) {
        if (strcmp(name, "gsm.sim.operator.numeric") == 0) {
            strcpy(value, "310030");
            LOGD("Spoofed gsm.sim.operator.numeric to 310030");
            return strlen("310030");
        }
        if (strcmp(name, "gsm.sim.operator.iso-country") == 0) {
            strcpy(value, "us");
            LOGD("Spoofed gsm.sim.operator.iso-country to us");
            return strlen("us");
        }
    }
    
    // 如果不是我们要改的属性，放行给原函数
    if (orig_system_property_get != nullptr) {
        return orig_system_property_get(name, value);
    }
    return 0;
}

class TimelineUnlocker : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        const char *process_name = env->GetStringUTFChars(args->nice_name, nullptr);
        if (process_name != nullptr) {
            if (strcmp(process_name, "com.google.android.apps.maps") == 0 ||
                strncmp(process_name, "com.google.android.gms", 22) == 0 ||
                strncmp(process_name, "com.google.android.gsf", 22) == 0) {
                is_target_app = true;
                LOGD("Target app detected: %s", process_name);
            }
            env->ReleaseStringUTFChars(args->nice_name, process_name);
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        if (is_target_app) {
            dev_t dev = 0;
            ino_t inode = 0;
            
            // 1. 获取 libc.so 的底层身份信息
            if (get_libc_dev_ino(&dev, &inode)) {
                LOGD("Found libc.so: dev=%lu, inode=%lu, injecting Zygisk v4 PLT hooks...", 
                     (unsigned long)dev, (unsigned long)inode);
                
                // 2. 传入 dev 和 inode 进行 Hook (Zygisk v4 标准写法)
                api->pltHookRegister(dev, inode, "__system_property_get", 
                                     (void *)my_system_property_get, 
                                     (void **)&orig_system_property_get);
                
                // 3. 提交并生效 Hook
                if (api->pltHookCommit()) {
                    LOGD("Successfully committed PLT hooks for __system_property_get");
                } else {
                    LOGD("Failed to commit PLT hooks");
                }
            } else {
                LOGD("Failed to find libc.so in /proc/self/maps");
            }
        }
    }

private:
    zygisk::Api *api;
    JNIEnv *env;
    bool is_target_app = false;
};

REGISTER_ZYGISK_MODULE(TimelineUnlocker)