<?php
// derzhava_api/users_get.php
// Возвращает список всех пользователей (администраторы и обычные игроки).
// Используется в админ‑панели для просмотра списка игроков и администраторов.
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$secret = $_POST['secret'] ?? '';

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

// Загружаем все записи из таблицы users
$stmt = $pdo->query('SELECT id, ruler_name, country_name, is_admin, registration_time, last_login_time FROM users ORDER BY ruler_name');
$rows = $stmt->fetchAll();

$users = [];
foreach ($rows as $row) {
    $users[] = [
        'id' => (int)$row['id'],
        'ruler_name' => $row['ruler_name'],
        'country_name' => $row['country_name'],
        'is_admin' => (int)$row['is_admin'],
        'registration_time' => (int)$row['registration_time'],
        'last_login_time' => (int)$row['last_login_time'],
    ];
}

respond(true, 'OK', $users);

function respond(bool $success, string $message, ?array $users): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'users' => $users,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}