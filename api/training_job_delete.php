<?php
// derzhava_api/training_job_delete.php
// Удалить задание обучения войск по id
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

$del = $pdo->prepare('DELETE FROM training_jobs WHERE id = ?');
$del->execute([$id]);

if ($del->rowCount() > 0) {
    respond(true, 'Задание удалено');
} else {
    respond(false, 'Задание не найдено');
}

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}