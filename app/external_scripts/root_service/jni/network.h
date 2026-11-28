#ifndef NETWORK_H
#define NETWORK_H

/* Arguments for /system/bin/svc for "wif0", "wif1", "dat0" or "dat1"; NULL for anything else. */
const char *const *network_svc_args(const char *command);

#define NETWORK_SVC "/system/bin/svc"

/* Runs program and waits at most timeout_ms for it; returns its exit status, or -1 if killed or failed. */
int network_run(const char *program, const char *const *args, int timeout_ms);

#endif
