<?php
// derzhava_api/training_job_add.php
// Добавить задачу обучения войск
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$input = file_get_contents('php://input');
$data = json_decode($input, true);
if (!is_array($data)) {
    respond(false, 'Некорректный JSON', null);
}

$ruler      = trim($data['ruler_name'] ?? '');
$unitType   = (int)($data['unit_type'] ?? 0);
$workers    = (int)($data['workers'] ?? 0);
$scientists = (int)($data['scientists'] ?? 0);
$start      = (int)($data['start_time_millis'] ?? 0);
$duration   = (int)($data['duration_seconds'] ?? 0);

if ($ruler === '' || $unitType <= 0 || $workers <= 0 || $scientists < 0) {
    respond(false, 'Некорректные данные', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$ins = $pdo->prepare('INSERT INTO training_jobs (ruler_name, unit_type, workers, scientists, start_time_millis, duration_seconds) VALUES (?, ?, ?, ?, ?, ?)');
$ins->execute([$ruler, $unitType, $workers, $scientists, $start, $duration]);
$id = (int)$pdo->lastInsertId();

respond(true, 'Задание добавлено', ['id' => $id]);

function respond(bool $success, string $message, ?array $data): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data'    => $data,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}