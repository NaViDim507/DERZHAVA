<?php
// derzhava_api/build_task_get.php
// Получить одну задачу строительства по id
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

// id передаётся через POST. Используем intval для безопасности.
$id = isset($_POST['id']) ? (int)$_POST['id'] : 0;
if ($id <= 0) {
    respond(false, 'Некорректный id', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$stmt = $pdo->prepare('SELECT * FROM build_tasks WHERE id = ? LIMIT 1');
$stmt->execute([$id]);
$row = $stmt->fetch();

if (!$row) {
    respond(false, 'Задача не найдена', null);
}

// Приводим значения к типам, ожидаемым клиентом
$task = [
    'id'                 => (int)$row['id'],
    'ruler_name'         => $row['ruler_name'],
    'building_type'      => (int)$row['building_type'],
    'workers'            => (int)$row['workers'],
    'start_time_millis'  => (int)$row['start_time_millis'],
    'end_time_millis'    => (int)$row['end_time_millis'],
];

respond(true, 'OK', $task);

function respond(bool $success, string $message, ?array $task): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'task'    => $task,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}