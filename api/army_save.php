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
	sync_gos_app_army($pdo, $ruler, $peh, $kaz, $gva);
    respond(true, 'Армия обновлена');
} else {
    // INSERT с дефолтными параметрами атаки/защиты и нулевыми катапультами
    $ins = $pdo->prepare('INSERT INTO army_state (ruler_name, infantry, cossacks, guards, catapults, infantry_attack, infantry_defense, cossack_attack, cossack_defense, guard_attack, guard_defense) VALUES (?, ?, ?, ?, 0, 10, 10, 15, 12, 20, 18)');
    $ins->execute([$ruler, $peh, $kaz, $gva]);
	sync_gos_app_army($pdo, $ruler, $peh, $kaz, $gva);
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
function sync_gos_app_army(PDO $pdo, string $ruler, int $peh, int $kaz, int $gva): void
{
    $st = $pdo->prepare('SELECT country_name FROM gos_app WHERE ruler_name = ? LIMIT 1');
    $st->execute([$ruler]);
    $row = $st->fetch();

    if ($row) {
        $upd = $pdo->prepare('UPDATE gos_app SET peh = ?, kaz = ?, gva = ? WHERE ruler_name = ?');
        $upd->execute([$peh, $kaz, $gva, $ruler]);
        return;
    }

    $countryName = $ruler;

    // Попытаемся взять название страны из основной таблицы, если она создана
    $stCountry = $pdo->prepare('SELECT country_name, money, workers, land, zah, domik1, domik2, domik3, domik4, domik5, domik6, domik7 FROM countries WHERE ruler_name = ? LIMIT 1');
    $stCountry->execute([$ruler]);
    if ($country = $stCountry->fetch()) {
        $countryName = $country['country_name'];
    } else {
        $country = [
            'money' => 0,
            'workers' => 0,
            'land' => 0,
            'zah' => 0,
            'domik1' => 0,
            'domik2' => 0,
            'domik3' => 0,
            'domik4' => 0,
            'domik5' => 0,
            'domik6' => 0,
            'domik7' => 0,
        ];
    }

    $ins = $pdo->prepare('INSERT INTO gos_app (ruler_name, country_name, peh, kaz, gva, money, workers, land, zah, domik1, domik2, domik3, domik4, domik5, domik6, domik7) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $ins->execute([
        $ruler,
        $countryName,
        $peh,
        $kaz,
        $gva,
        (int)($country['money'] ?? 0),
        (int)($country['workers'] ?? 0),
        (int)($country['land'] ?? 0),
        (int)($country['zah'] ?? 0),
        !empty($country['domik1']) ? 1 : 0,
        !empty($country['domik2']) ? 1 : 0,
        !empty($country['domik3']) ? 1 : 0,
        !empty($country['domik4']) ? 1 : 0,
        !empty($country['domik5']) ? 1 : 0,
        !empty($country['domik6']) ? 1 : 0,
        !empty($country['domik7']) ? 1 : 0,
    ]);
}

