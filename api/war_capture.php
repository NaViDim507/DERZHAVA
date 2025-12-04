<?php
header('Content-Type: application/json; charset=utf-8');
require_once __DIR__ . '/db.php';

/**
 * Универсальный ответ в JSON
 */
function respond(bool $success, string $message, ?array $data = null): void
{
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data'    => $data,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

// ---------- Входные параметры ----------

$warId        = isset($_POST['war_id']) ? (int)$_POST['war_id'] : 0;
$attacker     = isset($_POST['attacker_ruler']) ? trim($_POST['attacker_ruler']) : '';
$attackerWon  = isset($_POST['attacker_won']) ? (int)$_POST['attacker_won'] : null;

if ($warId <= 0 || $attacker === '') {
    respond(false, 'Не переданы идентификатор войны или атакующая сторона');
}

if ($attackerWon === null || ($attackerWon !== 0 && $attackerWon !== 1)) {
    respond(false, 'Не передан корректный результат боя (attacker_won)');
}

// ---------- Подключение к БД через PDO (как в других war_*.php) ----------

try {
    $dsn = "mysql:host={$DB_HOST};dbname={$DB_NAME};charset=utf8mb4";
    $pdo = new PDO($dsn, $DB_USER, $DB_PASS, [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
} catch (Throwable $e) {
    respond(false, 'Ошибка подключения к БД');
}

try {
    $pdo->beginTransaction();

    // ---------- 1. Берём войну на блокировку ----------

    $st = $pdo->prepare('SELECT * FROM wars_app WHERE id = ? FOR UPDATE');
    $st->execute([$warId]);
    $war = $st->fetch();

    if (!$war) {
        $pdo->rollBack();
        respond(false, 'Война не найдена');
    }

    if ($war['attacker_ruler'] !== $attacker) {
        $pdo->rollBack();
        respond(false, 'Захват может инициировать только атакующая сторона');
    }

    if (!empty($war['is_resolved'])) {
        $pdo->rollBack();
        respond(false, 'Война уже завершена');
    }

    $now = time();
    $canCaptureAt = (int)$war['can_capture_at'];

    if ($now < $canCaptureAt) {
        $pdo->rollBack();
        respond(false, 'До захвата ещё рано');
    }

    $atkPeh = (int)$war['attacker_peh'];
    $atkKaz = (int)$war['attacker_kaz'];
    $atkGva = (int)$war['attacker_gva'];

    if ($atkPeh + $atkKaz + $atkGva <= 0) {
        $pdo->rollBack();
        respond(false, 'На территории противника нет твоих войск');
    }

    $defender = $war['defender_ruler'];

    // ---------- 2. Берём обе страны на блокировку ----------

    $st = $pdo->prepare('SELECT * FROM gos_app WHERE ruler_name = ? FOR UPDATE');
    $st->execute([$attacker]);
    $attRow = $st->fetch();

    if (!$attRow) {
        $pdo->rollBack();
        respond(false, 'Страна атакующего не найдена в gos_app');
    }

    $st->execute([$defender]);
    $defRow = $st->fetch();

    if (!$defRow) {
        $pdo->rollBack();
        respond(false, 'Страна защитника не найдена в gos_app');
    }

    // ---------- 3. Награда за успешный захват ----------
    // (формула как в Kotlin: 30% денег и 20% земли + zah++)

    $rewardMoney = 0;
    $rewardLand  = 0;

    if ($attackerWon === 1) {
        $defMoney = (int)$defRow['money'];
        $defLand  = (int)$defRow['land'];

        $rewardMoney = (int)floor($defMoney * 0.30);
        $rewardLand  = (int)floor($defLand * 0.20);

        // Атакующему добавляем, защитнику отнимаем (но не уходим в минус)
        $st = $pdo->prepare('
            UPDATE gos_app
            SET money = money + ?, land = land + ?, zah = zah + 1
            WHERE ruler_name = ?
        ');
        $st->execute([$rewardMoney, $rewardLand, $attacker]);

        $st = $pdo->prepare('
            UPDATE gos_app
            SET money = GREATEST(0, money - ?),
                land  = GREATEST(0, land  - ?)
            WHERE ruler_name = ?
        ');
        $st->execute([$rewardMoney, $rewardLand, $defender]);
    }

    // ---------- 4. Обновляем состояние войны ----------

    $state = $attackerWon === 1 ? 'captured' : 'failed';

    $st = $pdo->prepare('
        UPDATE wars_app
        SET state = ?, attacker_won = ?, is_resolved = 1, ended_at = ?
        WHERE id = ?
    ');
    $st->execute([$state, $attackerWon, $now, $warId]);

    // ---------- 5. Лог в war_logs_app ----------

    $logText = $attackerWon === 1
        ? 'Захват успешен: атакующий захватил страну'
        : 'Захват провален: оборона отбила атаку';

    $st = $pdo->prepare('
        INSERT INTO war_logs_app (war_id, ts, text)
        VALUES (?, ?, ?)
    ');
    $st->execute([$warId, $now, $logText]);

    $pdo->commit();

    respond(true, $logText, [
        'war_id'       => $warId,
        'state'        => $state,
        'attacker_won' => $attackerWon,
        'reward_money' => $rewardMoney,
        'reward_land'  => $rewardLand,
        'ended_at'     => $now,
    ]);

} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    respond(false, 'Ошибка при обработке захвата: ' . $e->getMessage());
}
