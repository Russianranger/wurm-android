#ifndef WURM_WORLD_LOCK_H
#define WURM_WORLD_LOCK_H
/* POSIX record lock interoperates with Android FileChannel.tryLock(). */
int wurm_world_lock(const char *path);
#endif
