/* Error attribution only: no additional GL queries or changes to error state. */
#ifndef WURM_ERROR_TRACE_H
#define WURM_ERROR_TRACE_H
void wurm_error_shim(const char *function, int line);
void wurm_error_driver(const char *function, int line);
void wurm_error_observed(unsigned error, int driver);
#endif
