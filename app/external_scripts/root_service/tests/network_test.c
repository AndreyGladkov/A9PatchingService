#include "../jni/network.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static int failures = 0;

#define EXPECT(condition)                                                \
    do {                                                                 \
        if (!(condition)) {                                              \
            fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #condition); \
            failures++;                                                  \
        }                                                                \
    } while (0)

static int same_args(const char *const *args, const char *a, const char *b, const char *c) {
    return args && strcmp(args[0], a) == 0 && strcmp(args[1], b) == 0 && strcmp(args[2], c) == 0 && !args[3];
}

static long elapsed_ms(struct timespec from) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec - from.tv_sec) * 1000 + (now.tv_nsec - from.tv_nsec) / 1000000;
}

static void maps_commands_to_svc(void) {
    EXPECT(same_args(network_svc_args("wif0"), "svc", "wifi", "disable"));
    EXPECT(same_args(network_svc_args("wif1"), "svc", "wifi", "enable"));
    EXPECT(same_args(network_svc_args("dat0"), "svc", "data", "disable"));
    EXPECT(same_args(network_svc_args("dat1"), "svc", "data", "enable"));
}

static void leaves_other_commands_alone(void) {
    EXPECT(network_svc_args("wif") == NULL);
    EXPECT(network_svc_args("wif2") == NULL);
    EXPECT(network_svc_args("dat0 ") == NULL);
    EXPECT(network_svc_args("r") == NULL);
    EXPECT(network_svc_args("") == NULL);
}

static void returns_the_exit_status(void) {
    const char *const args[] = {"sh", "-c", "exit 3", NULL};
    EXPECT(network_run("/bin/sh", args, 2000) == 3);
}

static void kills_a_program_that_never_ends(void) {
    const char *const args[] = {"sh", "-c", "sleep 5", NULL};
    struct timespec start;
    clock_gettime(CLOCK_MONOTONIC, &start);
    EXPECT(network_run("/bin/sh", args, 200) == -1);
    EXPECT(elapsed_ms(start) < 1000);
}

static void fails_when_the_program_is_missing(void) {
    const char *const args[] = {"missing", NULL};
    EXPECT(network_run("/nonexistent/svc", args, 2000) == 127);
}

int main(void) {
    maps_commands_to_svc();
    leaves_other_commands_alone();
    returns_the_exit_status();
    kills_a_program_that_never_ends();
    fails_when_the_program_is_missing();
    if (failures) {
        fprintf(stderr, "%d failure(s)\n", failures);
        return EXIT_FAILURE;
    }
    printf("network: all tests passed\n");
    return EXIT_SUCCESS;
}
