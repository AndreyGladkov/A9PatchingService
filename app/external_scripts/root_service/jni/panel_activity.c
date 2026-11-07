#include "panel_activity.h"

#include <android/log.h>
#include <dirent.h>
#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "a9EinkService"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define COMPOSER_NAME "vendor.qti.hardware.display.composer-service"
#define LOGDR_SOCKET "/dev/socket/logdr"
#define COMPOSER_TAG "SDM"
/* The composer logs this line for every update it sends to the panel. */
#define PANEL_UPDATE_PREFIX "epd:: clear="
#define RECONNECT_DELAY_S 2
#define COMPOSER_CHECK_MS 2000

/* logd wire format (system/logging/liblog/include/log/log_read.h), stable since Android 7. */
struct logger_entry {
    uint16_t len;
    uint16_t hdr_size;
    int32_t pid;
    uint32_t tid;
    uint32_t sec;
    uint32_t nsec;
    uint32_t lid;
    uint32_t uid;
};

static int64_t last_update_ms;

static int64_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

int64_t panel_activity_last_update_ms(void) {
    return __atomic_load_n(&last_update_ms, __ATOMIC_RELAXED);
}

static int find_composer_pid(void) {
    DIR *proc = opendir("/proc");
    if (!proc)
        return -1;
    struct dirent *entry;
    int found = -1;
    while (found < 0 && (entry = readdir(proc)) != NULL) {
        int pid = atoi(entry->d_name);
        if (pid <= 0)
            continue;
        char path[64], name[128] = {0};
        snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
        FILE *f = fopen(path, "re");
        if (!f)
            continue;
        size_t n = fread(name, 1, sizeof(name) - 1, f);
        fclose(f);
        name[n] = '\0';
        const char *base = strrchr(name, '/');
        if (strcmp(base ? base + 1 : name, COMPOSER_NAME) == 0)
            found = pid;
    }
    closedir(proc);
    return found;
}

static int open_composer_log_stream(int pid) {
    int fd = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    if (fd < 0)
        return -1;
    struct sockaddr_un addr = { .sun_family = AF_UNIX };
    strncpy(addr.sun_path, LOGDR_SOCKET, sizeof(addr.sun_path) - 1);
    char request[64];
    int len = snprintf(request, sizeof(request), "stream lids=0 pid=%d", pid);
    if (connect(fd, (struct sockaddr *) &addr, sizeof(addr)) < 0 || write(fd, request, len) != len) {
        close(fd);
        return -1;
    }
    return fd;
}

static int is_panel_update(const char *buf, ssize_t n) {
    if (n < (ssize_t) sizeof(struct logger_entry))
        return 0;
    const struct logger_entry *entry = (const struct logger_entry *) buf;
    if (entry->hdr_size < sizeof(struct logger_entry) || entry->hdr_size + entry->len > n || entry->len < 3)
        return 0;
    const char *payload = buf + entry->hdr_size;
    const char *end = payload + entry->len;
    const char *tag = payload + 1;
    const char *tag_end = memchr(tag, '\0', end - tag);
    if (!tag_end || strcmp(tag, COMPOSER_TAG) != 0)
        return 0;
    const char *message = tag_end + 1;
    size_t prefix_len = strlen(PANEL_UPDATE_PREFIX);
    return (size_t) (end - message) > prefix_len && strncmp(message, PANEL_UPDATE_PREFIX, prefix_len) == 0;
}

/*
 * logd keeps a pid-filtered stream open after that process exits and just stops sending, so a
 * restarted composer (new pid) would look like a panel that never updates.
 */
static void follow(int fd, int pid, char *buf, size_t size) {
    for (;;) {
        struct pollfd pfd = { .fd = fd, .events = POLLIN };
        int ready = poll(&pfd, 1, COMPOSER_CHECK_MS);
        if (ready < 0 && errno == EINTR)
            continue;
        if (ready < 0)
            return;
        if (ready == 0) {
            if (find_composer_pid() != pid)
                return;
            continue;
        }
        ssize_t n = read(fd, buf, size);
        if (n <= 0)
            return;
        if (is_panel_update(buf, n))
            __atomic_store_n(&last_update_ms, now_ms(), __ATOMIC_RELAXED);
    }
}

static void *run(void *unused) {
    (void) unused;
    char buf[5 * 1024];
    for (;;) {
        int pid = find_composer_pid();
        int fd = pid > 0 ? open_composer_log_stream(pid) : -1;
        if (fd < 0) {
            sleep(RECONNECT_DELAY_S);
            continue;
        }
        LOGI("Panel activity: following composer pid %d", pid);
        follow(fd, pid, buf, sizeof(buf));
        close(fd);
        sleep(RECONNECT_DELAY_S);
    }
    return NULL;
}

int panel_activity_start(void) {
    pthread_t thread;
    if (pthread_create(&thread, NULL, run, NULL) != 0) {
        LOGE("Panel activity: cannot start thread");
        return -1;
    }
    pthread_detach(thread);
    return 0;
}
