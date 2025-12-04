<?php
// derzhava_api/market_add.php
// Добавить новый лот на бирже
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

// входные данные
$ruler      = trim($_POST['ruler_name'] ?? '');
$resType    = isset($_POST['resource_type']) ? (int)$_POST['resource_type'] : 0;
$amount     = isset($_POST['amount']) ? (int)$_POST['amount'] : 0;
$priceUnit  = isset($_POST['price_per_unit']) ? (int)$_POST['price_per_unit'] : 0;

if ($ruler === '' || $resType <= 0 || $amount <= 0 || $priceUnit <= 0) {
    respond(false, 'Некорректные данные');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

// Создаём запись
$ins = $pdo->prepare(
    'INSERT INTO market_offers (ruler_name, resource_type, amount, price_per_unit)
     VALUES (?, ?, ?, ?)'
);
$ins->execute([$ruler, $resType, $amount, $priceUnit]);
$offerId = (int)$pdo->lastInsertId();

respond(true, 'Лот добавлен', [ 'offer_id' => $offerId ]);

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