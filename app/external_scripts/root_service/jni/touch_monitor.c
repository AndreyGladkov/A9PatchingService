#include "touch_monitor.h"
#include "scroll_boost.h"

#include <android/log.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "a9EinkService"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define INPUT_DIR "/dev/input"
#define PANEL_WIDTH 824
#define PANEL_HEIGHT 1648
#define MAX_SLOTS 20
#define BITS_PER_LONG (sizeof(unsigned long) * 8)
#define BIT_WORDS(n) (((n) + BITS_PER_LONG - 1) / BITS_PER_LONG)

typedef struct {
    int fd;
    struct input_absinfo x_range;
    struct input_absinfo y_range;
    int slot;
    int active[MAX_SLOTS];
    int x[MAX_SLOTS];
    int y[MAX_SLOTS];
    int primary;
    scroll_boost boost;
    int64_t lift_ms;
    int64_t restore_at_ms;
    int64_t speed_since_ms;
    int64_t pause_check_at_ms;
    touch_monitor_callbacks callbacks;
} touch_monitor;

static int has_bit(const unsigned long *bits, int bit) {
    return (bits[bit / BITS_PER_LONG] >> (bit % BITS_PER_LONG)) & 1;
}

static int is_touchscreen(int fd) {
    unsigned long abs_bits[BIT_WORDS(ABS_CNT)] = {0};
    unsigned long props[BIT_WORDS(INPUT_PROP_CNT)] = {0};
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs_bits)), abs_bits) < 0)
        return 0;
    if (ioctl(fd, EVIOCGPROP(sizeof(props)), props) < 0)
        return 0;
    return has_bit(abs_bits, ABS_MT_POSITION_X) && has_bit(abs_bits, ABS_MT_POSITION_Y)
           && has_bit(abs_bits, ABS_MT_SLOT) && has_bit(props, INPUT_PROP_DIRECT);
}

static int open_touchscreen(void) {
    DIR *dir = opendir(INPUT_DIR);
    if (!dir) {
        LOGE("Touch monitor: cannot open %s: %s", INPUT_DIR, strerror(errno));
        return -1;
    }
    struct dirent *entry;
    int found = -1;
    while (found < 0 && (entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "event", 5) != 0)
            continue;
        char path[64];
        snprintf(path, sizeof(path), "%s/%s", INPUT_DIR, entry->d_name);
        int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0)
            continue;
        if (is_touchscreen(fd)) {
            LOGI("Touch monitor: using %s", path);
            found = fd;
        } else {
            close(fd);
        }
    }
    closedir(dir);
    return found;
}

/* Speed redraws every ~80 ms while content moves (p90 108 ms), so a shorter pause cuts scrolls. */
static int settle_ms = 150;

void touch_monitor_set_settle_ms(int value) {
    __atomic_store_n(&settle_ms, value, __ATOMIC_RELAXED);
}

static int max_speed_after_lift_ms = 800;

void touch_monitor_set_max_speed_after_lift_ms(int value) {
    __atomic_store_n(&max_speed_after_lift_ms, value, __ATOMIC_RELAXED);
}

static int64_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static int scale(int value, const struct input_absinfo *range, int size) {
    int span = range->maximum - range->minimum;
    if (span <= 0)
        return value;
    return (int) ((int64_t) (value - range->minimum) * (size - 1) / span);
}

static int first_active_slot(const touch_monitor *m) {
    for (int i = 0; i < MAX_SLOTS; i++)
        if (m->active[i])
            return i;
    return -1;
}

static void restore(touch_monitor *m) {
    m->restore_at_ms = 0;
    m->pause_check_at_ms = 0;
    scroll_boost_restored(&m->boost);
    m->callbacks.scroll_ended();
}

static void check_held_pause(touch_monitor *m) {
    int64_t next = scroll_boost_pause_check(now_ms(), m->speed_since_ms, m->callbacks.last_panel_update_ms(),
                                            SCROLL_BOOST_HELD_PAUSE_MS);
    if (next != 0) {
        m->pause_check_at_ms = next;
        return;
    }
    m->pause_check_at_ms = 0;
    if (scroll_boost_paused(&m->boost) == SCROLL_BOOST_RESTORE_NOW)
        m->callbacks.scroll_ended();
}

static void check_settled(touch_monitor *m) {
    int64_t next = scroll_boost_settle_check(now_ms(), m->lift_ms, m->callbacks.last_panel_update_ms(),
                                             __atomic_load_n(&settle_ms, __ATOMIC_RELAXED),
                                             __atomic_load_n(&max_speed_after_lift_ms, __ATOMIC_RELAXED));
    if (next == 0)
        restore(m);
    else
        m->restore_at_ms = next;
}

static void apply(touch_monitor *m, scroll_boost_action action) {
    switch (action) {
        case SCROLL_BOOST_ENTER_SPEED:
            m->callbacks.scroll_started();
            m->speed_since_ms = now_ms();
            m->pause_check_at_ms = m->speed_since_ms + SCROLL_BOOST_HELD_PAUSE_MS;
            break;
        case SCROLL_BOOST_CANCEL_RESTORE:
            m->restore_at_ms = 0;
            break;
        case SCROLL_BOOST_SCHEDULE_RESTORE:
            m->pause_check_at_ms = 0;
            m->lift_ms = now_ms();
            check_settled(m);
            break;
        case SCROLL_BOOST_RESTORE_NOW:
        case SCROLL_BOOST_NONE:
            break;
    }
}

static int poll_timeout_ms(const touch_monitor *m) {
    int64_t due = m->restore_at_ms;
    if (m->pause_check_at_ms && (!due || m->pause_check_at_ms < due))
        due = m->pause_check_at_ms;
    if (!due)
        return -1;
    int64_t remaining = due - now_ms();
    return remaining > 0 ? (int) remaining : 0;
}

static void release_all(touch_monitor *m) {
    memset(m->active, 0, sizeof(m->active));
    m->primary = -1;
    apply(m, scroll_boost_up(&m->boost));
}

static void on_frame(touch_monitor *m) {
    if (m->primary >= 0 && m->active[m->primary]) {
        apply(m, scroll_boost_move(&m->boost, m->x[m->primary], m->y[m->primary]));
        return;
    }
    int was_touching = m->primary >= 0;
    m->primary = first_active_slot(m);
    if (m->primary >= 0)
        apply(m, scroll_boost_down(&m->boost, m->x[m->primary], m->y[m->primary]));
    else if (was_touching)
        apply(m, scroll_boost_up(&m->boost));
}

static void on_event(touch_monitor *m, const struct input_event *ev) {
    if (ev->type == EV_SYN) {
        if (ev->code == SYN_REPORT)
            on_frame(m);
        else if (ev->code == SYN_DROPPED)
            release_all(m);
        return;
    }
    if (ev->type != EV_ABS)
        return;
    switch (ev->code) {
        case ABS_MT_SLOT:
            m->slot = ev->value >= 0 && ev->value < MAX_SLOTS ? ev->value : -1;
            break;
        case ABS_MT_TRACKING_ID:
            if (m->slot >= 0)
                m->active[m->slot] = ev->value >= 0;
            break;
        case ABS_MT_POSITION_X:
            if (m->slot >= 0)
                m->x[m->slot] = scale(ev->value, &m->x_range, PANEL_WIDTH);
            break;
        case ABS_MT_POSITION_Y:
            if (m->slot >= 0)
                m->y[m->slot] = scale(ev->value, &m->y_range, PANEL_HEIGHT);
            break;
        default:
            break;
    }
}

static void *run(void *arg) {
    touch_monitor *m = arg;
    struct pollfd pfd = { .fd = m->fd, .events = POLLIN };
    struct input_event events[64];
    for (;;) {
        int ready = poll(&pfd, 1, poll_timeout_ms(m));
        if (ready < 0 && errno != EINTR) {
            LOGE("Touch monitor: poll failed: %s", strerror(errno));
            return NULL;
        }
        if (ready > 0) {
            ssize_t n;
            while ((n = read(m->fd, events, sizeof(events))) > 0)
                for (size_t i = 0; i < (size_t) n / sizeof(events[0]); i++)
                    on_event(m, &events[i]);
        }
        if (m->restore_at_ms && now_ms() >= m->restore_at_ms)
            check_settled(m);
        if (m->pause_check_at_ms && now_ms() >= m->pause_check_at_ms)
            check_held_pause(m);
    }
}

int touch_monitor_start(touch_monitor_callbacks callbacks) {
    static touch_monitor monitor;
    int fd = open_touchscreen();
    if (fd < 0) {
        LOGE("Touch monitor: no touchscreen found");
        return -1;
    }

    memset(&monitor, 0, sizeof(monitor));
    monitor.fd = fd;
    monitor.primary = -1;
    monitor.callbacks = callbacks;
    ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &monitor.x_range);
    ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &monitor.y_range);
    struct input_absinfo slot;
    if (ioctl(fd, EVIOCGABS(ABS_MT_SLOT), &slot) == 0 && slot.value >= 0 && slot.value < MAX_SLOTS)
        monitor.slot = slot.value;
    scroll_boost_reset(&monitor.boost);

    pthread_t thread;
    if (pthread_create(&thread, NULL, run, &monitor) != 0) {
        LOGE("Touch monitor: cannot start thread");
        close(fd);
        return -1;
    }
    pthread_detach(thread);
    return 0;
}
