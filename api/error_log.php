<?php
// derzhava_api/error_log.php
// Сохраняет сообщение об ошибке, присланное клиентом, в таблицу error_logs.
// Принимает POST или JSON‑тело с полями:
//   ruler_name   (опционально) — правитель, от которого пришёл лог.
//   error_type   (обязательное) — тип ошибки: 'client' или 'server'.
//   message      (обязательное) — краткое описание ошибки.
//   context      (опционально) — дополнительная информация, например стек трейс, JSON окружения.

header('Content-Type: application/json; charset=utf-8');
require_once __DIR__ . '/db.php';

// Поддерживаем как application/json, так и form-urlencoded
if ($_SERVER['CONTENT_TYPE'] ?? '' === 'application/json') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
} else {
    $data = $_POST;
}

if (!is_array($data)) {
    respond(false, 'Некорректные данные');
}

$ruler   = isset($data['ruler_name']) ? trim($data['ruler_name']) : null;
$type    = isset($data['error_type']) ? trim($data['error_type']) : '';
$message = isset($data['message']) ? trim($data['message']) : '';
$context = isset($data['context']) ? trim($data['context']) : null;

if ($type === '' || $message === '') {
    respond(false, 'Не переданы обязательные поля');
}

try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

$now = (int)(microtime(true) * 1000);

$ins = $pdo->prepare('INSERT INTO error_logs (ruler_name, error_type, message, context, timestamp_millis) VALUES (?, ?, ?, ?, ?)');
$ins->execute([
    $ruler !== '' ? $ruler : null,
    $type,
    $message,
    $context,
    $now
]);

respond(true, 'Лог сохранён');

function respond(bool $success, string $message): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}