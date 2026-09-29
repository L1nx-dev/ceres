#!/run/current-system/sw/bin/zsh
set -euo pipefail

usage() {
    cat <<EOF
Usage:
  $(basename "$0") [ACTION]
  $(basename "$0") --arg ACTION
  $(basename "$0") -a ACTION

Gradle actions:
  run       Run the application (default)
  build     Build the project
  clean     Clean build artifacts
  test      Run tests
  check     Run all verification tasks
  assemble  Assemble project outputs
  tasks     List available Gradle tasks
  help      Show this help message
EOF
}

action="run"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --arg|-a)
            if [[ $# -lt 2 ]]; then
                echo "Error: $1 requires an argument." >&2
                usage
                exit 1
            fi
            action="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        -*)
            echo "Error: unknown option: $1" >&2
            usage
            exit 1
            ;;
        *)
            if [[ "$action" != "run" ]]; then
                echo "Error: action specified more than once." >&2
                usage
                exit 1
            fi
            action="$1"
            shift
            ;;
    esac
done

case "$action" in
    run|build|clean|test|check|assemble|tasks)
        ;;
    help)
        usage
        exit 0
        ;;
    *)
        echo "Error: unsupported Gradle action: $action" >&2
        usage
        exit 1
        ;;
esac

java_bin="$(command -v java)"
if [[ -z "$java_bin" ]]; then
    echo "Error: java was not found in PATH." >&2
    exit 1
fi

java_bin="$(readlink -f "$java_bin")"
export JAVA_HOME="$(dirname "$(dirname "$java_bin")")"

exec gradle "$action"
