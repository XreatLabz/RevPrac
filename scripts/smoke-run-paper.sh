#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MINECRAFT_VERSION="${MINECRAFT_VERSION:-1.21.11}"
USER_AGENT="RevPracSmoke/0.1 (https://github.com/XreatLabz/RevPrac)"
SMOKE_DIR="$ROOT_DIR/build/smoke/paper-$MINECRAFT_VERSION"
PLUGIN_DIR="$SMOKE_DIR/plugins"
PAPER_JAR="$SMOKE_DIR/paper-$MINECRAFT_VERSION.jar"
LOG_FILE="$SMOKE_DIR/latest-smoke.log"
STDIN_PIPE="$SMOKE_DIR/stdin.pipe"

mkdir -p "$PLUGIN_DIR"

PLUGIN_JAR="$(find "$ROOT_DIR/build/libs" -maxdepth 1 -type f -name 'RevPrac-*.jar' ! -name '*-sources.jar' | sort | tail -n 1)"
if [[ -z "$PLUGIN_JAR" ]]; then
    echo "No RevPrac plugin jar found under build/libs. Run ./gradlew jar first." >&2
    exit 1
fi

if [[ ! -f "$PAPER_JAR" ]]; then
    BUILDS_JSON="$(curl -fsSL -H "User-Agent: $USER_AGENT" "https://fill.papermc.io/v3/projects/paper/versions/$MINECRAFT_VERSION/builds")"
    BUILD_ID="$(jq -r 'map(select(.channel == "STABLE")) | .[0].id' <<<"$BUILDS_JSON")"
    DOWNLOAD_URL="$(jq -r 'map(select(.channel == "STABLE")) | .[0].downloads["server:default"].url' <<<"$BUILDS_JSON")"

    if [[ "$BUILD_ID" == "null" || -z "$BUILD_ID" || "$DOWNLOAD_URL" == "null" || -z "$DOWNLOAD_URL" ]]; then
        echo "No stable Paper build found for $MINECRAFT_VERSION." >&2
        exit 1
    fi

    curl -fsSL -H "User-Agent: $USER_AGENT" "$DOWNLOAD_URL" -o "$PAPER_JAR"
fi

cp "$PLUGIN_JAR" "$PLUGIN_DIR/RevPrac.jar"
echo "eula=true" > "$SMOKE_DIR/eula.txt"
rm -f "$LOG_FILE" "$STDIN_PIPE"
mkfifo "$STDIN_PIPE"

(
    cd "$SMOKE_DIR"
    java -Xms512M -Xmx1G -jar "$PAPER_JAR" nogui < "$STDIN_PIPE" > "$LOG_FILE" 2>&1
) &
SERVER_PID="$!"

exec 3>"$STDIN_PIPE"

cleanup() {
    if kill -0 "$SERVER_PID" 2>/dev/null; then
        printf 'stop\n' >&3 || true
        sleep 5 || true
        kill "$SERVER_PID" 2>/dev/null || true
    fi
    exec 3>&- || true
    rm -f "$STDIN_PIPE"
}
trap cleanup EXIT

DEADLINE=$((SECONDS + 90))
while ((SECONDS < DEADLINE)); do
    if grep -q "RevPrac enabled" "$LOG_FILE" 2>/dev/null; then
        printf 'stop\n' >&3
        wait "$SERVER_PID"
        trap - EXIT
        exec 3>&-
        rm -f "$STDIN_PIPE"
        echo "Paper smoke succeeded: RevPrac enabled on Paper $MINECRAFT_VERSION."
        exit 0
    fi

    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        cat "$LOG_FILE" >&2 || true
        echo "Paper server exited before RevPrac enabled." >&2
        exit 1
    fi

    sleep 2
done

cat "$LOG_FILE" >&2 || true
echo "Timed out waiting for RevPrac to enable on Paper $MINECRAFT_VERSION." >&2
exit 1
