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

// Перед добавлением проверяем, достаточно ли рабочих и ресурсов у страны
try {
    // Получаем текущие ресурсы и рабочих
    $stCountry = $pdo->prepare('SELECT money, food, wood, metal, workers FROM countries WHERE ruler_name = ? LIMIT 1');
    $stCountry->execute([$ruler]);
    $countryRow = $stCountry->fetch();
    if (!$countryRow) {
        respond(false, 'Страна не найдена', null);
    }
    $money  = (int)$countryRow['money'];
    $food   = (int)$countryRow['food'];
    $wood   = (int)$countryRow['wood'];
    $metal  = (int)$countryRow['metal'];
    $freeWorkers = (int)$countryRow['workers'];
    // Таблица стоимости по типу здания (как в Kotlin Buildings.cost)
    $costMoney = 0;
    $costFood  = 0;
    $costWood  = 0;
    $costMetal = 0;
    switch ($buildingType) {
        case 1: // KOMBINAT
            $costMoney = 2000;
            $costWood  = 800;
            $costMetal = 600;
            break;
        case 2: // TOWN
            $costMoney = 1500;
            $costFood  = 500;
            $costWood  = 600;
            break;
        case 3: // COMMAND_CENTER
            $costMoney = 3000;
            $costMetal = 800;
            break;
        case 4: // WAR_BASE
            $costMoney = 2500;
            $costFood  = 500;
            $costWood  = 400;
            $costMetal = 800;
            break;
        case 5: // PERIMETR
            $costMoney = 3000;
            $costWood  = 700;
            $costMetal = 700;
            break;
        case 6: // BIRZHA
            $costMoney = 4000;
            $costWood  = 300;
            $costMetal = 300;
            break;
        case 7: // WATCH_TOWER
            $costMoney = 1500;
            $costWood  = 400;
            $costMetal = 400;
            break;
        default:
            $costMoney = 0;
            $costFood  = 0;
            $costWood  = 0;
            $costMetal = 0;
    }
    // Проверяем достаточность ресурсов и рабочих
    if ($workers > $freeWorkers ||
        $money < $costMoney ||
        $food  < $costFood  ||
        $wood  < $costWood  ||
        $metal < $costMetal) {
        respond(false, 'Недостаточно ресурсов или рабочих', null);
    }
    // Списываем ресурсы и рабочих
    $updCountry = $pdo->prepare('UPDATE countries SET money = money - ?, food = food - ?, wood = wood - ?, metal = metal - ?, workers = workers - ? WHERE ruler_name = ?');
    $updCountry->execute([$costMoney, $costFood, $costWood, $costMetal, $workers, $ruler]);
    // Списываем деньги и рабочих в gos_app (работа не влияет на еду/ресурсы в gos_app, только на деньги и рабочих)
    $updGos = $pdo->prepare('UPDATE gos_app SET money = money - ?, workers = workers - ? WHERE ruler_name = ?');
    $updGos->execute([$costMoney, $workers, $ruler]);
    // Добавляем задание на стройку
    $ins = $pdo->prepare('INSERT INTO build_tasks (ruler_name, building_type, workers, start_time_millis, end_time_millis) VALUES (?, ?, ?, ?, ?)');
    $ins->execute([$ruler, $buildingType, $workers, $start, $end]);
    $id = (int)$pdo->lastInsertId();
    respond(true, 'Задача добавлена', ['id' => $id]);
} catch (Throwable $ex) {
    respond(false, 'Ошибка при создании задачи', null);
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