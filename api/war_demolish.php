<?php
// derzhava_api/war_demolish.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$warId   = (int)($_POST['war_id'] ?? 0);
$attacker = trim($_POST['attacker_ruler'] ?? '');
$buildingKey = trim($_POST['building_key'] ?? '');

if ($warId <= 0 || $attacker === '' || $buildingKey === '') {
    respond(false, 'Некорректные параметры');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

// Описание зданий и наград — в одном месте (адаптивно)
$buildingMap = [
    'town' => [
        'domik_col'       => 'domik1',
        'name'           => 'Городок',
        'reward_type'    => 'workers',
        'reward_percent' => 40
    ],
    'command_center' => [
        'domik_col'       => 'domik2',
        'name'           => 'Командный центр',
        'reward_type'    => 'money',
        'reward_percent' => 40
    ],
    // 'kombinat' => [ ... ]
];

if (!array_key_exists($buildingKey, $buildingMap)) {
    respond(false, 'Неизвестное здание');
}

$conf = $buildingMap[$buildingKey];

// 1. Вытаскиваем войну
$st = $pdo->prepare('SELECT * FROM wars_app WHERE id = ? LIMIT 1');
$st->execute([$warId]);
$war = $st->fetch();

if (!$war) {
    respond(false, 'Война не найдена');
}

if ($war['attacker_ruler'] !== $attacker) {
    respond(false, 'Это не твоя война');
}

if ($war['state'] !== 'active') {
    respond(false, 'Война уже завершена');
}

// Проверка войск на территории
$frontPeh = (int)$war['attacker_peh'];
$frontKaz = (int)$war['attacker_kaz'];
$frontGva = (int)$war['attacker_gva'];

if ($frontPeh + $frontKaz + $frontGva <= 0) {
    respond(false, 'На территории врага нет твоих войск');
}

$now = time();

// Проверка таймингов: 3 часа до первого разрушения
$canRaidAt = (int)$war['can_raid_at'];
if ($now < $canRaidAt) {
    $left = $canRaidAt - $now;
    respond(false, 'Разрушение доступно через ' . gmdate('H:i', $left));
}

// Кулдаун 1 час между разрушениями
$lastDemolish = (int)$war['last_demolition_at'];
if ($lastDemolish > 0 && ($now - $lastDemolish) < 3600) {
    $left = 3600 - ($now - $lastDemolish);
    respond(false, 'Следующее разрушение будет доступно через ' . gmdate('H:i', $left));
}

// 2. Вытаскиваем атакующего и защитника
$defender = $war['defender_ruler'];

$stDef = $pdo->prepare('SELECT * FROM gos_app WHERE ruler_name = ? LIMIT 1');
$stDef->execute([$defender]);
$def = $stDef->fetch();
if (!$def) {
    respond(false, 'Целевая держава не найдена');
}

$stAtt = $pdo->prepare('SELECT * FROM gos_app WHERE ruler_name = ? LIMIT 1');
$stAtt->execute([$attacker]);
$att = $stAtt->fetch();
if (!$att) {
    respond(false, 'Атакующая держава не найдена');
}

// Проверяем, что здание ещё стоит
$domikCol = $conf['domik_col'];
if ((int)$def[$domikCol] !== 1) {
    respond(false, 'Это здание уже разрушено');
}

// Начинаем транзакцию
$pdo->beginTransaction();

try {
    // 3. Считаем награду
    $rewardType    = $conf['reward_type'];
    $rewardPercent = (int)$conf['reward_percent'];
    $rewardAmount  = 0;

    if ($rewardType === 'money') {
        $defMoney = (int)$def['money'];
        if ($defMoney > 0 && $rewardPercent > 0) {
            $rewardAmount = (int)floor($defMoney * $rewardPercent / 100);
        }
        $newDefMoney = $defMoney - $rewardAmount;
        if ($newDefMoney < 0) $newDefMoney = 0;

        // обновляем деньги
        $updDef = $pdo->prepare('
            UPDATE gos_app SET money = ?, ' . $domikCol . ' = 0
            WHERE ruler_name = ?
        ');
        $updDef->execute([$newDefMoney, $defender]);

        $attMoney = (int)$att['money'] + $rewardAmount;
        $updAtt = $pdo->prepare('
            UPDATE gos_app SET money = ?
            WHERE ruler_name = ?
        ');
        $updAtt->execute([$attMoney, $attacker]);
    } elseif ($rewardType === 'workers') {
        $defWorkers = (int)$def['workers'];
        if ($defWorkers > 0 && $rewardPercent > 0) {
            $rewardAmount = (int)floor($defWorkers * $rewardPercent / 100);
        }
        $newDefWorkers = $defWorkers - $rewardAmount;
        if ($newDefWorkers < 0) $newDefWorkers = 0;

        $updDef = $pdo->prepare('
            UPDATE gos_app SET workers = ?, ' . $domikCol . ' = 0
            WHERE ruler_name = ?
        ');
        $updDef->execute([$newDefWorkers, $defender]);

        $attWorkers = (int)$att['workers'] + $rewardAmount;
        $updAtt = $pdo->prepare('
            UPDATE gos_app SET workers = ?
            WHERE ruler_name = ?
        ');
        $updAtt->execute([$attWorkers, $attacker]);
    } else {
        // без награды, просто ломаем домик
        $updDef = $pdo->prepare('
            UPDATE gos_app SET ' . $domikCol . ' = 0
            WHERE ruler_name = ?
        ');
        $updDef->execute([$defender]);
    }

    // 4. Обновляем войну: время и кол-во диверсий
    $updWar = $pdo->prepare('
        UPDATE wars_app
        SET last_demolition_at = ?, total_raids = total_raids + 1
        WHERE id = ?
    ');
    $updWar->execute([$now, $warId]);

    // 5. Лог
    $logText = 'Разрушено здание: ' . $conf['name'];
    if ($rewardAmount > 0 && $rewardType === 'money') {
        $logText .= ', награда атакующему: ' . $rewardAmount . ' денег.';
    } elseif ($rewardAmount > 0 && $rewardType === 'workers') {
        $logText .= ', награда атакующему: ' . $rewardAmount . ' рабочих.';
    }

    $insLog = $pdo->prepare('
        INSERT INTO war_logs_app (war_id, ts, text)
        VALUES (?, ?, ?)
    ');
    $insLog->execute([$warId, $now, $logText]);

    $pdo->commit();

} catch (Throwable $e) {
    $pdo->rollBack();
    respond(false, 'Ошибка при разрушении: ' . $e->getMessage());
}

respond(true, 'Здание разрушено: ' . $conf['name']);

function respond(bool $success, string $message): void
{
    echo json_encode(
        [
            'success' => $success,
            'message' => $message
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}
