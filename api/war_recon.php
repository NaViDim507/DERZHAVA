<?php
// derzhava_api/war_recon.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$warId = (int)($_POST['war_id'] ?? 0);
$attacker = trim($_POST['attacker_ruler'] ?? '');

if ($warId <= 0 || $attacker === '') {
    respond(false, 'Некорректные параметры', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

// 1. Вытаскиваем войну
$st = $pdo->prepare('SELECT * FROM wars_app WHERE id = ? LIMIT 1');
$st->execute([$warId]);
$war = $st->fetch();

if (!$war) {
    respond(false, 'Война не найдена', null);
}

if ($war['attacker_ruler'] !== $attacker) {
    respond(false, 'Это не твоя война', null);
}

if ($war['state'] !== 'active') {
    respond(false, 'Война уже завершена', null);
}

// Проверим, что войска есть на территории
$frontPeh = (int)$war['attacker_peh'];
$frontKaz = (int)$war['attacker_kaz'];
$frontGva = (int)$war['attacker_gva'];

if ($frontPeh + $frontKaz + $frontGva <= 0) {
    respond(false, 'На вражеской территории нет твоих войск', null);
}

// 2. Вытаскиваем защитника
$defender = $war['defender_ruler'];

$st = $pdo->prepare('SELECT * FROM gos_app WHERE ruler_name = ? LIMIT 1');
$st->execute([$defender]);
$def = $st->fetch();

if (!$def) {
    respond(false, 'Целевая держава не найдена', null);
}

// 3. Формируем список зданий (адаптивно, чтобы легко менять логику)
$buildings = [];

$buildings[] = [
    'key'           => 'town',
    'name'          => 'Городок',
    'exists'        => (int)$def['domik1'] === 1,
    'can_demolish'  => (int)$def['domik1'] === 1,
    'reward_type'   => 'workers',   // 40% рабочих
    'reward_percent'=> 40
];

$buildings[] = [
    'key'           => 'command_center',
    'name'          => 'Командный центр',
    'exists'        => (int)$def['domik2'] === 1,
    'can_demolish'  => (int)$def['domik2'] === 1,
    'reward_type'   => 'money',     // 40% денег
    'reward_percent'=> 40
];

// Пример задела на будущее (пока просто светим наличие)
// $buildings[] = [
//     'key'           => 'kombinat',
//     'name'          => 'Комбинат',
//     'exists'        => (int)$def['domik3'] === 1,
//     'can_demolish'  => (int)$def['domik3'] === 1,
//     'reward_type'   => null,
//     'reward_percent'=> 0
// ];

respond(true, 'Разведка выполнена', $buildings);

function respond(bool $success, string $message, ?array $buildings): void
{
    echo json_encode(
        [
            'success'   => $success,
            'message'   => $message,
            'buildings' => $buildings
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}
