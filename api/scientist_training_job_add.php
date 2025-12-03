<?php
// derzhava_api/scientist_training_job_add.php
// Добавить задание обучения учёных
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$data = json_decode(file_get_contents('php://input'), true);
if (!is_array($data)) {
    respond(false, 'Некорректный JSON', null);
}

$ruler      = trim($data['ruler_name'] ?? '');
$workers    = (int)($data['workers'] ?? 0);
$scientists = (int)($data['scientists'] ?? 0);
$start      = (int)($data['start_time_millis'] ?? 0);
$duration   = (int)($data['duration_seconds'] ?? 0);

if ($ruler === '' || $workers <= 0 || $scientists <= 0) {
    respond(false, 'Некорректные данные', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$ins = $pdo->prepare('INSERT INTO scientist_training_jobs (ruler_name, workers, scientists, start_time_millis, duration_seconds) VALUES (?, ?, ?, ?, ?)');
$ins->execute([$ruler, $workers, $scientists, $start, $duration]);
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