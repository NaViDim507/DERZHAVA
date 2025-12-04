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

// При удалении задания обучения нам нужно вернуть заблокированные ресурсы (рабочих и учёных).
// Для этого сначала загружаем задание, чтобы знать, сколько рабочих и учёных было выделено.
$stmt = $pdo->prepare('SELECT ruler_name, workers, scientists FROM training_jobs WHERE id = ? LIMIT 1');
$stmt->execute([$id]);
$job = $stmt->fetch();
if (!$job) {
    respond(false, 'Задание не найдено');
}
$rulerName = $job['ruler_name'];
$workers = (int)$job['workers'];
$scientists = (int)$job['scientists'];

// Удаляем задание
$del = $pdo->prepare('DELETE FROM training_jobs WHERE id = ?');
$del->execute([$id]);

// Возвращаем рабочих и учёных в страну
$updCountry = $pdo->prepare('UPDATE countries SET workers = workers + ?, bots = bots + ? WHERE ruler_name = ?');
$updCountry->execute([$workers, $scientists, $rulerName]);
// Обновляем workers в gos_app, если запись существует
$updGos = $pdo->prepare('UPDATE gos_app SET workers = workers + ? WHERE ruler_name = ?');
$updGos->execute([$workers, $rulerName]);

respond(true, 'Задание удалено');

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}