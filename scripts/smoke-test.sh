#!/usr/bin/env bash
# End-to-end smoke test of a running ERP stack (docker compose up). It drives the real HTTP APIs with curl and checks
# the flows that cross service boundaries, including the RabbitMQ event flows (document upload -> order import and
# notification, task completion -> notification) and MinIO presigned uploads.
#
#   ./scripts/smoke-test.sh                # uses ports/credentials from ./.env, falling back to the defaults
#   VERBOSE=1 ./scripts/smoke-test.sh      # also prints every request and a trimmed response (a transcript)
#
# Needs: bash, curl, jq, python3.  Safe to re-run against the same stack (it uses unique SKUs / emails).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then set -a; source "$ROOT/.env"; set +a; fi

USER_URL="${USER_URL:-http://localhost:${USER_SERVICE_PORT:-41801}}"
ITEM_URL="${ITEM_URL:-http://localhost:${ITEM_SERVICE_PORT:-41802}}"
INVENTORY_URL="${INVENTORY_URL:-http://localhost:${INVENTORY_SERVICE_PORT:-41803}}"
TASK_URL="${TASK_URL:-http://localhost:${TASK_SERVICE_PORT:-41805}}"
NOTIFICATION_URL="${NOTIFICATION_URL:-http://localhost:${NOTIFICATION_SERVICE_PORT:-41806}}"
ADMIN_EMAIL="${ADMIN_EMAIL:-${ERP_ADMIN_EMAIL:-admin@erp.local}}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-${ERP_ADMIN_PASSWORD:?set ADMIN_PASSWORD or ERP_ADMIN_PASSWORD}}"
VERBOSE="${VERBOSE:-0}"

RUN="$(date +%s)$RANDOM"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/pu-erp-smoke-XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
STATUS=""; BODY=""
: > "$TMP/checks"

# progress goes to stderr so helper functions can return values on stdout
say()  { printf '\n\033[1m== %s\033[0m\n' "$*" >&2; }
ok()   { printf . >> "$TMP/checks"; printf '  \033[32mok\033[0m  %s\n' "$*" >&2; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$*" >&2; [[ -n "$BODY" ]] && printf '       last response (%s): %s\n' "$STATUS" "$(redact "$BODY")" >&2; exit 1; }

# Never put passwords, JWTs or presigned storage credentials in captured evidence.
redact() {
  printf '%s' "$1" | jq -c 'walk(if type == "object" then with_entries(if (.key | test("password|accessToken|uploadUrl|^url$"; "i")) then .value = "[REDACTED]" else . end) else . end)' 2>/dev/null || printf '[non-JSON response]'
}

# req METHOD URL [TOKEN] [JSON_BODY]  -> sets STATUS and BODY
req() {
  local method="$1" url="$2" token="${3:-}" data="${4:-}" args=(--connect-timeout 5 --max-time 20 -sS -o "$TMP/body" -w '%{http_code}' -X "$1")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  STATUS="$(curl "${args[@]}" "$url")" || fail "curl failed: $method $url"
  BODY="$(cat "$TMP/body")"
  if [[ "$VERBOSE" == "1" ]]; then
    printf '  \033[2m$ %s %s%s\n    -> %s %s\033[0m\n' "$method" "$url" "${data:+  $(redact "$data")}" "$STATUS" "$(redact "$BODY" | tr -d '\n' | cut -c1-500)" >&2
  fi
}
expect() { [[ "$STATUS" == "$1" ]] || fail "$2: expected HTTP $1, got $STATUS"; ok "$2 (HTTP $STATUS)"; }
jqe()    { printf '%s' "$BODY" | jq -e "$1" >/dev/null || fail "$2 (jq: $1)"; ok "$2"; }
val()    { printf '%s' "$BODY" | jq -r "$1"; }

wait_for() { # wait_for DESCRIPTION COMMAND... (retries for ~60 s)
  local what="$1"; shift
  for _ in $(seq 1 60); do if "$@" >/dev/null 2>&1; then ok "$what"; return 0; fi; sleep 1; done
  fail "timed out waiting for: $what"
}

# ------------------------------------------------------------------------------------------------------------------
say "1. Services are up"
for pair in "user-service:$USER_URL" "item-service:$ITEM_URL" "inventory-service:$INVENTORY_URL" \
            "task-service:$TASK_URL" "notification-service:$NOTIFICATION_URL"; do
  wait_for "${pair%%:*} is healthy" bash -c "curl -fsS '${pair#*:}/actuator/health' | jq -e '.status==\"UP\"'"
done

say "2. Authentication"
req POST "$USER_URL/auth/login" "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
expect 200 "admin can log in"
TOKEN="$(val .accessToken)"
PAYLOAD="$(printf '%s' "$TOKEN" | cut -d. -f2 | tr '_-' '/+')"
CLAIMS="$(printf '%s%s' "$PAYLOAD" "$(printf '%*s' $(( (4 - ${#PAYLOAD} % 4) % 4 )) '' | tr ' ' '=')" | base64 -d 2>/dev/null | jq -c . || true)"
[[ "$VERBOSE" == "1" ]] && printf '  \033[2mtoken claims: %s\033[0m\n' "$CLAIMS" >&2
printf '%s' "$CLAIMS" | jq -e '.roles | index("ROLE_ADMIN")' >/dev/null || fail "token lacks ROLE_ADMIN"
ok "token carries ROLE_ADMIN"
req GET "$USER_URL/api/users/me" "$TOKEN"; expect 200 "GET /api/users/me"
ME="$(val .id)"
req POST "$USER_URL/auth/login" "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"definitely-wrong\"}"; expect 401 "wrong password is rejected"
req GET "$ITEM_URL/api/items"; expect 401 "item-service rejects requests without a token"
req GET "$ITEM_URL/api/items" "not.a.token"; expect 401 "item-service rejects a forged token"
req GET "$ITEM_URL/api/items" "$TOKEN"; expect 200 "token issued by user-service is accepted by item-service"
req GET "$INVENTORY_URL/api/inventory/stocks/$ME" "$TOKEN"; expect 404 "token is accepted by inventory-service (404 = no such stock)"

BOB_EMAIL="bob-$RUN@erp.test"
req POST "$USER_URL/api/users/createUser" "$TOKEN" "{\"name\":\"Bob\",\"email\":\"$BOB_EMAIL\",\"password\":\"bob-password-1\"}"
expect 201 "admin creates a user"
req POST "$USER_URL/auth/login" "" "{\"email\":\"$BOB_EMAIL\",\"password\":\"bob-password-1\"}"; BOB="$(val .accessToken)"
req GET "$USER_URL/api/users" "$BOB"; expect 403 "ordinary user cannot list users"

# ------------------------------------------------------------------------------------------------------------------
say "3. Items, units of measure and a BOM (item-service)"
req GET "$ITEM_URL/api/uoms" "$TOKEN"
EA="$(printf '%s' "$BODY" | jq -r '.[] | select(.code=="EA") | .id')"; KG="$(printf '%s' "$BODY" | jq -r '.[] | select(.code=="KG") | .id')"
[[ -n "$EA" && -n "$KG" ]] || fail "missing EA/KG seed units"
ok "EA and KG units are seeded"
mk_item() { # mk_item SKU TYPE UOM -> prints id
  req POST "$ITEM_URL/api/items" "$TOKEN" "{\"skuCode\":\"$1\",\"name\":\"Smoke $1\",\"itemType\":\"$2\",\"baseUomId\":\"$3\"}"
  if [[ "$STATUS" == 409 ]]; then req GET "$ITEM_URL/api/items/sku/$1" "$TOKEN"; else expect 201 "create item $1"; fi
  val .id
}
FG_SKU="SMOKE-FG-$RUN"; STEEL_SKU="SMOKE-STEEL-$RUN"; BOLT_SKU="SMOKE-BOLT-$RUN"
FG="$(mk_item "$FG_SKU" FG "$EA")"; STEEL="$(mk_item "$STEEL_SKU" RAW "$KG")"; BOLT="$(mk_item "$BOLT_SKU" RAW "$EA")"
req POST "$ITEM_URL/api/items" "$TOKEN" "{\"skuCode\":\"$FG_SKU\",\"name\":\"dup\",\"itemType\":\"FG\",\"baseUomId\":\"$EA\"}"
expect 409 "duplicate SKU is a conflict"

req GET "$ITEM_URL/api/items?keyword=SMOKE-FG&sort=skuCode,desc" "$TOKEN"; expect 200 "items can be searched and sorted"
req GET "$ITEM_URL/api/items?sort=bogus" "$TOKEN"; expect 400 "an unknown sort property is a 400, not a 500"

req POST "$ITEM_URL/api/boms" "$TOKEN" "{\"productItemId\":\"$FG\",\"revision\":\"A\",\"lines\":[{\"componentItemId\":\"$STEEL\",\"qtyPer\":2.5,\"scrapRate\":0.2},{\"componentItemId\":\"$BOLT\",\"qtyPer\":4}]}"
expect 201 "create DRAFT BOM with two lines"; BOM="$(val .id)"
req GET "$ITEM_URL/api/boms/$BOM/requirements?quantity=10" "$TOKEN"
jqe '.[0].netQuantity==25 and .[0].grossQuantity==31.25 and .[1].grossQuantity==40' "requirements for 10 units: 25 kg steel net, 31.25 gross after 20% scrap, 40 bolts"
req PUT "$ITEM_URL/api/boms/$BOM" "$TOKEN" '{"status":"ACTIVE"}'; expect 200 "activate the BOM"
req POST "$ITEM_URL/api/boms/$BOM/lines" "$TOKEN" "{\"componentItemId\":\"$BOLT\",\"qtyPer\":1}"; expect 409 "an ACTIVE BOM is frozen"

# ------------------------------------------------------------------------------------------------------------------
say "4. Inventory movements (inventory-service)"
req POST "$INVENTORY_URL/api/inventory/stocks/$STEEL/transactions" "$TOKEN" '{"type":"OUT","quantity":1}'
expect 404 "cannot take stock out of an item that was never stocked"
req POST "$INVENTORY_URL/api/inventory/stocks/$STEEL/transactions" "$TOKEN" '{"type":"IN","quantity":100}'; expect 201 "receive 100 kg steel"
req POST "$INVENTORY_URL/api/inventory/stocks/$STEEL/transactions" "$TOKEN" '{"type":"OUT","quantity":30.5}'; expect 201 "issue 30.5 kg"
req GET "$INVENTORY_URL/api/inventory/stocks/$STEEL" "$TOKEN"; jqe '.quantity==69.5' "stock is 69.5"
STOCK_ID="$(val .id)"
req POST "$INVENTORY_URL/api/inventory/stocks/$STEEL/transactions" "$TOKEN" '{"type":"OUT","quantity":500}'; expect 409 "cannot go below zero"
req GET "$INVENTORY_URL/api/inventory/stocks/$STOCK_ID/transactions" "$TOKEN"; jqe 'length==2 and .[0].transactionType=="OUT"' "ledger has both movements, newest first"
req POST "$INVENTORY_URL/api/inventory/stocks/$BOLT/transactions" "$TOKEN" '{"type":"IN","quantity":10}'; expect 201 "receive 10 bolts"

# ------------------------------------------------------------------------------------------------------------------
say "5. Orders confirm against live stock (item-service -> inventory-service over HTTP)"
req POST "$ITEM_URL/api/orders" "$TOKEN" "{\"items\":[{\"sku\":\"$STEEL_SKU\",\"quantity\":5,\"unitPriceCents\":1200}]}"
expect 201 "create an order by SKU"; jqe '.totalAmountCents==6000 and .status=="NEW"' "total is 6000 cents"; ORDER_OK="$(val .id)"
req PATCH "$ITEM_URL/api/orders/$ORDER_OK/status" "$TOKEN" '{"status":"CONFIRMED"}'; expect 200 "order within stock is confirmed"
req POST "$ITEM_URL/api/orders" "$TOKEN" "{\"items\":[{\"sku\":\"$BOLT_SKU\",\"quantity\":50,\"unitPriceCents\":10}]}"; ORDER_BIG="$(val .id)"
req PATCH "$ITEM_URL/api/orders/$ORDER_BIG/status" "$TOKEN" '{"status":"CONFIRMED"}'
expect 409 "order beyond stock cannot be confirmed"; jqe '.detail | test("available 10, required 50")' "the reply names the shortage"
req GET "$ITEM_URL/api/orders/$ORDER_OK" "$BOB"; expect 403 "another user cannot read the order"

# ------------------------------------------------------------------------------------------------------------------
say "6. Document upload -> MinIO -> RabbitMQ -> notification-service / order import"
NOTE="smoke test drawing $RUN"; printf '%s' "$NOTE" > "$TMP/drawing.txt"; SIZE="$(wc -c < "$TMP/drawing.txt")"
req POST "$ITEM_URL/api/documents/$FG/images:init" "$TOKEN" "{\"filename\":\"drawing-$RUN.txt\",\"contentType\":\"text/plain\",\"sizeBytes\":$SIZE,\"linkType\":\"ITEM\"}"
expect 200 "init upload (presigned PUT URL)"; DOC="$(val .documentId)"; UPLOAD_URL="$(val .uploadUrl)"
req POST "$ITEM_URL/api/documents/$FG/images:complete" "$TOKEN" "{\"documentId\":\"$DOC\"}"; expect 409 "cannot complete before the file is uploaded"
req POST "$ITEM_URL/api/documents/$FG/images:complete" "$BOB" "{\"documentId\":\"$DOC\"}"; expect 403 "another user cannot complete the upload"
CODE="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT --data-binary @"$TMP/drawing.txt" "$UPLOAD_URL")"
[[ "$CODE" == 200 ]] && ok "PUT straight to MinIO with the presigned URL (HTTP 200)" || fail "presigned PUT returned HTTP $CODE"
req POST "$ITEM_URL/api/documents/$FG/images:complete" "$TOKEN" "{\"documentId\":\"$DOC\"}"; expect 200 "complete upload"
req GET "$ITEM_URL/api/documents?itemId=$FG" "$TOKEN"; jqe "length==1 and .[0].status==\"APPROVED\"" "document is attached to the item and APPROVED"
req GET "$ITEM_URL/api/documents/documents/$DOC:download-url" "$TOKEN"; expect 200 "presigned download URL"
[[ "$(curl -sS "$(val .url)")" == "$NOTE" ]] && ok "downloaded bytes equal the uploaded bytes" || fail "download mismatch"

poll_notifications() { # poll_notifications JQ_FILTER DESCRIPTION
  for _ in $(seq 1 30); do
    req GET "$NOTIFICATION_URL/api/notifications?status=UNREAD&size=50" "$TOKEN"
    if printf '%s' "$BODY" | jq -e "$1" >/dev/null; then ok "$2"; return 0; fi; sleep 1
  done
  fail "$2 (no matching notification after 30 s)"
}
poll_notifications ".content | map(select(.type==\"DOCUMENT_UPLOADED\" and .links[0].refId==\"$DOC\")) | length==1" \
  "RabbitMQ delivered the upload event: notification-service created a DOCUMENT_UPLOADED notification"

IMP_A="SMOKE-IMPORT-A-$RUN"; IMP_B="SMOKE-IMPORT-B-$RUN"; mk_item "$IMP_A" RAW "$EA" >/dev/null; mk_item "$IMP_B" RAW "$EA" >/dev/null
python3 "$ROOT/scripts/make_order_xlsx.py" "$TMP/po.xlsx" "$IMP_A:4:250" "$IMP_B:10:15"
XSIZE="$(wc -c < "$TMP/po.xlsx")"; BATCH="$(cat /proc/sys/kernel/random/uuid)"
req POST "$ITEM_URL/api/documents/$BATCH/images:init" "$TOKEN" "{\"filename\":\"po-$RUN.xlsx\",\"sizeBytes\":$XSIZE,\"linkType\":\"ORDER\"}"
expect 200 "init upload of an order spreadsheet"; PO_DOC="$(val .documentId)"
curl -sS -o /dev/null -X PUT --data-binary @"$TMP/po.xlsx" "$(val .uploadUrl)"
req POST "$ITEM_URL/api/documents/$BATCH/images:complete" "$TOKEN" "{\"documentId\":\"$PO_DOC\"}"; expect 200 "complete the spreadsheet upload"
for _ in $(seq 1 30); do
  req GET "$ITEM_URL/api/orders?size=100" "$TOKEN"
  printf '%s' "$BODY" | jq -e ".content | map(select(.documentIds | index(\"$PO_DOC\"))) | length==1" >/dev/null && break; sleep 1
done
jqe ".content | map(select(.documentIds | index(\"$PO_DOC\"))) | .[0] | .totalAmountCents==1150 and (.items|length)==2" \
  "RabbitMQ delivered the upload event: item-service imported the spreadsheet as an order (1150 cents, 2 lines)"

req POST "$ITEM_URL/api/orders/$PO_DOC/import" "$BOB"; expect 403 "another user cannot import the spreadsheet"

# ------------------------------------------------------------------------------------------------------------------
say "7. Tasks with stock prerequisites (task-service -> user/item/inventory-service) and task.completed -> notification"
req POST "$TASK_URL/api/tasks" "$TOKEN" "{\"title\":\"Cut steel $RUN\",\"priority\":\"HIGH\",\"assigneeUserId\":\"$ME\",\"prerequisiteItemId\":\"$STEEL\",\"prerequisiteItemQuantity\":20}"
expect 201 "create a task that needs 20 kg steel"; TASK="$(val .id)"
req POST "$TASK_URL/api/tasks" "$TOKEN" "{\"title\":\"bad\",\"assigneeUserId\":\"$(cat /proc/sys/kernel/random/uuid)\"}"; expect 400 "unknown assignee is rejected (user-service lookup)"
req POST "$TASK_URL/api/tasks" "$TOKEN" "{\"title\":\"bad\",\"prerequisiteItemId\":\"$(cat /proc/sys/kernel/random/uuid)\",\"prerequisiteItemQuantity\":1}"
expect 400 "unknown prerequisite item is rejected (item-service lookup)"
req PATCH "$TASK_URL/api/tasks/$TASK/status" "$TOKEN" '{"status":"DONE"}'; expect 409 "OPEN -> DONE is not a valid transition"
req PATCH "$TASK_URL/api/tasks/$TASK/status" "$TOKEN" '{"status":"IN_PROGRESS"}'; expect 200 "start: 69.5 kg in stock covers the 20 kg prerequisite (inventory-service lookup)"
req POST "$TASK_URL/api/tasks" "$TOKEN" "{\"title\":\"Too big $RUN\",\"assigneeUserId\":\"$ME\",\"prerequisiteItemId\":\"$STEEL\",\"prerequisiteItemQuantity\":1000}"
BIG_TASK="$(val .id)"
req PATCH "$TASK_URL/api/tasks/$BIG_TASK/status" "$TOKEN" '{"status":"IN_PROGRESS"}'; expect 409 "cannot start a task whose prerequisite stock is missing"
req POST "$TASK_URL/api/tasks/$TASK/links" "$TOKEN" "{\"refType\":\"ITEM\",\"refId\":\"$STEEL\",\"refMeta\":\"{\\\"note\\\":\\\"raw material\\\"}\"}"; expect 201 "link the task to the steel item (jsonb meta)"
req PATCH "$TASK_URL/api/tasks/$TASK/status" "$TOKEN" '{"status":"DONE"}'; expect 200 "complete the task"
poll_notifications ".content | map(select(.type==\"TASK_COMPLETED\" and .links[0].refId==\"$TASK\")) | length==1" \
  "RabbitMQ delivered task.completed: notification-service created a TASK_COMPLETED notification"
NID="$(printf '%s' "$BODY" | jq -r ".content[] | select(.type==\"TASK_COMPLETED\" and .links[0].refId==\"$TASK\") | .id")"
req PATCH "$NOTIFICATION_URL/api/notifications/$NID/read" "$TOKEN" '{"read":true}'; jqe '.status=="READ"' "mark the notification as read"
req GET "$NOTIFICATION_URL/api/notifications/$NID" "$BOB"; expect 403 "another user cannot read the notification"

# ------------------------------------------------------------------------------------------------------------------
say "8. Logout revokes a token; OpenAPI is published by every service"
req POST "$USER_URL/auth/login" "" "{\"email\":\"$BOB_EMAIL\",\"password\":\"bob-password-1\"}"; BOB2="$(val .accessToken)"
req GET "$USER_URL/api/users/me" "$BOB2"; expect 200 "fresh token works"
req POST "$USER_URL/auth/logout" "$BOB2"; expect 204 "logout"
req GET "$USER_URL/api/users/me" "$BOB2"; expect 401 "revoked token is rejected by user-service"
for pair in "user-service:$USER_URL" "item-service:$ITEM_URL" "inventory-service:$INVENTORY_URL" \
            "task-service:$TASK_URL" "notification-service:$NOTIFICATION_URL"; do
  req GET "${pair#*:}/v3/api-docs"; expect 200 "${pair%%:*} serves /v3/api-docs"
  jqe '.paths | length > 0' "${pair%%:*} documents its endpoints"
  req GET "${pair#*:}/swagger-ui/index.html"; expect 200 "${pair%%:*} serves Swagger UI"
done

printf '\n\033[1;32mSMOKE TEST PASSED\033[0m (%d checks)\n' "$(wc -c < "$TMP/checks")"
