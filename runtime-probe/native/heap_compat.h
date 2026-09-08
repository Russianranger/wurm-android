#ifndef WURM_HEAP_COMPAT_H
#define WURM_HEAP_COMPAT_H
/* No request leaves allocator policy alone. Explicit "off" must succeed. */
int wurm_configure_heap(const char *policy);
/* Platform-independent policy contract, with allocator operations supplied by caller. */
int wurm_heap_compat_apply(const char *policy, int (*disable)(void), int (*sample_tag)(void));
#endif
