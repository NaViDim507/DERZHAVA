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

// При добавлении задания обучения нужно зарезервировать рабочую силу и учёных,
// чтобы они не могли быть использованы в других заданиях или стройках.
// Проверяем наличие достаточного количества свободных работников и ботов (учёных) у страны.
try {
    // Загружаем текущие запасы страны
    $stCountry = $pdo->prepare('SELECT workers, bots FROM countries WHERE ruler_name = ? LIMIT 1');
    $stCountry->execute([$ruler]);
    $countryRow = $stCountry->fetch();
    if (!$countryRow) {
        respond(false, 'Страна не найдена', null);
    }
    $freeWorkers = (int)$countryRow['workers'];
    $freeBots    = (int)$countryRow['bots'];
    // Проверяем, достаточно ли свободных ресурсов
    if ($workers > $freeWorkers || $scientists > $freeBots) {
        respond(false, 'Недостаточно рабочих или учёных', null);
    }
    // Списываем рабочих и учёных из страны
    $updCountry = $pdo->prepare('UPDATE countries SET workers = workers - ?, bots = bots - ? WHERE ruler_name = ?');
    $updCountry->execute([$workers, $scientists, $ruler]);
    // Списываем рабочих из gos_app, если запись существует
    $updGos = $pdo->prepare('UPDATE gos_app SET workers = workers - ? WHERE ruler_name = ?');
    $updGos->execute([$workers, $ruler]);
    // Добавляем запись о тренировке
    $ins = $pdo->prepare('INSERT INTO training_jobs (ruler_name, unit_type, workers, scientists, start_time_millis, duration_seconds) VALUES (?, ?, ?, ?, ?, ?)');
    $ins->execute([$ruler, $unitType, $workers, $scientists, $start, $duration]);
    $id = (int)$pdo->lastInsertId();
    respond(true, 'Задание добавлено', ['id' => $id]);
} catch (Throwable $ex) {
    respond(false, 'Ошибка при создании задания', null);
}

function respond(bool $success, string $message, ?array $data): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data'    => $data,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}