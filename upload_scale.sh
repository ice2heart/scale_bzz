#!/usr/bin/env bash
# upload_scale.sh — загрузка /home/vsp/Документы/Scale в https://github.com/oblvsp/scale
# с прогресс-баром, проверкой статуса и аутентификацией (токен / логин+пароль / SSH).
# Использование:  ./upload_scale.sh            (при отсутствии токена запросит его)
#                 GITHUB_TOKEN=ghp_xxx ./upload_scale.sh   (без интерактива)
set -uo pipefail

SRC="/home/vsp/Документы/Scale"
REPO="https://github.com/oblvsp/scale.git"
BRANCH="main"
API_REPO="oblvsp/scale"

# ---------- цвета и вывод ----------
G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; C=$'\e[36m'; B=$'\e[1m'; N=$'\e[0m'
ok()   { echo "${G}[OK]${N}   $*"; }
warn() { echo "${Y}[!!]${N}   $*"; }
die()  { echo "${R}[FAIL]${N} $*" >&2; exit 1; }

TOTAL=6   # всего шагов
step() { echo "${B}${C}[$1/$TOTAL]${N} ${B}$2${N}"; }

# прогресс-бар по переданному числу процентов
bar() {
  local pct="${1:-0}" width=30 filled s="" i
  (( pct < 0 )) && pct=0; (( pct > 100 )) && pct=100
  filled=$(( pct * width / 100 ))
  for ((i=0;i<width;i++)); do (( i < filled )) && s+="#" || s+="."; done
  printf "\r    [%s] %3d%%" "$s" "$pct"
}

TMP="$(mktemp -d /tmp/scale-upload.XXXXXX)"
cleanup() {
  # вычищаем токен из временных файлов перед удалением
  shred -u "$TMP"/* 2>/dev/null || true
  rm -rf "$TMP"
}
trap cleanup EXIT

# ---------- шаг 1: проверка окружения ----------
step 1 "Проверка статуса (окружение, папка, git)"
for c in git curl; do command -v "$c" >/dev/null || die "Не найдена утилита: $c"; done
[ -d "$SRC" ] || die "Папка не найдена: $SRC"
git -C "$SRC" rev-parse --is-inside-work-tree &>/dev/null && \
  warn "Внутри '$SRC' есть git-репозиторий — копируются файлы без .git"
ok "Окружение готово"

# ---------- шаг 2: аутентификация ----------
step 2 "Аутентификация GitHub"
REMOTE="$REPO"
AUTH_MODE=""

if [[ "$REPO" == https://* ]]; then
  # 2a. Токен: из переменной или запросить интерактивно (скрытый ввод)
  if [ -z "${GITHUB_TOKEN:-}" ]; then
    echo "  Токен не задан (переменная GITHUB_TOKEN пуста)."
    read -rsp "  Введите Personal Access Token (или Enter — вход по логину/паролю или SSH): " GITHUB_TOKEN
    echo
  fi

  if [ -n "${GITHUB_TOKEN:-}" ]; then
    # проверяем токен через API ДО использования
    code=$(curl -s -o "$TMP/who.json" -w '%{http_code}' \
              -H "Authorization: Bearer $GITHUB_TOKEN" \
              "https://api.github.com/user")
    case "$code" in
      200)
        LOGIN=$(grep -o '"login": *"[^"]*"' "$TMP/who.json" | head -1 | cut -d'"' -f4)
        ok "Токен принят. Авторизован как: ${B}$LOGIN${N}" ;;
      401) die "Токен отклонён GitHub (HTTP 401). Проверьте срок действия и права (scope: repo)." ;;
      *)   warn "Не удалось проверить токен (HTTP $code) — пробуем использовать" ;;
    esac
    # права на конкретный репо
    code=$(curl -s -o /dev/null -w '%{http_code}' \
              -H "Authorization: Bearer $GITHUB_TOKEN" \
              "https://api.github.com/repos/$API_REPO")
    case "$code" in
      200) ok "Доступ к $API_REPO подтверждён (HTTP 200)" ;;
      404) warn "Репо не виден с этим токеном (404) — приватный или нет прав 'repo'" ;;
      401) die "Токен не авторизован для API (401)" ;;
      *)   warn "Код API: $code — продолжаем" ;;
    esac
    REMOTE="${REPO/https:\/\//https:\/\/x-access-token:${GITHUB_TOKEN}@}"
    AUTH_MODE="token"
  else
    # 2b. Без токена: если есть SSH-ключ — используем SSH
    if ssh -o BatchMode=yes -o ConnectTimeout=5 -T git@github.com 2>&1 | grep -q "successfully authenticated\|Hi .*! You've successfully"; then
      REMOTE="git@github.com:${API_REPO}.git"
      ok "Найден рабочий SSH-ключ GitHub — авторизация через SSH"
      AUTH_MODE="ssh"
    else
      # 2c. Логин + PAT-как-пароль через git credential helper (интерактивный ввод при пуше)
      warn "SSH-ключ не найден — используется интерактивный ввод логина и пароля (PAT)"
      echo "  Подсказка: пароль GitHub не принимает обычный пароль, нужен Personal Access Token."
      AUTH_MODE="interactive"
    fi
  fi
else
  REMOTE="$REPO"
  AUTH_MODE="ssh"
fi

# ---------- шаг 3: клонирование ----------
step 3 "Клонирование репозитория ($AUTH_MODE)"
bar 10
if ! git clone --quiet --branch "$BRANCH" --single-branch "$REMOTE" "$TMP/repo" 2>"$TMP/clone.err"; then
  bar 20
  if grep -qi "authentication\|403\|401" "$TMP/clone.err"; then
    bar 100; echo
    die "Ошибка аутентификации при клонировании: $(tail -n1 "$TMP/clone.err")"
  fi
  git init -q "$TMP/repo" || die "git init не удался"
  git -C "$TMP/repo" remote add origin "$REMOTE"
  git -C "$TMP/repo" checkout -q -B "$BRANCH"
  warn "Ветка '$BRANCH' пуста или недоступна — будет создана заново"
fi
bar 30
ok "Репозиторий склонирован"

# ---------- шаг 4: копирование файлов ----------
step 4 "Копирование содержимого папки"
mapfile -t FILES < <(find "$SRC" -type f ! -path "*/.git/*")
NF=${#FILES[@]}
[ "$NF" -gt 0 ] || die "В папке нет файлов: $SRC"
i=0
for f in "${FILES[@]}"; do
  rel="${f#"$SRC"/}"
  mkdir -p "$TMP/repo/$(dirname "$rel")"
  cp -a "$f" "$TMP/repo/$rel"
  i=$((i+1))
  bar $(( 30 + i * 40 / NF ))
done
echo
ok "Скопировано файлов: $NF"

# ---------- шаг 5: коммит ----------
step 5 "Коммит изменений"
bar 75
git -C "$TMP/repo" add -A
if git -C "$TMP/repo" diff --cached --quiet; then
  warn "Нет изменений относительно '$BRANCH' — пуш не требуется"
  bar 100; echo
  echo "${G}${B}СТАТУС: OK — репозиторий уже актуален${N}"
  exit 0
fi
CHANGED=$(git -C "$TMP/repo" diff --cached --name-only | wc -l)
git -C "$TMP/repo" -c user.name="${GIT_AUTHOR_NAME:-vsp}" -c user.email="${GIT_AUTHOR_EMAIL:-vsp@local}" \
  commit -q -m "upload: $(date '+%Y-%m-%d %H:%M') ($CHANGED файлов)"
bar 85
ok "Коммит создан ($CHANGED файлов)"

# ---------- шаг 6: пуш и проверка статуса ----------
step 6 "Пуш в origin/$BRANCH и проверка статуса"
if ! git -C "$TMP/repo" push -q origin "HEAD:$BRANCH" 2>"$TMP/push.err"; then
  bar 100; echo
  if grep -qi "authentication\|permission\|403\|401" "$TMP/push.err"; then
    die "Пуш отклонён (аутентификация/права): $(tail -n1 "$TMP/push.err")
  Проверьте, что у токена есть право 'repo' (write) на $API_REPO"
  fi
  die "Пуш не выполнен: $(tail -n1 "$TMP/push.err")"
fi
bar 95
LOCAL_SHA=$(git -C "$TMP/repo" rev-parse HEAD)
REMOTE_SHA=$(git -C "$TMP/repo" ls-remote origin "$BRANCH" | awk '{print $1}')
bar 100; echo
if [ "$LOCAL_SHA" = "$REMOTE_SHA" ]; then
  ok "Удалённая ветка совпадает с локальным коммитом: ${LOCAL_SHA:0:7}"
  echo "${G}${B}СТАТУС: OK — загружено $CHANGED файлов в $API_REPO ($BRANCH), способ входа: $AUTH_MODE${N}"
else
  die "НЕСОВПАДЕНИЕ SHA: local=$LOCAL_SHA remote=$REMOTE_SHA"
fi
