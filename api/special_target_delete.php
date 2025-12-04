<?php
// derzhava_api/special_target_delete.php
// Удалить NPC-цель
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$targetId = isset($_POST['target_id']) ? (int)$_POST['target_id'] : 0;
$ruler    = trim($_POST['ruler_name'] ?? '');

if ($targetId <= 0 || $ruler === '') {
    respond(false, 'Некорректные данные');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$st = $pdo->prepare('SELECT ruler_name FROM special_targets WHERE id = ? LIMIT 1');
$st->execute([$targetId]);
$row = $st->fetch();
if (!$row) {
    respond(false, 'Цель не найдена');
}
if ($row['ruler_name'] !== $ruler) {
    respond(false, 'Нельзя удалить чужую цель');
}

$del = $pdo->prepare('DELETE FROM special_targets WHERE id = ?');
$del->execute([$targetId]);

respond(true, 'Цель удалена');

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}