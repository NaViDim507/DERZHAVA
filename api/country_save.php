<?php
// derzhava_api/country_save.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$input = file_get_contents('php://input');
$data  = json_decode($input, true);

if (!is_array($data)) {
    respond(false, 'Некорректный JSON');
}

$ruler = trim($data['ruler_name'] ?? '');
$countryName = trim($data['country_name'] ?? '');

if ($ruler === '' || $countryName === '') {
    respond(false, 'Не переданы ruler_name / country_name');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$fields = [
    'metal',
    'mineral',
    'wood',
    'food',
    'money',
    'workers',
    'bots',
    'metall_workers',
    'mine_workers',
    'wood_workers',
    'industry_workers',
    'last_tax_time',
    'last_production_time',
    'last_resource_update_time',
    'last_population_update_time',
    'population_growth_enabled',
    'science_growth_bonus',
    'stash_money',
    'global_science_level',
    'science_metal',
    'science_stone',
    'science_wood',
    'science_food',
    'science_demolition',
    'epoch',
    'bunker_infantry',
    'bunker_cossacks',
    'bunker_guards',
    'peh',
    'kaz',
    'gva',
    'domik1',
    'domik2',
    'domik3',
    'domik4',
    'domik5',
    'domik6',
    'domik7',
    'land',
    'lesa',
    'shah',
    'rudn',
    'pole',
    'zah',
    'is_npc',
    'defense_level',
];

$values = [];
foreach ($fields as $f) {
    $values[$f] = $data[$f] ?? null;
}

$values['population_growth_enabled'] = !empty($values['population_growth_enabled']) ? 1 : 0;
$values['is_npc'] = !empty($values['is_npc']) ? 1 : 0;

// Проверяем, есть ли уже строка
$stmt = $pdo->prepare('SELECT ruler_name FROM countries WHERE ruler_name = ? LIMIT 1');
$stmt->execute([$ruler]);

if ($stmt->fetch()) {
    // UPDATE
    $setParts = [];
    $params = [];

    $setParts[] = 'country_name = ?';
    $params[] = $countryName;

    foreach ($fields as $f) {
        $setParts[] = "`$f` = ?";
        $params[] = $values[$f];
    }

    $params[] = $ruler;

    $sql = 'UPDATE countries SET ' . implode(', ', $setParts) . ' WHERE ruler_name = ?';
    $upd = $pdo->prepare($sql);
    $upd->execute($params);

    sync_gos_app($pdo, $ruler, $countryName, $values);

    respond(true, 'Страна обновлена');
} else {
    // INSERT
    $cols = ['ruler_name', 'country_name'];
    $placeholders = ['?', '?'];
    $params = [$ruler, $countryName];

    foreach ($fields as $f) {
        $cols[] = "`$f`";
        $placeholders[] = '?';
        $params[] = $values[$f];
    }

    $sql = 'INSERT INTO countries (' . implode(',', $cols) . ') VALUES (' . implode(',', $placeholders) . ')';
    $ins = $pdo->prepare($sql);
    $ins->execute($params);
	
	sync_gos_app($pdo, $ruler, $countryName, $values);

    respond(true, 'Страна создана');
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
function sync_gos_app(PDO $pdo, string $ruler, string $countryName, array $values): void
{
    $gosFields = [
        'country_name' => $countryName,
        'peh'         => (int)($values['peh'] ?? 0),
        'kaz'         => (int)($values['kaz'] ?? 0),
        'gva'         => (int)($values['gva'] ?? 0),
        'money'       => (int)($values['money'] ?? 0),
        'workers'     => (int)($values['workers'] ?? 0),
        'land'        => (int)($values['land'] ?? 0),
        'zah'         => (int)($values['zah'] ?? 0),
        'domik1'      => !empty($values['domik1']) ? 1 : 0,
        'domik2'      => !empty($values['domik2']) ? 1 : 0,
        'domik3'      => !empty($values['domik3']) ? 1 : 0,
        'domik4'      => !empty($values['domik4']) ? 1 : 0,
        'domik5'      => !empty($values['domik5']) ? 1 : 0,
        'domik6'      => !empty($values['domik6']) ? 1 : 0,
        'domik7'      => !empty($values['domik7']) ? 1 : 0,
    ];

    $st = $pdo->prepare('SELECT ruler_name FROM gos_app WHERE ruler_name = ? LIMIT 1');
    $st->execute([$ruler]);

    if ($st->fetch()) {
        $setParts = [];
        $params   = [];

        foreach ($gosFields as $key => $val) {
            $setParts[] = "$key = ?";
            $params[]   = $val;
        }

        $params[] = $ruler;

        $sql = 'UPDATE gos_app SET ' . implode(', ', $setParts) . ' WHERE ruler_name = ?';
        $upd = $pdo->prepare($sql);
        $upd->execute($params);
    } else {
        $cols         = ['ruler_name'];
        $placeholders = ['?'];
        $params       = [$ruler];

        foreach ($gosFields as $key => $val) {
            $cols[]         = $key;
            $placeholders[] = '?';
            $params[]       = $val;
        }

        $sql = 'INSERT INTO gos_app (' . implode(',', $cols) . ') VALUES (' . implode(',', $placeholders) . ')';
        $ins = $pdo->prepare($sql);
        $ins->execute($params);
    }
}