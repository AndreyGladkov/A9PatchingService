#include "../jni/scroll_boost.h"

#include <stdio.h>
#include <stdlib.h>

static int failures = 0;

#define EXPECT_EQ(expected, actual)                                                        \
    do {                                                                                   \
        long long e = (long long) (expected), a = (long long) (actual);                    \
        if (e != a) {                                                                      \
            fprintf(stderr, "%s:%d: expected %lld, got %lld\n", __FILE__, __LINE__, e, a); \
            failures++;                                                                    \
        }                                                                                  \
    } while (0)

static scroll_boost fresh(void) {
    scroll_boost boost;
    scroll_boost_reset(&boost);
    return boost;
}

static void tap_does_not_boost(void) {
    scroll_boost boost = fresh();
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_down(&boost, 100, 100));
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_move(&boost, 106, 104));
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_up(&boost));
}

static void drag_beyond_slop_enters_speed_once(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 500);
    EXPECT_EQ(SCROLL_BOOST_ENTER_SPEED, scroll_boost_move(&boost, 100, 489));
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_move(&boost, 100, 400));
}

static void release_schedules_restore(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 500);
    scroll_boost_move(&boost, 100, 300);
    EXPECT_EQ(SCROLL_BOOST_SCHEDULE_RESTORE, scroll_boost_up(&boost));
}

static void touch_between_swipes_keeps_speed(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 500);
    scroll_boost_move(&boost, 100, 300);
    scroll_boost_up(&boost);
    EXPECT_EQ(SCROLL_BOOST_CANCEL_RESTORE, scroll_boost_down(&boost, 100, 500));
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_move(&boost, 100, 300));
    EXPECT_EQ(SCROLL_BOOST_SCHEDULE_RESTORE, scroll_boost_up(&boost));
}

static void tap_while_restore_pending_reschedules_it(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 500);
    scroll_boost_move(&boost, 100, 300);
    scroll_boost_up(&boost);
    EXPECT_EQ(SCROLL_BOOST_CANCEL_RESTORE, scroll_boost_down(&boost, 200, 200));
    EXPECT_EQ(SCROLL_BOOST_SCHEDULE_RESTORE, scroll_boost_up(&boost));
}

static void next_scroll_after_restore_enters_speed_again(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 500);
    scroll_boost_move(&boost, 100, 300);
    scroll_boost_up(&boost);
    scroll_boost_restored(&boost);
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_down(&boost, 100, 500));
    EXPECT_EQ(SCROLL_BOOST_ENTER_SPEED, scroll_boost_move(&boost, 100, 300));
}

static void restores_once_the_panel_is_quiet(void) {
    EXPECT_EQ(0, scroll_boost_settle_check(1400, 1000, 1100, 300, 4000));
}

static void waits_while_the_panel_still_updates(void) {
    EXPECT_EQ(1500, scroll_boost_settle_check(1300, 1000, 1200, 300, 4000));
}

static void counts_quiet_time_from_the_lift_when_nothing_moved(void) {
    EXPECT_EQ(1300, scroll_boost_settle_check(1100, 1000, 500, 300, 4000));
    EXPECT_EQ(0, scroll_boost_settle_check(1300, 1000, 500, 300, 4000));
}

static void cuts_a_long_fling_tail_at_the_limit(void) {
    EXPECT_EQ(1800, scroll_boost_settle_check(1700, 1000, 1690, 150, 800));
    EXPECT_EQ(0, scroll_boost_settle_check(1800, 1000, 1790, 150, 800));
}

static void held_pause_leaves_speed_and_moving_on_reenters(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 900);
    scroll_boost_move(&boost, 100, 600);
    EXPECT_EQ(SCROLL_BOOST_RESTORE_NOW, scroll_boost_paused(&boost));
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_move(&boost, 100, 595));
    EXPECT_EQ(SCROLL_BOOST_ENTER_SPEED, scroll_boost_move(&boost, 100, 580));
}

static void pause_without_speed_does_nothing(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 900);
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_paused(&boost));
}

static void release_after_held_pause_needs_no_restore(void) {
    scroll_boost boost = fresh();
    scroll_boost_down(&boost, 100, 900);
    scroll_boost_move(&boost, 100, 600);
    scroll_boost_paused(&boost);
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_up(&boost));
}

static void pause_check_waits_for_quiet_after_the_last_update(void) {
    EXPECT_EQ(1700, scroll_boost_pause_check(1500, 1000, 1300, 400));
    EXPECT_EQ(0, scroll_boost_pause_check(1700, 1000, 1300, 400));
    EXPECT_EQ(1400, scroll_boost_pause_check(1200, 1000, 500, 400));
}

static void move_without_touch_is_ignored(void) {
    scroll_boost boost = fresh();
    EXPECT_EQ(SCROLL_BOOST_NONE, scroll_boost_move(&boost, 100, 300));
}

int main(void) {
    tap_does_not_boost();
    drag_beyond_slop_enters_speed_once();
    release_schedules_restore();
    touch_between_swipes_keeps_speed();
    tap_while_restore_pending_reschedules_it();
    next_scroll_after_restore_enters_speed_again();
    move_without_touch_is_ignored();
    restores_once_the_panel_is_quiet();
    waits_while_the_panel_still_updates();
    counts_quiet_time_from_the_lift_when_nothing_moved();
    cuts_a_long_fling_tail_at_the_limit();
    held_pause_leaves_speed_and_moving_on_reenters();
    pause_without_speed_does_nothing();
    release_after_held_pause_needs_no_restore();
    pause_check_waits_for_quiet_after_the_last_update();
    if (failures) {
        fprintf(stderr, "%d failure(s)\n", failures);
        return EXIT_FAILURE;
    }
    printf("scroll_boost: all tests passed\n");
    return EXIT_SUCCESS;
}
