#include "world_lock.h"
#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>

int wurm_world_lock(const char *path) {
    if (path == NULL) return 0;
    if (path[0] != '/') return -1;
    int fd = open(path, O_RDWR | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (fd < 0) return -1;
    struct flock lock = { .l_type = F_WRLCK, .l_whence = SEEK_SET, .l_start = 0, .l_len = 0 };
    if (fcntl(fd, F_SETLK, &lock) < 0) { close(fd); return -1; }
    /* Intentionally held until process exit. Never close/reopen this inode. */
    puts("[native] WORLD_LOCK_OK");
    return fd;
}
