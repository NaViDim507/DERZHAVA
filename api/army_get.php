<?php
// derzhava_api/army_get.php
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

// Армия хранится в таблице army_state
$st = $pdo->prepare('SELECT * FROM army_state WHERE ruler_name = ? LIMIT 1');
$st->execute([$ruler]);
$row = $st->fetch();

if (!$row) {
    respond(false, 'Армия не найдена', null);
}

$army = [
    'ruler_name' => $row['ruler_name'],
    'peh'        => (int)$row['infantry'],
    'kaz'        => (int)$row['cossacks'],
    'gva'        => (int)$row['guards'],
    'catapults'  => (int)$row['catapults'],
    'infantry_attack'  => (int)$row['infantry_attack'],
    'infantry_defense' => (int)$row['infantry_defense'],
    'cossack_attack'   => (int)$row['cossack_attack'],
    'cossack_defense'  => (int)$row['cossack_defense'],
    'guard_attack'     => (int)$row['guard_attack'],
    'guard_defense'    => (int)$row['guard_defense'],
];

respond(true, 'OK', $army);

function respond(bool $success, string $message, ?array $army): void
{
    echo json_encode(
        [
            'success' => $success,
            'message' => $message,
            'army'    => $army,
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}
