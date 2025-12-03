<?php
// derzhava_api/market_delete.php
// Удалить свой лот с биржи
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$offerId = isset($_POST['offer_id']) ? (int)$_POST['offer_id'] : 0;
$ruler   = trim($_POST['ruler_name'] ?? '');

if ($offerId <= 0 || $ruler === '') {
    respond(false, 'Некорректные данные');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

// Проверяем, что лот принадлежит пользователю
$st = $pdo->prepare('SELECT ruler_name FROM market_offers WHERE id = ? LIMIT 1');
$st->execute([$offerId]);
$row = $st->fetch();
if (!$row) {
    respond(false, 'Лот не найден');
}
if ($row['ruler_name'] !== $ruler) {
    respond(false, 'Нельзя удалить чужой лот');
}

// Удаляем
$del = $pdo->prepare('DELETE FROM market_offers WHERE id = ?');
$del->execute([$offerId]);

respond(true, 'Лот удалён');

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}