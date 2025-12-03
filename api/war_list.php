<?php
// derzhava_api/war_list.php
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

$sql = '
    SELECT *
    FROM wars_app
    WHERE attacker_ruler = :r OR defender_ruler = :r
    ORDER BY start_at DESC
';
$st = $pdo->prepare($sql);
$st->execute([':r' => $ruler]);

$rows = [];
while ($row = $st->fetch()) {
    $rows[] = map_war_row($row);
}

respond(true, 'OK', $rows);

function map_war_row(array $row): array
{
    return [
        'id' => (int)$row['id'],
        'attacker_ruler'   => $row['attacker_ruler'],
        'defender_ruler'   => $row['defender_ruler'],
        'attacker_country' => $row['attacker_country'],
        'defender_country' => $row['defender_country'],
        'attacker_peh'     => (int)$row['attacker_peh'],
        'attacker_kaz'     => (int)$row['attacker_kaz'],
        'attacker_gva'     => (int)$row['attacker_gva'],
        'total_raids'      => (int)$row['total_raids'],
        'total_captures'   => (int)$row['total_captures'],
        'start_at'         => (int)$row['start_at'],
        'can_raid_at'      => (int)$row['can_raid_at'],
        'can_capture_at'   => (int)$row['can_capture_at'],
        'last_demolition_at' => (int)$row['last_demolition_at'],
        'state'            => $row['state'],
        'is_resolved'      => (int)$row['is_resolved'] === 1,
        'attacker_won'     => $row['attacker_won'] === null ? null : ((int)$row['attacker_won'] === 1),
        'recon_acc'        => (int)$row['recon_acc'],
    ];
}

function respond(bool $success, string $message, ?array $wars): void
{
    echo json_encode(
        [
            'success' => $success,
            'message' => $message,
            'wars'    => $wars,
        ],
        JSON_UNESCAPED_UNICODE
    );
    exit;
}
