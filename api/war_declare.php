<?php
// derzhava_api/war_declare.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$attacker = trim($_POST['attacker_ruler'] ?? '');
$defender = trim($_POST['defender_ruler'] ?? '');
$peh      = (int)($_POST['peh'] ?? 0);
$kaz      = (int)($_POST['kaz'] ?? 0);
$gva      = (int)($_POST['gva'] ?? 0);

if ($attacker === '' || $defender === '') {
    respond(false, 'Не передан атакующий / защищающийся');
}

if ($attacker === $defender) {
    respond(false, 'Нельзя объявить войну самому себе');
}

if ($peh < 0 || $kaz < 0 || $gva < 0) {
    respond(false, 'Некорректные числа войск');
}

$now = time();

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

// проверяем, есть ли такие страны в gos_app
$st = $pdo->prepare('SELECT * FROM gos_app WHERE ruler_name = ? LIMIT 1');
$st->execute([$attacker]);
$attRow = $st->fetch();
if (!$attRow) {
    respond(false, 'Атакующая держава не найдена');
}

$st->execute([$defender]);
$defRow = $st->fetch();
if (!$defRow) {
    respond(false, 'Целевая держава не найдена');
}

// ограничение: не более 3 активных войн у атакующего
$st = $pdo->prepare('
    SELECT COUNT(*) AS cnt 
    FROM wars_app 
    WHERE attacker_ruler = ? AND state = "active"
');
$st->execute([$attacker]);
$rowCnt = $st->fetch();
if ($rowCnt && (int)$rowCnt['cnt'] >= 3) {
    respond(false, 'У тебя уже три активных войны');
}

// проверяем, что есть нужное количество войск у атакующего (в gos_app.peh/kaz/gva)
if ($peh > (int)$attRow['peh'] ||
    $kaz > (int)$attRow['kaz'] ||
    $gva > (int)$attRow['gva']) {
    respond(false, 'Недостаточно войск для отправки');
}

// списываем войска из державы атакующего
$updArmy = $pdo->prepare('
    UPDATE gos_app 
    SET peh = peh - ?, kaz = kaz - ?, gva = gva - ?
    WHERE ruler_name = ?
');
$updArmy->execute([$peh, $kaz, $gva, $attacker]);

// создаём запись войны
$startAt       = $now;
$canRaidAt     = $now + 3 * 3600;   // как в der1: через 3 часа можно диверсии/рейды
$canCaptureAt  = $now + 3 * 3600;   // и захват тоже через 3 часа

$insWar = $pdo->prepare('
    INSERT INTO wars_app (
      attacker_ruler, defender_ruler,
      attacker_country, defender_country,
      attacker_peh, attacker_kaz, attacker_gva,
      total_raids, total_captures,
      start_at, can_raid_at, can_capture_at, last_demolition_at,
      state, is_resolved, attacker_won, recon_acc
    ) VALUES (
      ?, ?, ?, ?,
      ?, ?, ?,
      0, 0,
      ?, ?, ?, 0,
      "active", 0, NULL, 0
    )
');

$insWar->execute([
    $attacker,
    $defender,
    $attRow['country_name'],
    $defRow['country_name'],
    $peh, $kaz, $gva,
    $startAt, $canRaidAt, $canCaptureAt
]);

$warId = (int)$pdo->lastInsertId();

// лог
$logText = "Война объявлена: {$attacker} против {$defender}. "
         . "Отправлено войск: пех={$peh}, каз={$kaz}, гва={$gva}.";

$insLog = $pdo->prepare('
    INSERT INTO war_logs_app (war_id, ts, text)
    VALUES (?, ?, ?)
');
$insLog->execute([$warId, $now, $logText]);

respond(true, 'Война объявлена', [ 'war_id' => $warId ]);

function respond(bool $success, string $message, ?array $extra = null): void
{
    $resp = [
        'success' => $success,
        'message' => $message,
    ];
    if ($extra !== null) {
        $resp += $extra;
    }
    echo json_encode($resp, JSON_UNESCAPED_UNICODE);
    exit;
}
