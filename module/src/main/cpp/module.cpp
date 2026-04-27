#include <string>
#include <vector>
#include <cstring>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include "zygisk.hpp"

#define LOG_TAG "TimelineUnlocker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 需要伪装的属性前缀及其假值
// 使用前缀匹配以同时覆盖多 SIM 卡槽后缀（如 .0, .1）
static const std::pair<std::string, std::string> fake_prop_prefixes[] = {
    {"gsm.sim.operator.numeric",     "310030"},
    {"gsm.sim.operator.iso-country", "us"},
    {"gsm.operator.numeric",         "310030"},
    {"gsm.operator.iso-country",     "us"},
};

// 匹配属性名：精确匹配或带卡槽后缀（.0 .1 ,0 ,1 等）
static const char* find_fake_value(const char *key) {
    if (!key) return nullptr;
    for (const auto &prop : fake_prop_prefixes) {
        const auto &prefix = prop.first;
        if (strncmp(key, prefix.c_str(), prefix.length()) == 0) {
            char suffix = key[prefix.length()];
            // 精确匹配，或后缀为 .N / ,N（多卡槽）
            if (suffix == '\0' ||
                ((suffix == '.' || suffix == ',') && key[prefix.length() + 1] >= '0' && key[prefix.length() + 1] <= '9')) {
                return prop.second.c_str();
            }
        }
    }
    return nullptr;
}

// ==========================================
// 1. Hook __system_property_get
// ==========================================
static int (*old___system_property_get)(const char *key, char *value) = nullptr;

static int new___system_property_get(const char *key, char *value) {
    if (!old___system_property_get) return 0;
    int res = old___system_property_get(key, value);
    const char *fake = find_fake_value(key);
    if (fake) {
        LOGI("system_property_get: %s=%s -> %s", key, value, fake);
        strcpy(value, fake);
        res = strlen(fake);
    }
    return res;
}

// ==========================================
// 2. Hook __system_property_read_callback (致敬 Riru 原作的 thread_local 技巧)
// ==========================================
using callback_func = void(void *cookie, const char *name, const char *value, uint32_t serial);

// 线程本地变量，完美解决多线程并发读取和内存泄漏问题
thread_local callback_func *saved_callback = nullptr;

static void my_callback(void *cookie, const char *name, const char *value, uint32_t serial) {
    if (!saved_callback) return;
    
    const char *fake = find_fake_value(name);
    if (fake) {
        LOGI("system_property_read_callback: %s=%s -> %s", name, value, fake);
        saved_callback(cookie, name, fake, serial);
        return;
    }
    saved_callback(cookie, name, value, serial);
}

static void (*old___system_property_read_callback)(const prop_info *pi, callback_func *callback, void *cookie) = nullptr;

static void new___system_property_read_callback(const prop_info *pi, callback_func *callback, void *cookie) {
    if (!old___system_property_read_callback) {
        if (callback) callback(cookie, "", "", 0);
        return;
    }
    saved_callback = callback;
    old___system_property_read_callback(pi, my_callback, cookie);
}

// ==========================================
// 3. Zygisk Module 核心逻辑
// ==========================================
struct LibEntry {
    dev_t dev;
    ino_t ino;
    std::string path;
};

// 文件里是否包含某个字符串（mmap + memmem）。
// 用于筛选真正引用了 __system_property_get 的库。
static bool file_contains_str(const char *path, const char *needle, size_t nlen) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0) { close(fd); return false; }
    if (st.st_size <= 0 || (size_t)st.st_size < nlen) { close(fd); return false; }
    if (st.st_size > 64 * 1024 * 1024) { close(fd); return false; }
    size_t size = (size_t) st.st_size;
    void *base = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (base == MAP_FAILED) return false;
    bool found = memmem(base, size, needle, nlen) != nullptr;
    munmap(base, size);
    return found;
}

// 诊断阶段：只信任少数已知会调 __system_property_get 的核心系统库。
// 如果这几个 commit 成功，说明问题在数量；都不行说明机制层面有问题。
static bool should_skip_lib(const char *path) {
    const char *name = strrchr(path, '/');
    name = name ? name + 1 : path;
    static const char *kWhitelist[] = {
        "libandroid_runtime.so",
        "libcutils.so",
        "libutils.so",
        nullptr
    };
    for (int i = 0; kWhitelist[i]; i++) {
        if (strcmp(name, kWhitelist[i]) == 0) return false;
    }
    return true;
}

static void install_plt_hooks(zygisk::Api *api) {
    LOGI("install_plt_hooks: begin");
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) {
        LOGE("Failed to open /proc/self/maps");
        return;
    }

    char line[512];
    std::vector<LibEntry> libs;
    std::vector<ino_t> seen_inodes;
    size_t total_seen = 0;

    static const char kSym1[] = "__system_property_get";

    while (fgets(line, sizeof(line), fp)) {
        if (!strstr(line, ".so")) continue;

        // /proc/self/maps 行格式：
        //   addr-addr perms offset dev_major:dev_minor inode  path
        // 直接从行里解析 dev/inode，不要用 stat（overlayfs 下 stat 拿到的
        // 是合并层的 dev/inode，与 maps 报告的真实底层 dev/inode 不一致，
        // 会导致 ReZygisk 的 PLT hook 注册完全失效）。
        unsigned int dev_major = 0, dev_minor = 0;
        unsigned long inode_num = 0;
        char path[256];
        if (sscanf(line, "%*s %*s %*s %x:%x %lu %255s",
                   &dev_major, &dev_minor, &inode_num, path) != 4) continue;
        if (inode_num == 0) continue;

        dev_t dev = makedev(dev_major, dev_minor);
        ino_t inode = (ino_t) inode_num;

        bool seen = false;
        for (ino_t prev : seen_inodes) {
            if (prev == inode) { seen = true; break; }
        }
        if (seen) continue;
        seen_inodes.push_back(inode);
        total_seen++;

        if (should_skip_lib(path)) continue;
        // 仅保留 .dynstr 中含目标符号名的库（极快，单次 mmap+memmem）
        if (!file_contains_str(path, kSym1, sizeof(kSym1) - 1)) continue;

        libs.push_back({dev, inode, path});
    }
    fclose(fp);

    LOGI("install_plt_hooks: filtered %zu/%zu candidate libs", libs.size(), total_seen);

    // 逐库 register + commit。某些库（如 libutils.so）通过 direct binding
    // 调用，不走 PLT，commit 会失败但不影响其他库。
    size_t hooked = 0;
    for (const auto &lib : libs) {
        void *tmp_get = nullptr;
        void *tmp_cb = nullptr;
        api->pltHookRegister(lib.dev, lib.ino, "__system_property_get",
                             (void *)new___system_property_get, &tmp_get);
        api->pltHookRegister(lib.dev, lib.ino, "__system_property_read_callback",
                             (void *)new___system_property_read_callback, &tmp_cb);
        api->pltHookCommit();
        if (tmp_get) {
            old___system_property_get = (int (*)(const char *, char *)) tmp_get;
            hooked++;
        }
        if (tmp_cb) {
            old___system_property_read_callback =
                (void (*)(const prop_info *, callback_func *, void *)) tmp_cb;
        }
    }

    LOGI("install_plt_hooks: %zu libs hooked, old_get=%p old_cb=%p",
         hooked,
         (void *)old___system_property_get,
         (void *)old___system_property_read_callback);
}

class LocationReportEnabler : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
        LOGI("onLoad: module loaded into process pid=%d", getpid());
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        const char *process = env->GetStringUTFChars(args->nice_name, nullptr);
        if (process) {
            // 仅 hook 消费方进程：GMS / GSF / Maps 在自己进程里通过
            // SystemProperties.get → __system_property_get 读 telephony 属性。
            // 不要 hook com.android.phone 或 system_server，会破坏自身 SIM 状态机。
            if (strcmp(process, "com.google.android.gsf") == 0 ||
                strncmp(process, "com.google.android.gms", 22) == 0 ||
                strcmp(process, "com.google.android.apps.maps") == 0) {
                is_target_app = true;
                LOGI("Target app detected: %s", process);
            }
            env->ReleaseStringUTFChars(args->nice_name, process);
        }

        // 非目标进程卸载模块，减少开销和检测风险
        if (!is_target_app) {
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        if (!is_target_app) return;
        LOGI("postAppSpecialize: installing hooks");
        install_plt_hooks(api);
    }

private:
    zygisk::Api *api;
    JNIEnv *env;
    bool is_target_app = false;
};

REGISTER_ZYGISK_MODULE(LocationReportEnabler)