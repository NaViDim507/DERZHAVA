<?php
// derzhava_api/special_target_add.php
// Добавить NPC-цель для спецопераций
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$ruler       = trim($_POST['ruler_name'] ?? '');
$country     = trim($_POST['country_name'] ?? '');
$perimeter   = isset($_POST['perimeter']) ? (int)$_POST['perimeter'] : 0;
$security    = isset($_POST['security']) ? (int)$_POST['security'] : 0;
$money       = isset($_POST['money']) ? (int)$_POST['money'] : 0;
$isAlly      = isset($_POST['is_ally']) ? ((int)$_POST['is_ally'] === 1) : false;

if ($ruler === '' || $country === '' || $perimeter <= 0 || $security < 0) {
    respond(false, 'Некорректные данные');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$ins = $pdo->prepare(
    'INSERT INTO special_targets (
       ruler_name, country_name, perimeter, security, money, is_ally
     ) VALUES (?, ?, ?, ?, ?, ?)'
);
$ins->execute([
    $ruler,
    $country,
    $perimeter,
    $security,
    $money,
    $isAlly ? 1 : 0,
]);
$targetId = (int)$pdo->lastInsertId();

respond(true, 'Цель добавлена', [ 'target_id' => $targetId ]);

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