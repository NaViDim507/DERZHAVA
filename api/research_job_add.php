<?php
// derzhava_api/research_job_add.php
// Добавить задание исследования
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$data = json_decode(file_get_contents('php://input'), true);
if (!is_array($data)) {
    respond(false, 'Некорректный JSON', null);
}

$ruler      = trim($data['ruler_name'] ?? '');
$science    = (int)($data['science_type'] ?? 0);
$start      = (int)($data['start_time_millis'] ?? 0);
$duration   = (int)($data['duration_seconds'] ?? 0);
$scientists = (int)($data['scientists'] ?? 0);
$progress   = (int)($data['progress_points'] ?? 0);

if ($ruler === '' || $science <= 0 || $scientists < 0) {
    respond(false, 'Некорректные данные', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$ins = $pdo->prepare('INSERT INTO research_jobs (ruler_name, science_type, start_time_millis, duration_seconds, scientists, progress_points) VALUES (?, ?, ?, ?, ?, ?)');
$ins->execute([$ruler, $science, $start, $duration, $scientists, $progress]);
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