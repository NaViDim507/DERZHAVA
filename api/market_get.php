<?php
// derzhava_api/market_get.php
// Получить лоты на бирже. Можно отфильтровать по ресурсу и исключить свои
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$resourceType = isset($_POST['resource_type']) ? (int)$_POST['resource_type'] : 0;
$excludeRuler = trim($_POST['exclude_ruler'] ?? '');

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$query = 'SELECT * FROM market_offers';
$params = [];
$clauses = [];

if ($resourceType > 0) {
    $clauses[] = 'resource_type = ?';
    $params[] = $resourceType;
}
if ($excludeRuler !== '') {
    $clauses[] = 'ruler_name <> ?';
    $params[] = $excludeRuler;
}
if (!empty($clauses)) {
    $query .= ' WHERE ' . implode(' AND ', $clauses);
}

$stmt = $pdo->prepare($query);
$stmt->execute($params);
$rows = $stmt->fetchAll();

$offers = [];
foreach ($rows as $row) {
    $offers[] = [
        'id'            => (int)$row['id'],
        'ruler_name'    => $row['ruler_name'],
        'resource_type' => (int)$row['resource_type'],
        'amount'        => (int)$row['amount'],
        'price_per_unit'=> (int)$row['price_per_unit'],
    ];
}

respond(true, 'OK', $offers);

function respond(bool $success, string $message, ?array $offers): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'offers'  => $offers,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}