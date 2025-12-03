<?php
// derzhava_api/training_jobs_get.php
// Получить все задания обучения войск (kmb) для правителя
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

$stmt = $pdo->prepare('SELECT * FROM training_jobs WHERE ruler_name = ?');
$stmt->execute([$ruler]);
$rows = $stmt->fetchAll();

$jobs = [];
foreach ($rows as $row) {
    $jobs[] = [
        'id'                 => (int)$row['id'],
        'ruler_name'         => $row['ruler_name'],
        'unit_type'          => (int)$row['unit_type'],
        'workers'            => (int)$row['workers'],
        'scientists'         => (int)$row['scientists'],
        'start_time_millis'  => (int)$row['start_time_millis'],
        'duration_seconds'   => (int)$row['duration_seconds'],
    ];
}

respond(true, 'OK', $jobs);

function respond(bool $success, string $message, ?array $jobs): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'jobs'    => $jobs,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}