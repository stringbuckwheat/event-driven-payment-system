#!/usr/bin/env bash
set -euo pipefail

# Kafka Topic Bootstrap Script
# 토픽 규약 고정 목적
# auto-create으로 토픽을 만들어도 파티션 수, retention 정책은 명시적 설정
#
# 사용법:
# ./scripts/kafka/create-topics.sh
# cf) 실행 권한
# chmod +x scripts/kafka/create-topics.sh
#
# 전제:
# docker-compose.yml에 아래 컨테이너가 존재해야한다
# Kafka broker container name: broker
# (broker 컨테이너 내부에서 kafka-topics.sh를 실행)

# 설정값
BROKER_CONTAINER_NAME="${BROKER_CONTAINER_NAME:-broker}"
BOOTSTRAP_SERVERS="${BOOTSTRAP_SERVERS:-broker:29092}"
PARTITIONS="${PARTITIONS:-1}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-1}"

ONE_DAY_MS=$((24 * 60 * 60 * 1000))
THREE_DAYS_MS=$((3 * ONE_DAY_MS))
SEVEN_DAYS_MS=$((7 * ONE_DAY_MS))
FOURTEEN_DAYS_MS=$((14 * ONE_DAY_MS))

# ---------------------------------------------
# 토픽 규약
## 결제 요청 커맨드: PG 호출
TOPIC_PAYMENT_COMMAND_REQUESTED="payment.command.requested"
## 결제 성공 이벤트: 주문 상태 전이, 후처리(알림 등)
TOPIC_PAYMENT_EVENT_SUCCEEDED="payment.event.succeeded"
## 결제 실패 이벤트: 재고 롤백, 실패 사유 기록, 후처리(알림 등)
TOPIC_PAYMENT_EVENT_FAILED="payment.event.failed"
## 결제 요청 DLQ: 반복 실패/역직렬화 오류 등 정상 처리가 불가능한 메시지
TOPIC_PAYMENT_COMMAND_REQUESTED_DLQ="payment.command.requested.dlq"

# ---------------------------------------------
# 보관 기간 정책
RETENTION_COMMAND_MS="${RETENTION_COMMAND_MS:-$THREE_DAYS_MS}"
RETENTION_EVENT_MS="${RETENTION_EVENT_MS:-$SEVEN_DAYS_MS}"
RETENTION_DLQ_MS="${RETENTION_DLQ_MS:-$FOURTEEN_DAYS_MS}"

# ---------------------------------------------
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

kafka_topics() {
  docker exec -i "${BROKER_CONTAINER_NAME}" bash -lc "${KAFKA_BIN}/kafka-topics.sh $*"
}

# 컨테이너가 떠 있는지 빠른 검증
if ! docker ps --format '{{.Names}}' | grep -q "^${BROKER_CONTAINER_NAME}$"; then
  echo "[ERROR] Kafka broker container '${BROKER_CONTAINER_NAME}' is not running."
  echo "        Run: docker compose up -d"
  exit 1
fi

echo "============================================================"
echo "[INFO] Creating Kafka topics (bootstrap servers: ${BOOTSTRAP_SERVERS})"
echo "[INFO] partitions=${PARTITIONS}, replication-factor=${REPLICATION_FACTOR}"
echo "============================================================"

# ------------------------------------------------------------
# 토픽 생성 헬퍼
# - --if-not-exists: 이미 존재하면 실패하지 않고 넘어감
# - --config retention.ms=... : 보관 기간 설정
# ------------------------------------------------------------
create_topic() {
  local topic="$1"
  local retention_ms="$2"

  echo "[INFO] create topic: ${topic} (retention.ms=${retention_ms})"

  kafka_topics "--bootstrap-server ${BOOTSTRAP_SERVERS} \
    --create \
    --if-not-exists \
    --topic ${topic} \
    --partitions ${PARTITIONS} \
    --replication-factor ${REPLICATION_FACTOR} \
    --config retention.ms=${retention_ms}"
}

# command topics
create_topic "${TOPIC_PAYMENT_COMMAND_REQUESTED}" "${RETENTION_COMMAND_MS}"

# event topics
create_topic "${TOPIC_PAYMENT_EVENT_SUCCEEDED}" "${RETENTION_EVENT_MS}"
create_topic "${TOPIC_PAYMENT_EVENT_FAILED}" "${RETENTION_EVENT_MS}"

# dlq topics
create_topic "${TOPIC_PAYMENT_COMMAND_REQUESTED_DLQ}" "${RETENTION_DLQ_MS}"

echo
echo "============================================================"
echo "[INFO] Topic list:"
kafka_topics "--bootstrap-server ${BOOTSTRAP_SERVERS} --list" | grep "^payment\." || true
echo "============================================================"
echo "[DONE] Kafka topics are ready."