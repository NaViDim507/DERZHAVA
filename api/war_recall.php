<?php
// derzhava_api/war_recall.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$warId = (int)($_POST['war_id'] ?? 0);
$ruler = trim($_POST['attacker_ruler'] ?? '');

$peh = isset($_POST['peh']) ? (int)$_POST['peh'] : -1; // -1 = все
$kaz = isset($_POST['kaz']) ? (int)$_POST['kaz'] : -1;
$gva = isset($_POST['gva']) ? (int)$_POST['gva'] : -1;

if ($warId <= 0 || $ruler === '') {
    respond(false, 'Некорректные параметры');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$st = $pdo->prepare('SELECT * FROM wars_app WHERE id = ? LIMIT 1');
$st->execute([$warId]);
$war = $st->fetch();
if (!$war) {
    respond(false, 'Война не найдена');
}

if ($war['attacker_ruler'] !== $ruler) {
    respond(false, 'Ты не атакующая сторона');
}

if ($war['state'] !== 'active') {
    respond(false, 'Война уже не активна');
}

$curPeh = (int)$war['attacker_peh'];
$curKaz = (int)$war['attacker_kaz'];
$curGva = (int)$war['attacker_gva'];

if ($peh < 0) $peh = $curPeh;
if ($kaz < 0) $kaz = $curKaz;
if ($gva < 0) $gva = $curGva;

$peh = min($peh, $curPeh);
$kaz = min($kaz, $curKaz);
$gva = min($gva, $curGva);

// возвращаем войска обратно в army_state и gos_app
// сначала обновляем army_state, добавляя вернувшиеся войска
$updArmyState = $pdo->prepare('
    UPDATE army_state
    SET infantry = infantry + ?, cossacks = cossacks + ?, guards = guards + ?
    WHERE ruler_name = ?
');
$updArmyState->execute([$peh, $kaz, $gva, $ruler]);

// затем синхронизируем gos_app: устанавливаем значения равными актуальному состоянию армии
$stNew = $pdo->prepare('SELECT infantry, cossacks, guards FROM army_state WHERE ruler_name = ? LIMIT 1');
$stNew->execute([$ruler]);
$newState = $stNew->fetch();
if ($newState) {
    $updArmy = $pdo->prepare('
        UPDATE gos_app
        SET peh = ?, kaz = ?, gva = ?
        WHERE ruler_name = ?
    ');
    $updArmy->execute([
        (int)$newState['infantry'],
        (int)$newState['cossacks'],
        (int)$newState['guards'],
        $ruler
    ]);
}

// списываем с фронта
$updWar = $pdo->prepare('
    UPDATE wars_app
    SET attacker_peh = attacker_peh - ?,
        attacker_kaz = attacker_kaz - ?,
        attacker_gva = attacker_gva - ?
    WHERE id = ?
');
$updWar->execute([$peh, $kaz, $gva, $warId]);

$now = time();

$insMove = $pdo->prepare('
    INSERT INTO war_moves_app (war_id, type, ts, peh, kaz, gva, note)
    VALUES (?, "recall", ?, ?, ?, ?, ?)
');
$insMove->execute([$warId, $now, $peh, $kaz, $gva, 'Вывод войск']);

$insLog = $pdo->prepare('
    INSERT INTO war_logs_app (war_id, ts, text)
    VALUES (?, ?, ?)
');
$insLog->execute([$warId, $now, "Выведено войск: пех={$peh}, каз={$kaz}, гва={$gva}"]);

// если все войска выведены — помечаем войну как завершённую (recalled)
$st = $pdo->prepare('SELECT attacker_peh, attacker_kaz, attacker_gva FROM wars_app WHERE id = ?');
$st->execute([$warId]);
$w2 = $st->fetch();

if ($w2 && (int)$w2['attacker_peh'] === 0 && (int)$w2['attacker_kaz'] === 0 && (int)$w2['attacker_gva'] === 0) {
    $updState = $pdo->prepare('
        UPDATE wars_app
        SET state = "recalled", is_resolved = 1, attacker_won = 0
        WHERE id = ?
    ');
    $updState->execute([$warId]);
}

respond(true, 'Войска выведены');

function respond(bool $success, string $message): void
{
    echo json_encode(['success' => $success, 'message' => $message], JSON_UNESCAPED_UNICODE);
    exit;
}
