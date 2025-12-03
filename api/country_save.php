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
