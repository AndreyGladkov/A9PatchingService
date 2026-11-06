#ifndef TOUCH_MONITOR_H
#define TOUCH_MONITOR_H

#include <stdint.h>

typedef struct {
    void (*scroll_started)(void);
    void (*scroll_ended)(void);
    int64_t (*last_panel_update_ms)(void);
} touch_monitor_callbacks;

/* Starts a thread that watches the touchscreen; returns 0 on success. */
int touch_monitor_start(touch_monitor_callbacks callbacks);

/* How long the panel must stay still after the finger lifts before Speed ends. */
void touch_monitor_set_settle_ms(int settle_ms);

/* The longest Speed may outlive the finger while the content keeps moving. */
void touch_monitor_set_max_speed_after_lift_ms(int max_ms);

#endif
