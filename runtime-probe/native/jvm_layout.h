#ifndef WURM_JVM_LAYOUT_H
#define WURM_JVM_LAYOUT_H

/* Call once in the standalone runner, before loading JLI or starting threads. */
int wurm_jvm_layout_init(const char *home, const char *installed_jvm);

#endif
