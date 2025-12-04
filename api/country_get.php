<?php
// derzhava_api/country_get.php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$ruler = $_POST['ruler_name'] ?? '';

if ($ruler === '') {
    respond(false, 'Не передан ruler_name', null);
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$stmt = $pdo->prepare('SELECT * FROM countries WHERE ruler_name = ? LIMIT 1');
$stmt->execute([$ruler]);
$row = $stmt->fetch();

if (!$row) {
    respond(false, 'Страна не найдена', null);
}

// Превращаем строку в аккуратный массив (можно отдать как есть)
$country = [
    'ruler_name'  => $row['ruler_name'],
    'country_name'=> $row['country_name'],

    'metal'   => (int)$row['metal'],
    'mineral' => (int)$row['mineral'],
    'wood'    => (int)$row['wood'],
    'food'    => (int)$row['food'],
    'money'   => (int)$row['money'],

    'workers' => (int)$row['workers'],
    'bots'    => (int)$row['bots'],

    'metall_workers'   => (int)$row['metall_workers'],
    'mine_workers'     => (int)$row['mine_workers'],
    'wood_workers'     => (int)$row['wood_workers'],
    'industry_workers' => (int)$row['industry_workers'],

    'last_tax_time'               => (int)$row['last_tax_time'],
    'last_production_time'        => (int)$row['last_production_time'],
    'last_resource_update_time'   => (int)$row['last_resource_update_time'],
    'last_population_update_time' => (int)$row['last_population_update_time'],
    'population_growth_enabled'   => (int)$row['population_growth_enabled'] === 1,
    'science_growth_bonus'        => (int)$row['science_growth_bonus'],
    'stash_money'                 => (int)$row['stash_money'],

    'global_science_level' => (int)$row['global_science_level'],
    'science_metal'        => (int)$row['science_metal'],
    'science_stone'        => (int)$row['science_stone'],
    'science_wood'         => (int)$row['science_wood'],
    'science_food'         => (int)$row['science_food'],
    'science_demolition'   => (int)$row['science_demolition'],

    'epoch' => (int)$row['epoch'],

    'bunker_infantry' => (int)$row['bunker_infantry'],
    'bunker_cossacks' => (int)$row['bunker_cossacks'],
    'bunker_guards'   => (int)$row['bunker_guards'],

    'peh' => (int)$row['peh'],
    'kaz' => (int)$row['kaz'],
    'gva' => (int)$row['gva'],

    'domik1' => (int)$row['domik1'],
    'domik2' => (int)$row['domik2'],
    'domik3' => (int)$row['domik3'],
    'domik4' => (int)$row['domik4'],
    'domik5' => (int)$row['domik5'],
    'domik6' => (int)$row['domik6'],
    'domik7' => (int)$row['domik7'],

    'land' => (int)$row['land'],
    'lesa' => (int)$row['lesa'],
    'shah' => (int)$row['shah'],
    'rudn' => (int)$row['rudn'],
    'pole' => (int)$row['pole'],

    'zah'           => (int)$row['zah'],
    'is_npc'        => (int)$row['is_npc'] === 1,
    'defense_level' => (int)$row['defense_level'],
];

respond(true, 'OK', $country);

function respond(bool $success, string $message, ?array $country): void
{
    echo json_encode(
        [
            'success' => $success,
            'message' => $message,
            'country' => $country,
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}
