<?php
// derzhava_api/messages_get.php
// Получить список сообщений для конкретного правителя
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

$st = $pdo->prepare('SELECT * FROM messages WHERE ruler_name = ? ORDER BY timestamp_millis DESC');
$st->execute([$ruler]);
$rows = $st->fetchAll();

$messages = [];
foreach ($rows as $row) {
    $messages[] = [
        'id'              => (int)$row['id'],
        'ruler_name'      => $row['ruler_name'],
        'text'            => $row['text'],
        'timestamp_millis'=> (int)$row['timestamp_millis'],
        'is_read'         => (int)$row['is_read'] === 1,
        'type'            => $row['type'],
        'payload_ruler'   => $row['payload_ruler'],
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