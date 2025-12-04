<?php
/**
 * Общий файл подключения к БД для всех API-скриптов.
 * Даёт функцию get_pdo() — именно её вызывают login.php, country_*.php, war_*.php.
 *
 * Не отправляем здесь никакого JSON — этим занимаются сами скрипты (login.php и т.п.),
 * чтобы не мешать их своим заголовкам/ответам.
 */

function get_pdo(): PDO
{
    static $pdo = null;
    if ($pdo !== null) {
        return $pdo;
    }

    // >>> ПОДГОНИ ПОД СВОЙ VPS, если надо <<<
    // Для удобства настройки на VPS значения можно задавать через переменные окружения:
    //   DB_HOST, DB_NAME, DB_USER, DB_PASS. Если переменная не задана, используется значение по умолчанию.
    $DB_HOST = getenv('DB_HOST') !== false ? getenv('DB_HOST') : 'localhost';
    $DB_NAME = getenv('DB_NAME') !== false ? getenv('DB_NAME') : 'derzhava';
    $DB_USER = getenv('DB_USER') !== false ? getenv('DB_USER') : 'deruser';
    $DB_PASS = getenv('DB_PASS') !== false ? getenv('DB_PASS') : '1psvs5';

    $dsn = "mysql:host={$DB_HOST};dbname={$DB_NAME};charset=utf8mb4";

    $options = [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ];

    try {
        $pdo = new PDO($dsn, $DB_USER, $DB_PASS, $options);
    } catch (Throwable $e) {
        // Здесь сознательно НИЧЕГО не выводим в JSON,
        // т.к. это делает вызывающий скрипт через свою respond().
        throw $e;
    }

    return $pdo;
}
