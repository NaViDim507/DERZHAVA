<?php
// derzhava_api/chat_send.php
// Отправить сообщение в общий чат / приватку
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

$ruler    = trim($_POST['ruler_name'] ?? '');
$country  = trim($_POST['country_name'] ?? '');
$text     = trim($_POST['text'] ?? '');
$isPrivate= isset($_POST['is_private']) ? ((int)$_POST['is_private'] === 1) : false;
$target   = trim($_POST['target_ruler_name'] ?? '');
$isSystem = isset($_POST['is_system']) ? ((int)$_POST['is_system'] === 1) : false;
$medal    = trim($_POST['medal_path'] ?? '');

if ($ruler === '' || $country === '' || $text === '') {
    respond(false, 'Некорректные данные');
}
if ($isPrivate && $target === '') {
    respond(false, 'Не указана цель для приватного сообщения');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$ts = (int)round(microtime(true) * 1000);
$ins = $pdo->prepare(
    'INSERT INTO chat_messages (
       ruler_name, country_name, text, timestamp_millis, is_private, target_ruler_name, is_system, medal_path
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
);
$ins->execute([
    $ruler,
    $country,
    $text,
    $ts,
    $isPrivate ? 1 : 0,
    $isPrivate ? $target : null,
    $isSystem ? 1 : 0,
    $medal !== '' ? $medal : null,
]);
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