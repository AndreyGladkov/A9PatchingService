#include "scroll_boost.h"

#include <math.h>
#include <string.h>

void scroll_boost_reset(scroll_boost *boost) {
    memset(boost, 0, sizeof(*boost));
}

scroll_boost_action scroll_boost_down(scroll_boost *boost, int x, int y) {
    boost->touching = 1;
    boost->start_x = boost->last_x = x;
    boost->start_y = boost->last_y = y;
    if (!boost->restore_pending)
        return SCROLL_BOOST_NONE;
    boost->restore_pending = 0;
    return SCROLL_BOOST_CANCEL_RESTORE;
}

scroll_boost_action scroll_boost_move(scroll_boost *boost, int x, int y) {
    if (!boost->touching)
        return SCROLL_BOOST_NONE;
    boost->last_x = x;
    boost->last_y = y;
    if (boost->boosting)
        return SCROLL_BOOST_NONE;
    if (hypot((double) (x - boost->start_x), (double) (y - boost->start_y)) <= SCROLL_BOOST_SLOP_PX)
        return SCROLL_BOOST_NONE;
    boost->boosting = 1;
    return SCROLL_BOOST_ENTER_SPEED;
}

scroll_boost_action scroll_boost_up(scroll_boost *boost) {
    boost->touching = 0;
    if (!boost->boosting)
        return SCROLL_BOOST_NONE;
    boost->restore_pending = 1;
    return SCROLL_BOOST_SCHEDULE_RESTORE;
}

void scroll_boost_restored(scroll_boost *boost) {
    boost->boosting = 0;
    boost->restore_pending = 0;
}

int64_t scroll_boost_settle_check(int64_t now_ms, int64_t lift_ms, int64_t last_update_ms, int settle_ms,
                                  int max_wait_ms) {
    int64_t give_up_at = lift_ms + max_wait_ms;
    int64_t quiet_from = (last_update_ms > lift_ms ? last_update_ms : lift_ms) + settle_ms;
    if (now_ms >= quiet_from || now_ms >= give_up_at)
        return 0;
    return quiet_from < give_up_at ? quiet_from : give_up_at;
}

scroll_boost_action scroll_boost_paused(scroll_boost *boost) {
    if (!boost->touching || !boost->boosting)
        return SCROLL_BOOST_NONE;
    boost->boosting = 0;
    boost->start_x = boost->last_x;
    boost->start_y = boost->last_y;
    return SCROLL_BOOST_RESTORE_NOW;
}

int64_t scroll_boost_pause_check(int64_t now_ms, int64_t speed_since_ms, int64_t last_update_ms, int pause_ms) {
    int64_t quiet_from = (last_update_ms > speed_since_ms ? last_update_ms : speed_since_ms) + pause_ms;
    return now_ms >= quiet_from ? 0 : quiet_from;
}
