<?php
// derzhava_api/special_targets_get.php
// Получить список NPC-целей для спецопераций
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

$st = $pdo->prepare('SELECT * FROM special_targets WHERE ruler_name = ? ORDER BY id ASC');
$st->execute([$ruler]);
$rows = $st->fetchAll();

$targets = [];
foreach ($rows as $row) {
    $targets[] = [
        'id'            => (int)$row['id'],
        'ruler_name'    => $row['ruler_name'],
        'country_name'  => $row['country_name'],
        'perimeter'     => (int)$row['perimeter'],
        'security'      => (int)$row['security'],
        'money'         => (int)$row['money'],
        'is_ally'       => (int)$row['is_ally'] === 1,
    ];
}

respond(true, 'OK', $targets);

function respond(bool $success, string $message, ?array $targets): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'targets' => $targets,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}