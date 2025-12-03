<?php
// derzhava_api/war_reinforce.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$warId    = (int)($_POST['war_id'] ?? 0);
$ruler    = trim($_POST['attacker_ruler'] ?? '');
$peh      = (int)($_POST['peh'] ?? 0);
$kaz      = (int)($_POST['kaz'] ?? 0);
$gva      = (int)($_POST['gva'] ?? 0);

if ($warId <= 0 || $ruler === '') {
    respond(false, 'Некорректные параметры');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

// вытаскиваем войну
$st = $pdo->prepare('SELECT * FROM wars_app WHERE id = ? LIMIT 1');
$st->execute([$warId]);
$war = $st->fetch();
if (!$war) {
    respond(false, 'Война не найдена');
}

if ($war['attacker_ruler'] !== $ruler) {
    respond(false, 'Ты не являешься атакующей стороной');
}

if ($war['state'] !== 'active') {
    respond(false, 'Война уже не активна');
}

// сверяем войска атакующего в gos_app
$st = $pdo->prepare('SELECT peh, kaz, gva FROM gos_app WHERE ruler_name = ? LIMIT 1');
$st->execute([$ruler]);
$army = $st->fetch();
if (!$army) {
    respond(false, 'Держава не найдена');
}

if ($peh < 0 || $kaz < 0 || $gva < 0) {
    respond(false, 'Некорректные числа войск');
}

if ($peh > (int)$army['peh'] ||
    $kaz > (int)$army['kaz'] ||
    $gva > (int)$army['gva']) {
    respond(false, 'Недостаточно войск');
}

// списываем войска из державы
$updArmy = $pdo->prepare('
    UPDATE gos_app SET peh = peh - ?, kaz = kaz - ?, gva = gva - ?
    WHERE ruler_name = ?
');
$updArmy->execute([$peh, $kaz, $gva, $ruler]);

// добавляем на фронт
$updWar = $pdo->prepare('
    UPDATE wars_app
    SET attacker_peh = attacker_peh + ?,
        attacker_kaz = attacker_kaz + ?,
        attacker_gva = attacker_gva + ?
    WHERE id = ?
');
$updWar->execute([$peh, $kaz, $gva, $warId]);

$now = time();

// записываем движение
$insMove = $pdo->prepare('
    INSERT INTO war_moves_app (war_id, type, ts, peh, kaz, gva, note)
    VALUES (?, "reinforce", ?, ?, ?, ?, ?)
');
$insMove->execute([$warId, $now, $peh, $kaz, $gva, "Подкрепление отправлено"]);

$insLog = $pdo->prepare('
    INSERT INTO war_logs_app (war_id, ts, text)
    VALUES (?, ?, ?)
');
$insLog->execute([$warId, $now, "Отправлено подкрепление: пех={$peh}, каз={$kaz}, гва={$gva}"]);

respond(true, 'Подкрепление отправлено');

function respond(bool $success, string $message): void
{
    echo json_encode(['success' => $success, 'message' => $message], JSON_UNESCAPED_UNICODE);
    exit;
}
