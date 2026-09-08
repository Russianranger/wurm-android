#!/data/data/com.termux/files/usr/bin/bash
# Invoked through su -c by RootServerController. No game files are supplied here.
set -u
runtime=$1
java_bin=$2
token=$3
marker="WURM_LAUNCHER_${token}"
prefix=/data/data/com.termux/files/usr

fail() { printf '%s\n' "[launcher] $*"; exit 1; }
[[ $(/system/bin/id -u) == 0 ]] || fail "Root was not granted."
[[ -d "$runtime" ]] || fail "Runtime directory does not exist: $runtime"
[[ -x "$java_bin" ]] || fail "Java executable not found: $java_bin"
[[ -x "$prefix/bin/flock" ]] || fail "Missing Termux flock; install util-linux."
cd -- "$runtime" || fail "Cannot enter runtime directory."
for required in wurm-arm64-poc.jar server.jar common.jar \
    poc-lib/sqlite-jdbc-3.53.2.1.jar poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar; do
    [[ -r "$required" ]] || fail "Missing or unreadable: $required"
done
[[ -d lib && -d Adventure ]] || fail "The runtime needs lib/ and Adventure/ directories."

# A kernel-held lock has no stale PID file. Java inherits it too, so a killed
# supervisor cannot allow another launcher instance to open the same world.
exec 9> .wurm-launcher.lock || fail "Cannot create runtime lock."
"$prefix/bin/flock" -n 9 || fail "This runtime is already locked by a launcher/server."

# su does not inherit an interactive Termux session. Use the existing Termux JRE
# and native libraries explicitly; do not inherit the Android app's preload.
unset LD_PRELOAD JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS CLASSPATH JAVA_HOME
export PREFIX="$prefix"
export HOME=/data/data/com.termux/files/home
export TMPDIR="$prefix/tmp"
export PATH="$prefix/bin:/system/bin:/system/xbin"
export LD_LIBRARY_PATH="$prefix/lib"
umask 077

# Never launch after a cancelled/expired root prompt: wait for an explicit
# acknowledgement over the app-owned pipe. EOF means the app went away.
printf '%s:READY\n' "$marker"
IFS= read -r -t 45 request || fail "Start cancelled or launcher disconnected."
[[ "$request" == start ]] || fail "Start cancelled."

child=""
stop_requested=0
request_stop() {
    if [[ "$stop_requested" == 0 && -n "$child" ]]; then
        stop_requested=1
        printf '%s\n' '[launcher] Sending SIGTERM to the managed JVM; waiting for it to exit.'
        kill -TERM "$child" 2>/dev/null || true
    fi
}
trap request_stop HUP INT TERM

"$java_bin" -Xms512m -Xmx4g -Djava.awt.headless=true \
    -cp 'wurm-arm64-poc.jar:poc-lib/sqlite-jdbc-3.53.2.1.jar:poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar:server.jar:common.jar:lib/*' \
    poc.AndroidServerMain Adventure </dev/null &
child=$!
printf '%s:PID:%s\n' "$marker" "$child"

while kill -0 "$child" 2>/dev/null; do
    if [[ "$stop_requested" == 1 ]]; then
        /system/bin/sleep 1
    elif IFS= read -r -t 1 request; then
        [[ "$request" == stop ]] && request_stop
    else
        read_status=$?
        # Bash read: >128 is timeout; 1 is EOF (Activity/service process died).
        [[ "$read_status" -le 128 ]] && request_stop
    fi
done
wait "$child"
result=$?
printf '%s:EXIT:%s\n' "$marker" "$result"
exit "$result"
