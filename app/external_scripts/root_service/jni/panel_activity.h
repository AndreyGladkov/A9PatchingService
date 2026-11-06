#ifndef PANEL_ACTIVITY_H
#define PANEL_ACTIVITY_H

#include <stdint.h>

/* Starts a thread that notes every panel update the vendor composer makes; returns 0 on success. */
int panel_activity_start(void);

/* CLOCK_MONOTONIC time of the latest panel update, in ms; 0 before the first one. */
int64_t panel_activity_last_update_ms(void);

#endif
