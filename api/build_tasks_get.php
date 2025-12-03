<?php
// derzhava_api/build_tasks_get.php
// Получить все задачи строительства для правителя
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

$stmt = $pdo->prepare('SELECT * FROM build_tasks WHERE ruler_name = ?');
$stmt->execute([$ruler]);
$rows = $stmt->fetchAll();

$tasks = [];
foreach ($rows as $row) {
    $tasks[] = [
        'id'               => (int)$row['id'],
        'ruler_name'       => $row['ruler_name'],
        'building_type'    => (int)$row['building_type'],
        'workers'          => (int)$row['workers'],
        'start_time_millis'=> (int)$row['start_time_millis'],
        'end_time_millis'  => (int)$row['end_time_millis'],
    ];
}

respond(true, 'OK', $tasks);

function respond(bool $success, string $message, ?array $tasks): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'tasks'   => $tasks,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}