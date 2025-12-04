<?php
// derzhava_api/login.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$mode     = $_POST['mode'] ?? 'login';      // login | register
$ruler    = trim($_POST['ruler_name'] ?? '');
$country  = trim($_POST['country_name'] ?? '');
$password = $_POST['password'] ?? '';

if ($ruler === '' || $password === '') {
    respond(false, 'Заполни логин и пароль');
}

if ($mode === 'register' && $country === '') {
    respond(false, 'Укажи название державы');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

if ($mode === 'register') {
    handle_register($pdo, $ruler, $country, $password);
} else {
    handle_login($pdo, $ruler, $password);
}

// ---------- функции ----------

function handle_register(PDO $pdo, string $ruler, string $country, string $password): void
{
    // проверяем, есть ли уже такой правитель
    $stmt = $pdo->prepare('SELECT id FROM users WHERE ruler_name = ? LIMIT 1');
    $stmt->execute([$ruler]);
    if ($stmt->fetch()) {
        respond(false, 'Такой правитель уже существует');
    }

    $hash = password_hash($password, PASSWORD_DEFAULT);

    // При регистрации фиксируем время в миллисекундах UNIX. is_admin по умолчанию 0.
    $now = (int)(microtime(true) * 1000);
    $stmt = $pdo->prepare('
        INSERT INTO users (ruler_name, country_name, password_hash, is_admin, registration_time, last_login_time)
        VALUES (?, ?, ?, 0, ?, ?)
    ');
    $stmt->execute([$ruler, $country, $hash, $now, $now]);

    $id = (int)$pdo->lastInsertId();

    $user = [
        'id'              => $id,
        'ruler_name'      => $ruler,
        'country_name'    => $country,
        'is_admin'        => 0,
        'registration_time' => $now,
        'last_login_time'   => $now,
    ];

    respond(true, 'Регистрация успешна', $user);
}

function handle_login(PDO $pdo, string $ruler, string $password): void
{
    $stmt = $pdo->prepare('
        SELECT id, ruler_name, country_name, password_hash, is_admin, registration_time, last_login_time
        FROM users
        WHERE ruler_name = ?
        LIMIT 1
    ');
    $stmt->execute([$ruler]);
    $row = $stmt->fetch();

    if (!$row || !password_verify($password, $row['password_hash'])) {
        respond(false, 'Неверный логин или пароль');
    }

    // Обновляем время последнего входа
    $now = (int)(microtime(true) * 1000);
    $upd = $pdo->prepare('UPDATE users SET last_login_time = ? WHERE id = ?');
    $upd->execute([$now, $row['id']]);

    $user = [
        'id'              => (int)$row['id'],
        'ruler_name'      => $row['ruler_name'],
        'country_name'    => $row['country_name'],
        'is_admin'        => (int)$row['is_admin'],
        'registration_time' => (int)$row['registration_time'],
        'last_login_time'   => $now,
    ];

    respond(true, 'Успешный вход', $user);
}

function respond(bool $success, string $message, ?array $user = null): void
{
    echo json_encode(
        [
            'success' => $success,
            'message' => $message,
            'user'    => $user,
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}
