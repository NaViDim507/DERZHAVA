<?php
// derzhava_api/build_task_delete.php
// Удалить задачу строительства по id
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$id = isset($_POST['id']) ? (int)$_POST['id'] : 0;
if ($id <= 0) {
    respond(false, 'Некорректный id');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$del = $pdo->prepare('DELETE FROM build_tasks WHERE id = ?');
$del->execute([$id]);

if ($del->rowCount() > 0) {
    respond(true, 'Задача удалена');
} else {
    respond(false, 'Задача не найдена');
}

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}