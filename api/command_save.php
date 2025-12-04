<?php
// derzhava_api/command_save.php
// Сохранение состояния командного центра
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$input = file_get_contents('php://input');
$data = json_decode($input, true);
if (!is_array($data)) {
    respond(false, 'Некорректный JSON');
}

$ruler = trim($data['ruler_name'] ?? '');
if ($ruler === '') {
    respond(false, 'Не передан ruler_name');
}

$intel     = (int)($data['intel'] ?? 10);
$sabotage  = (int)($data['sabotage'] ?? 10);
$theft     = (int)($data['theft'] ?? 10);
$agitation = (int)($data['agitation'] ?? 10);
$lastRecon    = (int)($data['last_recon_time'] ?? 0);
$lastSabotage = (int)($data['last_sabotage_time'] ?? 0);
$lastTheft    = (int)($data['last_theft_time'] ?? 0);
$lastAlliance = (int)($data['last_alliance_time'] ?? 0);

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$stmt = $pdo->prepare('SELECT ruler_name FROM command_center WHERE ruler_name = ? LIMIT 1');
$stmt->execute([$ruler]);
$exists = $stmt->fetch();

if ($exists) {
    // UPDATE
    $upd = $pdo->prepare('UPDATE command_center SET intel = ?, sabotage = ?, theft = ?, agitation = ?, last_recon_time = ?, last_sabotage_time = ?, last_theft_time = ?, last_alliance_time = ? WHERE ruler_name = ?');
    $upd->execute([$intel, $sabotage, $theft, $agitation, $lastRecon, $lastSabotage, $lastTheft, $lastAlliance, $ruler]);
    respond(true, 'Состояние командного центра обновлено');
} else {
    // INSERT
    $ins = $pdo->prepare('INSERT INTO command_center (ruler_name, intel, sabotage, theft, agitation, last_recon_time, last_sabotage_time, last_theft_time, last_alliance_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $ins->execute([$ruler, $intel, $sabotage, $theft, $agitation, $lastRecon, $lastSabotage, $lastTheft, $lastAlliance]);
    respond(true, 'Состояние командного центра создано');
}

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}