<?php
// derzhava_api/general_save.php
// Сохранение состояния генерала на сервер
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$input = file_get_contents('php://input');
$data  = json_decode($input, true);

if (!is_array($data)) {
    respond(false, 'Некорректный JSON');
}

$ruler = trim($data['ruler_name'] ?? '');
if ($ruler === '') {
    respond(false, 'Не передан ruler_name');
}

// Значения генерала
$level      = (int)($data['level'] ?? 1);
$attack     = (int)($data['attack'] ?? 0);
$defense    = (int)($data['defense'] ?? 0);
$leadership = (int)($data['leadership'] ?? 0);
$experience = (int)($data['experience'] ?? 0);
$battles    = (int)($data['battles'] ?? 0);
$wins       = (int)($data['wins'] ?? 0);

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

// Проверяем, есть ли запись
$stmt = $pdo->prepare('SELECT ruler_name FROM general_state WHERE ruler_name = ? LIMIT 1');
$stmt->execute([$ruler]);
$exists = $stmt->fetch();

if ($exists) {
    // UPDATE
    $upd = $pdo->prepare('UPDATE general_state SET level = ?, attack = ?, defense = ?, leadership = ?, experience = ?, battles = ?, wins = ? WHERE ruler_name = ?');
    $upd->execute([$level, $attack, $defense, $leadership, $experience, $battles, $wins, $ruler]);
    respond(true, 'Состояние генерала обновлено');
} else {
    // INSERT
    $ins = $pdo->prepare('INSERT INTO general_state (ruler_name, level, attack, defense, leadership, experience, battles, wins) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
    $ins->execute([$ruler, $level, $attack, $defense, $leadership, $experience, $battles, $wins]);
    respond(true, 'Состояние генерала создано');
}

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}