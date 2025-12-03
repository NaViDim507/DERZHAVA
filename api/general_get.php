<?php
// derzhava_api/general_get.php
// Получение состояния генерала для правителя
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

// ruler_name передаётся через POST
$ruler = trim($_POST['ruler_name'] ?? '');

if ($ruler === '') {
    respond(false, 'Не передан ruler_name', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

// Получаем строку из таблицы general_state
$stmt = $pdo->prepare('SELECT * FROM general_state WHERE ruler_name = ? LIMIT 1');
$stmt->execute([$ruler]);
$row = $stmt->fetch();

if (!$row) {
    respond(false, 'Состояние генерала не найдено', null);
}

$general = [
    'ruler_name' => $row['ruler_name'],
    'level'      => (int)$row['level'],
    'attack'     => (int)$row['attack'],
    'defense'    => (int)$row['defense'],
    'leadership' => (int)$row['leadership'],
    'experience' => (int)$row['experience'],
    'battles'    => (int)$row['battles'],
    'wins'       => (int)$row['wins'],
];

respond(true, 'OK', $general);

function respond(bool $success, string $message, ?array $general): void
{
    echo json_encode(
        [
            'success' => $success,
            'message' => $message,
            'general' => $general,
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}