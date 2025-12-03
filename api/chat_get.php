<?php
// derzhava_api/chat_get.php
// Получить сообщения общего чата (Ассамблеи)
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

// необязательный параметр since_ts для получения сообщений позже отметки (ms)
$sinceTs = isset($_POST['since_ts']) ? (int)$_POST['since_ts'] : 0;

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД', null);
}

$query = 'SELECT * FROM chat_messages';
$params = [];
if ($sinceTs > 0) {
    $query .= ' WHERE timestamp_millis > ?';
    $params[] = $sinceTs;
}
$query .= ' ORDER BY timestamp_millis ASC';

$st = $pdo->prepare($query);
$st->execute($params);
$rows = $st->fetchAll();

$messages = [];
foreach ($rows as $row) {
    $messages[] = [
        'id'               => (int)$row['id'],
        'ruler_name'       => $row['ruler_name'],
        'country_name'     => $row['country_name'],
        'text'             => $row['text'],
        'timestamp_millis' => (int)$row['timestamp_millis'],
        'is_private'       => (int)$row['is_private'] === 1,
        'target_ruler_name'=> $row['target_ruler_name'],
        'is_system'        => (int)$row['is_system'] === 1,
        'medal_path'       => $row['medal_path'],
    ];
}

respond(true, 'OK', $messages);

function respond(bool $success, string $message, ?array $messages): void
{
    echo json_encode([
        'success'  => $success,
        'message'  => $message,
        'messages' => $messages,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}