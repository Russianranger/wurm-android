#ifndef WURM_STARTUP_CRASH_H
#define WURM_STARTUP_CRASH_H

/* Temporary, opt-in recorder for faults before HotSpot installs its handlers. */
void wurm_startup_main(void);
int wurm_startup_finish(void);

#endif
