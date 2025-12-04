<?php
// derzhava_api/build_task_delete.php
// Удалить задачу строительства по id
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

// При удалении задачи строительства нужно вернуть использованные ресурсы и рабочих.
// Сначала найдём задачу и узнаем, кто её создавал и сколько ресурсов было затрачено.
$stmt = $pdo->prepare('SELECT ruler_name, building_type, workers FROM build_tasks WHERE id = ? LIMIT 1');
$stmt->execute([$id]);
$task = $stmt->fetch();
if (!$task) {
    respond(false, 'Задача не найдена');
}
$rulerName = $task['ruler_name'];
$type = (int)$task['building_type'];
$workers = (int)$task['workers'];
// Таблица стоимости — должна совпадать с cost() из Kotlin
$costMoney = 0;
$costFood  = 0;
$costWood  = 0;
$costMetal = 0;
switch ($type) {
    case 1:
        $costMoney = 2000;
        $costWood  = 800;
        $costMetal = 600;
        break;
    case 2:
        $costMoney = 1500;
        $costFood  = 500;
        $costWood  = 600;
        break;
    case 3:
        $costMoney = 3000;
        $costMetal = 800;
        break;
    case 4:
        $costMoney = 2500;
        $costFood  = 500;
        $costWood  = 400;
        $costMetal = 800;
        break;
    case 5:
        $costMoney = 3000;
        $costWood  = 700;
        $costMetal = 700;
        break;
    case 6:
        $costMoney = 4000;
        $costWood  = 300;
        $costMetal = 300;
        break;
    case 7:
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
// Удаляем задачу
$del = $pdo->prepare('DELETE FROM build_tasks WHERE id = ?');
$del->execute([$id]);
// Возвращаем ресурсы и рабочих в страну
$updCountry = $pdo->prepare('UPDATE countries SET money = money + ?, food = food + ?, wood = wood + ?, metal = metal + ?, workers = workers + ? WHERE ruler_name = ?');
$updCountry->execute([$costMoney, $costFood, $costWood, $costMetal, $workers, $rulerName]);
// Возвращаем деньги и рабочих в gos_app (если запись существует)
$updGos = $pdo->prepare('UPDATE gos_app SET money = money + ?, workers = workers + ? WHERE ruler_name = ?');
$updGos->execute([$costMoney, $workers, $rulerName]);
respond(true, 'Задача удалена');

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}