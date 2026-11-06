#ifndef SCROLL_BOOST_H
#define SCROLL_BOOST_H

#include <stdint.h>

#define SCROLL_BOOST_SLOP_PX 10
/* A slow drag leaves up to ~375 ms between panel updates (p99), so a held pause must be longer. */
#define SCROLL_BOOST_HELD_PAUSE_MS 400

typedef enum {
    SCROLL_BOOST_NONE,
    SCROLL_BOOST_ENTER_SPEED,
    SCROLL_BOOST_CANCEL_RESTORE,
    SCROLL_BOOST_SCHEDULE_RESTORE,
    SCROLL_BOOST_RESTORE_NOW,
} scroll_boost_action;

typedef struct {
    int touching;
    int boosting;
    int restore_pending;
    int start_x;
    int start_y;
    int last_x;
    int last_y;
} scroll_boost;

void scroll_boost_reset(scroll_boost *boost);

scroll_boost_action scroll_boost_down(scroll_boost *boost, int x, int y);

scroll_boost_action scroll_boost_move(scroll_boost *boost, int x, int y);

scroll_boost_action scroll_boost_up(scroll_boost *boost);

void scroll_boost_restored(scroll_boost *boost);

/*
 * The content stopped while the finger stays down: leave Speed so the scrolled area gets cleaned;
 * moving on by more than the slop enters Speed again.
 */
scroll_boost_action scroll_boost_paused(scroll_boost *boost);

/*
 * When a held finger has paused long enough: the panel has had no update for pause_ms since
 * max(speed_since_ms, last_update_ms). Returns 0 to clean now, otherwise the time to check again.
 */
int64_t scroll_boost_pause_check(int64_t now_ms, int64_t speed_since_ms, int64_t last_update_ms, int pause_ms);

/*
 * When to return from Speed after the finger lifted at lift_ms: once the panel has had no update
 * for settle_ms, or max_wait_ms after the lift if the content keeps moving (a fling's tail).
 * Returns 0 to restore now, otherwise the time to check again.
 */
int64_t scroll_boost_settle_check(int64_t now_ms, int64_t lift_ms, int64_t last_update_ms, int settle_ms,
                                  int max_wait_ms);

#endif
