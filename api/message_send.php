<?php
// derzhava_api/message_send.php
// Отправить приватное сообщение другому правителю
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$sender  = trim($_POST['sender_ruler'] ?? '');
$target  = trim($_POST['target_ruler'] ?? '');
$text    = trim($_POST['text'] ?? '');
$type    = trim($_POST['type'] ?? 'generic');
$payload = trim($_POST['payload_ruler'] ?? '');

if ($sender === '' || $target === '' || $text === '') {
    respond(false, 'Некорректные данные');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$ts = (int)round(microtime(true) * 1000);
$ins = $pdo->prepare(
    'INSERT INTO messages (ruler_name, text, timestamp_millis, is_read, type, payload_ruler)
     VALUES (?, ?, ?, 0, ?, ?)' 
);
$ins->execute([$target, $text, $ts, $type, ($payload !== '' ? $payload : null)]);
$msgId = (int)$pdo->lastInsertId();

respond(true, 'Сообщение отправлено', [ 'message_id' => $msgId ]);

function respond(bool $success, string $message, ?array $extra = null): void
{
    $resp = [
        'success' => $success,
        'message' => $message,
    ];
    if ($extra !== null) {
        $resp += $extra;
    }
    echo json_encode($resp, JSON_UNESCAPED_UNICODE);
    exit;
}