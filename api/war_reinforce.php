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

// сверяем войска атакующего на основе army_state, а затем на сервере. 
// Получаем локальное состояние армии
$stArmy = $pdo->prepare('SELECT infantry, cossacks, guards FROM army_state WHERE ruler_name = ? LIMIT 1');
$stArmy->execute([$ruler]);
$armyState = $stArmy->fetch();
if (!$armyState) {
    respond(false, 'Армия не найдена');
}

if ($peh < 0 || $kaz < 0 || $gva < 0) {
    respond(false, 'Некорректные числа войск');
}

// Проверяем, что отправляется хотя бы один боец
$total = $peh + $kaz + $gva;
if ($total <= 0) {
    respond(false, 'Нужно отправить хотя бы одного бойца');
}

if ($peh > (int)$armyState['infantry'] ||
    $kaz > (int)$armyState['cossacks'] ||
    $gva > (int)$armyState['guards']) {
    respond(false, 'Недостаточно войск');
}

// списываем войска из army_state
$updState = $pdo->prepare('
    UPDATE army_state
    SET infantry = infantry - ?, cossacks = cossacks - ?, guards = guards - ?
    WHERE ruler_name = ?
');

$updState->execute([$peh, $kaz, $gva, $ruler]);

// синхронизируем войска в gos_app: считываем актуальные остатки из army_state
$stNew = $pdo->prepare('SELECT infantry, cossacks, guards FROM army_state WHERE ruler_name = ? LIMIT 1');
$stNew->execute([$ruler]);
$newArmy = $stNew->fetch();
if ($newArmy) {
    $updArmy = $pdo->prepare('
        UPDATE gos_app SET peh = ?, kaz = ?, gva = ?
        WHERE ruler_name = ?
    ');
    $updArmy->execute([
        (int)$newArmy['infantry'],
        (int)$newArmy['cossacks'],
        (int)$newArmy['guards'],
        $ruler
    ]);
}

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
//
// Таблица war_moves_app в схеме содержит поля:
//   id, war_id, ts, move_type, payload
// Ранее здесь вставлялись значения в несуществующие колонки (type, peh, kaz, gva, note),
// что приводило к SQL‑ошибкам и HTTP 500. Теперь используем существующие колонки
// и сериализуем детали операции в JSON‑payload. Это позволяет хранить любые параметры
// без изменения структуры таблицы.

// Формируем JSON‑payload со всеми параметрами подкрепления и небольшим описанием.
// При необходимости можно добавить новые поля, сохраняя обратную совместимость.
$payload = json_encode([
    'peh'  => $peh,
    'kaz'  => $kaz,
    'gva'  => $gva,
    'note' => 'Подкрепление отправлено'
]);

$insMove = $pdo->prepare(
    'INSERT INTO war_moves_app (war_id, ts, move_type, payload) VALUES (?, ?, ?, ?)'
);
$insMove->execute([$warId, $now, 'reinforce', $payload]);

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
