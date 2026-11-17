#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-8080}"
DATA_DIR="${DATA_DIR:-$(cd "$(dirname "$0")" && pwd)/data}"
IMAGE="${IMAGE:-nvd-local-server}"
NAME="nvd-server-$PORT"

if ! [[ "$PORT" =~ ^[0-9]+$ ]] || (( PORT < 1 || PORT > 65535 )); then
  echo "Usage: $0 [port]   (default 8080)" >&2
  exit 1
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Image '$IMAGE' not found. Build it with: ./gradlew jibDockerBuild" >&2
  exit 1
fi

mkdir -p "$DATA_DIR"

if docker ps -a --format '{{.Names}}' | grep -qx "$NAME"; then
  docker rm -f "$NAME" >/dev/null
fi

args=(-d --name "$NAME" --user "$(id -u):$(id -g)" -p "$PORT:8080" -v "$DATA_DIR:/app/data")
if [ -n "${NVD_API_KEY:-}" ]; then
  args+=(-e NVD_API_KEY)
fi

docker run "${args[@]}" "$IMAGE" >/dev/null

echo "Started $NAME on http://localhost:$PORT (data: $DATA_DIR)"
echo "Logs: docker logs -f $NAME   Stop: docker rm -f $NAME"
