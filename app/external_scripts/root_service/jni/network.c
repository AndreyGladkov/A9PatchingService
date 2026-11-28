#include "network.h"

#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static const char *const wifi_off[] = {"svc", "wifi", "disable", NULL};
static const char *const wifi_on[] = {"svc", "wifi", "enable", NULL};
static const char *const data_off[] = {"svc", "data", "disable", NULL};
static const char *const data_on[] = {"svc", "data", "enable", NULL};

const char *const *network_svc_args(const char *command) {
    if (strcmp(command, "wif0") == 0) return wifi_off;
    if (strcmp(command, "wif1") == 0) return wifi_on;
    if (strcmp(command, "dat0") == 0) return data_off;
    if (strcmp(command, "dat1") == 0) return data_on;
    return NULL;
}

/*
 * `svc data` waits for the phone process to answer over binder. Without the sepolicy rule the image
 * patcher adds, that answer is denied and svc never exits, which would stall every later command.
 */
int network_run(const char *program, const char *const *args, int timeout_ms) {
    pid_t pid = fork();
    if (pid < 0)
        return -1;
    if (pid == 0) {
        execv(program, (char *const *) args);
        _exit(127);
    }
    const struct timespec step = {0, 50 * 1000 * 1000};
    for (int waited = 0; waited < timeout_ms; waited += 50) {
        int status;
        pid_t done = waitpid(pid, &status, WNOHANG);
        if (done == pid)
            return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
        if (done < 0 && errno != EINTR)
            return -1;
        nanosleep(&step, NULL);
    }
    kill(pid, SIGKILL);
    waitpid(pid, NULL, 0);
    return -1;
}
