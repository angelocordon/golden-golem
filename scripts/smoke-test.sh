#!/usr/bin/env bash
# Boots a real Paper server with the plugin jar and fails if the plugin doesn't load cleanly.
#
# Usage:
#   scripts/smoke-test.sh resolve            Print the latest Paper build id (and write it to
#                                            $GITHUB_OUTPUT as build=<id> when set), for cache keys.
#   scripts/smoke-test.sh run <plugin.jar> [<testsupport.jar>]
#                                            Download Paper, boot it with the plugin, check the log.
#                                            With the test-support jar, also runs the mining scenario
#                                            (a golem mines stone and fills its golden chest).
#
# Environment:
#   PAPER_VERSION    Minecraft version to test against (default 26.3).
#   PAPER_CACHE_DIR  Where downloaded server jars are kept between runs (default ~/.cache/paper).
#   SMOKE_DIR        Server working directory (default ./smoke-server). Wiped on each run.
#   SMOKE_TIMEOUT    Hard limit in seconds for the whole server run (default 420).
#   SCENARIO_TIMEOUT Seconds to wait for the mining scenario's result (default 200).
set -euo pipefail
# Let failures inside $(...) stop the script too (bash 4.4+; CI runners have it).
shopt -s inherit_errexit 2>/dev/null || true

PAPER_VERSION="${PAPER_VERSION:-26.3}"
PAPER_CACHE_DIR="${PAPER_CACHE_DIR:-$HOME/.cache/paper}"
SMOKE_DIR="${SMOKE_DIR:-$PWD/smoke-server}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-420}"
PLUGIN_NAME="GoldenGolem"
PLUGIN_PACKAGE='dev\.angelocordon\.goldengolem'

# Fill (v3) is PaperMC's downloads API; it asks clients to send a descriptive User-Agent.
FILL_API="https://fill.papermc.io/v3"
USER_AGENT="golden-golem-ci/1.0 (https://github.com/angelocordon/golden-golem)"

die() {
    echo "::error::$*" >&2
    exit 1
}

# Fetches a URL from the Fill API, failing with the HTTP status and body instead of a bare curl error.
fill_get() {
    local url="$1" body status
    body="$(mktemp)"
    status="$(curl -sS -L -A "$USER_AGENT" -o "$body" -w '%{http_code}' "$url")" \
        || die "Could not reach the PaperMC Fill API at $url"
    if [[ "$status" != "200" ]]; then
        echo "Response body:" >&2
        head -c 2000 "$body" >&2 || true
        echo >&2
        rm -f "$body"
        die "PaperMC Fill API returned HTTP $status for $url"
    fi
    cat "$body"
    rm -f "$body"
}

# Prints the JSON of the latest stable build for PAPER_VERSION.
latest_build_json() {
    local json
    json="$(fill_get "$FILL_API/projects/paper/versions/$PAPER_VERSION/builds/latest")"
    jq -e '.id and .downloads["server:default"].url and .downloads["server:default"].checksums.sha256' \
        >/dev/null <<<"$json" \
        || die "Unexpected Fill API response for Paper $PAPER_VERSION (API changed?): $json"
    echo "$json"
}

cmd_resolve() {
    local build
    build="$(latest_build_json | jq -r '.id')"
    echo "Latest Paper $PAPER_VERSION build: $build"
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        echo "build=$build" >>"$GITHUB_OUTPUT"
    fi
}

# Downloads (or reuses from cache) the latest server jar and prints its path.
fetch_paper() {
    local json name url sha jar
    json="$(latest_build_json)"
    name="$(jq -r '.downloads["server:default"].name' <<<"$json")"
    url="$(jq -r '.downloads["server:default"].url' <<<"$json")"
    sha="$(jq -r '.downloads["server:default"].checksums.sha256' <<<"$json")"
    mkdir -p "$PAPER_CACHE_DIR"
    jar="$PAPER_CACHE_DIR/$name"

    if [[ -f "$jar" ]] && echo "$sha  $jar" | sha256sum -c --status; then
        echo "Using cached $name" >&2
    else
        echo "Downloading $name from $url" >&2
        curl -fsS -L -A "$USER_AGENT" -o "$jar.part" "$url" || die "Failed to download $url"
        echo "$sha  $jar.part" | sha256sum -c --status \
            || die "Checksum mismatch for $name (expected $sha)"
        mv "$jar.part" "$jar"
    fi
    echo "$jar"
}

write_server_files() {
    echo "eula=true" >eula.txt
    cat >server.properties <<'EOF'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
view-distance=2
simulation-distance=2
spawn-protection=0
max-players=1
server-port=25565
enable-rcon=false
enable-query=false
sync-chunk-writes=false
EOF
}

# Prints the end of the log plus any lines around plugin mentions, for failure diagnosis.
print_log_context() {
    local log="$1"
    echo "----- log lines mentioning $PLUGIN_NAME or its package (with context) -----"
    grep -n -i -E -B2 -A25 "$PLUGIN_NAME|golden-golem|$PLUGIN_PACKAGE" "$log" | head -n 300 || true
    echo "----- last 60 log lines -----"
    tail -n 60 "$log" || true
    echo "--------------------------------"
}

cmd_run() {
    local plugin_jar="${1:-}" support_jar="${2:-}"
    [[ -n "$plugin_jar" ]] || die "Usage: $0 run <plugin.jar> [<testsupport.jar>]"
    [[ -f "$plugin_jar" ]] || die "Plugin jar not found: $plugin_jar"
    plugin_jar="$(cd "$(dirname "$plugin_jar")" && pwd)/$(basename "$plugin_jar")"
    if [[ -n "$support_jar" ]]; then
        [[ -f "$support_jar" ]] || die "Test-support jar not found: $support_jar"
        support_jar="$(cd "$(dirname "$support_jar")" && pwd)/$(basename "$support_jar")"
    fi
    command -v java >/dev/null || die "java is not on PATH"
    java -version 2>&1 | head -n 1

    local paper_jar
    paper_jar="$(fetch_paper)"

    rm -rf "$SMOKE_DIR"
    mkdir -p "$SMOKE_DIR/plugins"
    cd "$SMOKE_DIR"
    cp "$paper_jar" paper.jar
    cp "$plugin_jar" plugins/golden-golem.jar
    [[ -z "$support_jar" ]] || cp "$support_jar" plugins/golden-golem-testsupport.jar
    write_server_files

    local raw_log="$SMOKE_DIR/server-console.log" log="$SMOKE_DIR/server.log"
    : >"$raw_log"

    # The fifo is the server's stdin; holding fd 3 open read-write keeps it from seeing EOF
    # until we send "stop".
    mkfifo console.in
    exec 3<>console.in

    echo "Starting Paper (timeout ${SMOKE_TIMEOUT}s)..."
    timeout --kill-after=20 "$SMOKE_TIMEOUT" java -Xmx2G -jar paper.jar --nogui \
        <console.in >"$raw_log" 2>&1 &
    local pid=$!

    local done_ok=0
    while kill -0 "$pid" 2>/dev/null; do
        if grep -q 'Done (' "$raw_log"; then
            done_ok=1
            break
        fi
        sleep 2
    done

    if [[ "$done_ok" == 1 && -n "$support_jar" ]]; then
        echo "Server reached Done; running the mining scenario (timeout ${SCENARIO_TIMEOUT:-200}s)."
        echo "ggtest mining" >&3
        local waited=0
        until grep -q 'GGTEST \(PASS\|FAIL\)' "$raw_log"; do
            kill -0 "$pid" 2>/dev/null || break
            ((waited >= ${SCENARIO_TIMEOUT:-200})) && break
            sleep 2
            waited=$((waited + 2))
        done
    fi

    if [[ "$done_ok" == 1 ]]; then
        echo "Sending stop."
        echo "stop" >&3
    else
        echo "Server exited or timed out before reaching Done."
    fi

    local exit_code=0
    wait "$pid" || exit_code=$?
    exec 3>&-
    echo "Server process exited with code $exit_code."

    # Strip ANSI colour codes so the checks and the uploaded log are plain text.
    sed -E 's/\x1B\[[0-9;]*[A-Za-z]//g' "$raw_log" >"$log"

    local failures=()
    [[ "$done_ok" == 1 ]] || failures+=("server never reached 'Done (' (exit code $exit_code)")
    grep -q "Error occurred while enabling $PLUGIN_NAME" "$log" \
        && failures+=("log contains 'Error occurred while enabling $PLUGIN_NAME'")
    grep -q "Could not load 'plugins/golden-golem.jar'" "$log" \
        && failures+=("log contains \"Could not load 'plugins/golden-golem.jar'\"")
    # Frames look like "at golden-golem.jar//dev.angelocordon.goldengolem.X.y(...)", so allow a
    # classloader/module prefix; also catch "Caused by: dev.angelocordon.goldengolem.SomeException".
    grep -q -E "(at ([^ ]*/)?|Caused by: )$PLUGIN_PACKAGE\." "$log" \
        && failures+=("log contains a stack trace through $PLUGIN_PACKAGE")
    grep -q "Enabling $PLUGIN_NAME" "$log" \
        || failures+=("plugin was never enabled (no 'Enabling $PLUGIN_NAME' line)")

    if [[ -n "$support_jar" ]]; then
        grep -q 'GGTEST PASS mining' "$log" \
            || failures+=("mining scenario did not pass: $(grep -m1 'GGTEST FAIL' "$log" || echo 'no result line')")
    fi

    if ((${#failures[@]} > 0)); then
        print_log_context "$log"
        local f
        for f in "${failures[@]}"; do
            echo "::error::Smoke test failed: $f"
        done
        exit 1
    fi

    grep -m1 "Enabling $PLUGIN_NAME" "$log"
    grep -m1 'Done (' "$log"
    [[ -z "$support_jar" ]] || grep -m1 'GGTEST PASS mining' "$log"
    echo "Smoke test passed: $PLUGIN_NAME loaded and enabled on Paper $PAPER_VERSION."
}

case "${1:-}" in
    resolve) cmd_resolve ;;
    run) shift; cmd_run "$@" ;;
    *) die "Usage: $0 resolve | run <plugin.jar> [<testsupport.jar>]" ;;
esac
