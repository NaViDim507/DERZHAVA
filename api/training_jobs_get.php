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

// Загружаем все задания обучения войск для правителя.
$stmt = $pdo->prepare('SELECT * FROM training_jobs WHERE ruler_name = ?');
$stmt->execute([$ruler]);
$rows = $stmt->fetchAll();

$jobs = [];
// Текущее время в миллисекундах для проверки завершения обучения
$nowMillis = (int)(microtime(true) * 1000);

foreach ($rows as $row) {
    $jobId = (int)$row['id'];
    $start = (int)$row['start_time_millis'];
    $duration = (int)$row['duration_seconds'];
    $finishAt = $start + $duration * 1000;

    if ($nowMillis >= $finishAt) {
        // Обучение завершено. Не добавляем это задание в список, а сразу
        // обновляем армию и страну. Это позволяет серверу содержать
        // актуальные данные даже если клиент не обращается к автообновлению.

        $unitType   = (int)$row['unit_type'];
        $workers    = (int)$row['workers'];
        $scientists = (int)$row['scientists'];

        // 1. Обновляем army_state: добавляем обученных бойцов
        // Получаем текущие значения
        $stArmy = $pdo->prepare('SELECT infantry, cossacks, guards FROM army_state WHERE ruler_name = ? LIMIT 1');
        $stArmy->execute([$ruler]);
        $armyRow = $stArmy->fetch();
        if ($armyRow) {
            $inf = (int)$armyRow['infantry'];
            $cos = (int)$armyRow['cossacks'];
            $gua = (int)$armyRow['guards'];
            if ($unitType === 1) $inf += $workers;
            if ($unitType === 2) $cos += $workers;
            if ($unitType === 3) $gua += $workers;
            $updArmy = $pdo->prepare('UPDATE army_state SET infantry = ?, cossacks = ?, guards = ? WHERE ruler_name = ?');
            $updArmy->execute([$inf, $cos, $gua, $ruler]);
            // Синхронизуем в gos_app
            $updGos = $pdo->prepare('UPDATE gos_app SET peh = ?, kaz = ?, gva = ? WHERE ruler_name = ?');
            $updGos->execute([$inf, $cos, $gua, $ruler]);
        }

        // 2. Обновляем страны: возвращаем учёных (bots) и рабочих
        $stCountry = $pdo->prepare('SELECT workers, bots FROM countries WHERE ruler_name = ? LIMIT 1');
        $stCountry->execute([$ruler]);
        $countryRow = $stCountry->fetch();
        if ($countryRow) {
            $curWorkers = (int)$countryRow['workers'];
            $curBots    = (int)$countryRow['bots'];
            $newBots    = $curBots + $scientists;
            $newWorkers = $curWorkers + $workers;
            $updCountry = $pdo->prepare('UPDATE countries SET bots = ?, workers = ? WHERE ruler_name = ?');
            $updCountry->execute([$newBots, $newWorkers, $ruler]);
            // Синхронизируем workers в gos_app
            $updGWorkers = $pdo->prepare('UPDATE gos_app SET workers = workers + ? WHERE ruler_name = ?');
            $updGWorkers->execute([$workers, $ruler]);
        }

        // 3. Удаляем задание обучения
        $del = $pdo->prepare('DELETE FROM training_jobs WHERE id = ?');
        $del->execute([$jobId]);

        // Пропускаем добавление этого задания в список, поскольку оно завершено
        continue;
    }

    // Обучение ещё в процессе — добавляем в список
    $jobs[] = [
        'id'                 => $jobId,
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