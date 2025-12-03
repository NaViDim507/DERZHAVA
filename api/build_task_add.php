<?php
// derzhava_api/build_task_add.php
// Добавить новую задачу строительства
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$input = file_get_contents('php://input');
$data  = json_decode($input, true);
if (!is_array($data)) {
    respond(false, 'Некорректный JSON', null);
}

$ruler = trim($data['ruler_name'] ?? '');
$buildingType = (int)($data['building_type'] ?? 0);
$workers = (int)($data['workers'] ?? 0);
$start = (int)($data['start_time_millis'] ?? 0);
$end   = (int)($data['end_time_millis'] ?? 0);

if ($ruler === '' || $buildingType <= 0 || $workers <= 0) {
    respond(false, 'Некорректные данные', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$ins = $pdo->prepare('INSERT INTO build_tasks (ruler_name, building_type, workers, start_time_millis, end_time_millis) VALUES (?, ?, ?, ?, ?)');
$ins->execute([$ruler, $buildingType, $workers, $start, $end]);
$id = (int)$pdo->lastInsertId();

respond(true, 'Задача добавлена', ['id' => $id]);

function respond(bool $success, string $message, ?array $data): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data'    => $data,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}