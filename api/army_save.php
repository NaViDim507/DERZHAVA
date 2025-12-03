<?php
// derzhava_api/army_save.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$ruler = trim($_POST['ruler_name'] ?? '');
$peh   = (int)($_POST['peh'] ?? 0);
$kaz   = (int)($_POST['kaz'] ?? 0);
$gva   = (int)($_POST['gva'] ?? 0);

if ($ruler === '') {
    respond(false, 'Не передан ruler_name');
}

if ($peh < 0 || $kaz < 0 || $gva < 0) {
    respond(false, 'Некорректные числа войск');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

// Сохраняем состояние армии в таблицу army_state.
// Если запись существует — обновляем, иначе вставляем.
// Также инициализируем параметры атаки/защиты по умолчанию при создании.
$stmt = $pdo->prepare('SELECT ruler_name FROM army_state WHERE ruler_name = ? LIMIT 1');
$stmt->execute([$ruler]);
$exists = $stmt->fetch();

if ($exists) {
    // UPDATE
    $upd = $pdo->prepare('UPDATE army_state SET infantry = ?, cossacks = ?, guards = ? WHERE ruler_name = ?');
    $upd->execute([$peh, $kaz, $gva, $ruler]);
    respond(true, 'Армия обновлена');
} else {
    // INSERT с дефолтными параметрами атаки/защиты и нулевыми катапультами
    $ins = $pdo->prepare('INSERT INTO army_state (ruler_name, infantry, cossacks, guards, catapults, infantry_attack, infantry_defense, cossack_attack, cossack_defense, guard_attack, guard_defense) VALUES (?, ?, ?, ?, 0, 10, 10, 15, 12, 20, 18)');
    $ins->execute([$ruler, $peh, $kaz, $gva]);
    respond(true, 'Армия создана');
}

function respond(bool $success, string $message): void
{
    echo json_encode(
        [
            'success' => $success,
            'message' => $message,
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}
