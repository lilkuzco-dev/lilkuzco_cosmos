#!/bin/bash
# devserver.sh — boot the Loom dev server, feed it commands, capture the log.
#
# Exists because Phase 1's acceptance criteria are all server-side facts (do the
# features register, do deposits actually appear, does /locate find a seep) and none
# of them need a GUI client. Commands are piped in through a FIFO once the server
# reports "Done", so nothing is sent into a server that is not listening yet.
#
# Usage: tools/devserver.sh <logfile> <command> [command...]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

LOG="$1"; shift
export JAVA_HOME="$HOME/jdks/jdk-25.0.4+7/Contents/Home"

FIFO="$(mktemp -u)"
mkfifo "$FIFO"
# Read-write, not write-only: opening a FIFO for writing alone blocks until a reader
# attaches, and our reader is the gradle process started on the next line — that is a
# deadlock. Holding it open read-write also keeps the server from seeing EOF on stdin
# the moment the first command finishes.
exec 3<> "$FIFO"

./gradlew runServer --no-daemon --console=plain < "$FIFO" > "$LOG" 2>&1 &
GRADLE_PID=$!

# Wait for the server to finish booting (or die trying).
for _ in $(seq 1 180); do
	if grep -q 'Done (' "$LOG" 2>/dev/null; then break; fi
	if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
		echo "!! server exited during boot — see $LOG" >&2
		exec 3>&-; rm -f "$FIFO"
		exit 1
	fi
	sleep 2
done
if ! grep -q 'Done (' "$LOG" 2>/dev/null; then
	echo "!! server never reported Done — see $LOG" >&2
	kill "$GRADLE_PID" 2>/dev/null
	exec 3>&-; rm -f "$FIFO"
	exit 1
fi
echo "server up."

for cmd in "$@"; do
	echo ">> $cmd"
	echo "$cmd" >&3
	# Commands here are synchronous and can be long (a survey generates chunks);
	# wait for the server to go quiet rather than guessing a duration.
	last_size=-1
	for _ in $(seq 1 900); do
		size=$(wc -c < "$LOG")
		if [ "$size" = "$last_size" ]; then break; fi
		last_size=$size
		sleep 2
	done
done

echo "stop" >&3
exec 3>&-
wait "$GRADLE_PID"
rm -f "$FIFO"
echo "server stopped."
