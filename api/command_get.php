<?php
// derzhava_api/command_get.php
// Получение состояния командного центра для правителя
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$ruler = trim($_POST['ruler_name'] ?? '');
if ($ruler === '') {
    respond(false, 'Не передан ruler_name', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$stmt = $pdo->prepare('SELECT * FROM command_center WHERE ruler_name = ? LIMIT 1');
$stmt->execute([$ruler]);
$row = $stmt->fetch();

if (!$row) {
    respond(false, 'Состояние командного центра не найдено', null);
}

$data = [
    'ruler_name'      => $row['ruler_name'],
    'intel'           => (int)$row['intel'],
    'sabotage'        => (int)$row['sabotage'],
    'theft'           => (int)$row['theft'],
    'agitation'       => (int)$row['agitation'],
    'last_recon_time'    => (int)$row['last_recon_time'],
    'last_sabotage_time' => (int)$row['last_sabotage_time'],
    'last_theft_time'    => (int)$row['last_theft_time'],
    'last_alliance_time' => (int)$row['last_alliance_time'],
];

respond(true, 'OK', $data);

function respond(bool $success, string $message, ?array $data): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'command_center' => $data,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}