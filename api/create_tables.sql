-- SQL script to create all necessary tables for Derzhava online game

-- Users table for login/registration
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL UNIQUE,
  country_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  -- Новый флаг: 1 означает, что аккаунт имеет права администратора. 0 — обычный игрок.
  is_admin TINYINT DEFAULT 0,
  -- Время регистрации в миллисекундах Unix. Заполняется при создании пользователя.
  registration_time BIGINT DEFAULT 0,
  -- Время последнего входа (обновляется при успешной авторизации).
  last_login_time BIGINT DEFAULT 0
);

-- Legacy gos_app table (kept for war scripts compatibility)
-- Stores simplified country and army info together
CREATE TABLE IF NOT EXISTS gos_app (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL UNIQUE,
  country_name VARCHAR(100) NOT NULL,
  peh INT DEFAULT 0,
  kaz INT DEFAULT 0,
  gva INT DEFAULT 0,
  money INT DEFAULT 0,
  workers INT DEFAULT 0,
  land INT DEFAULT 0,
  zah INT DEFAULT 0,
  domik1 TINYINT DEFAULT 0,
  domik2 TINYINT DEFAULT 0,
  domik3 TINYINT DEFAULT 0,
  domik4 TINYINT DEFAULT 0,
  domik5 TINYINT DEFAULT 0,
  domik6 TINYINT DEFAULT 0,
  domik7 TINYINT DEFAULT 0
);

-- Countries table (state of resources, buildings, etc.)
CREATE TABLE IF NOT EXISTS countries (
  ruler_name VARCHAR(100) PRIMARY KEY,
  country_name VARCHAR(100) NOT NULL,
  metal INT DEFAULT 1500,
  mineral INT DEFAULT 2000,
  wood INT DEFAULT 2000,
  food INT DEFAULT 3000,
  money INT DEFAULT 20000,
  workers INT DEFAULT 300,
  bots INT DEFAULT 50,
  metall_workers INT DEFAULT 0,
  mine_workers INT DEFAULT 0,
  wood_workers INT DEFAULT 0,
  industry_workers INT DEFAULT 0,
  last_tax_time BIGINT DEFAULT 0,
  last_production_time BIGINT DEFAULT 0,
  population_growth_enabled TINYINT DEFAULT 1,
  science_growth_bonus INT DEFAULT 10,
  stash_money INT DEFAULT 0,
  last_resource_update_time BIGINT DEFAULT 0,
  last_population_update_time BIGINT DEFAULT 0,
  global_science_level INT DEFAULT 10,
  science_metal INT DEFAULT 10,
  science_stone INT DEFAULT 10,
  science_wood INT DEFAULT 10,
  science_food INT DEFAULT 10,
  science_demolition INT DEFAULT 0,
  epoch INT DEFAULT 1,
  bunker_infantry INT DEFAULT 0,
  bunker_cossacks INT DEFAULT 0,
  bunker_guards INT DEFAULT 0,
  peh INT DEFAULT 0,
  kaz INT DEFAULT 0,
  gva INT DEFAULT 0,
  domik1 INT DEFAULT 0,
  domik2 INT DEFAULT 0,
  domik3 INT DEFAULT 0,
  domik4 INT DEFAULT 0,
  domik5 INT DEFAULT 0,
  domik6 INT DEFAULT 0,
  domik7 INT DEFAULT 0,
  land INT DEFAULT 1000,
  lesa INT DEFAULT 1500,
  shah INT DEFAULT 1000,
  rudn INT DEFAULT 1500,
  pole INT DEFAULT 2000,
  is_npc TINYINT DEFAULT 0,
  npc_note TEXT,
  defense_level INT DEFAULT 0,
  zah INT DEFAULT 0
);

-- Army state per ruler
CREATE TABLE IF NOT EXISTS army_state (
  ruler_name VARCHAR(100) PRIMARY KEY,
  infantry INT DEFAULT 0,
  cossacks INT DEFAULT 0,
  guards INT DEFAULT 0,
  catapults INT DEFAULT 0,
  infantry_attack INT DEFAULT 10,
  infantry_defense INT DEFAULT 10,
  cossack_attack INT DEFAULT 15,
  cossack_defense INT DEFAULT 12,
  guard_attack INT DEFAULT 20,
  guard_defense INT DEFAULT 18
);

-- General state
CREATE TABLE IF NOT EXISTS general_state (
  ruler_name VARCHAR(100) PRIMARY KEY,
  level INT DEFAULT 1,
  attack INT DEFAULT 0,
  defense INT DEFAULT 0,
  leadership INT DEFAULT 0,
  experience BIGINT DEFAULT 0,
  battles INT DEFAULT 0,
  wins INT DEFAULT 0
);

-- Command center state
CREATE TABLE IF NOT EXISTS command_center (
  ruler_name VARCHAR(100) PRIMARY KEY,
  intel INT DEFAULT 10,
  sabotage INT DEFAULT 10,
  theft INT DEFAULT 10,
  agitation INT DEFAULT 10,
  last_recon_time BIGINT DEFAULT 0,
  last_sabotage_time BIGINT DEFAULT 0,
  last_theft_time BIGINT DEFAULT 0,
  last_alliance_time BIGINT DEFAULT 0
);

-- Build tasks
CREATE TABLE IF NOT EXISTS build_tasks (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  building_type INT NOT NULL,
  workers INT NOT NULL,
  start_time_millis BIGINT NOT NULL,
  end_time_millis BIGINT NOT NULL
);

-- Training jobs (troop training)
CREATE TABLE IF NOT EXISTS training_jobs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  unit_type INT NOT NULL,
  workers INT NOT NULL,
  scientists INT NOT NULL,
  start_time_millis BIGINT NOT NULL,
  duration_seconds INT NOT NULL
);

-- Research jobs (science upgrades)
CREATE TABLE IF NOT EXISTS research_jobs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  science_type INT NOT NULL,
  start_time_millis BIGINT NOT NULL,
  duration_seconds INT NOT NULL,
  scientists INT NOT NULL,
  progress_points INT NOT NULL
);

-- Scientist training jobs (botan)
CREATE TABLE IF NOT EXISTS scientist_training_jobs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  workers INT NOT NULL,
  scientists INT NOT NULL,
  start_time_millis BIGINT NOT NULL,
  duration_seconds INT NOT NULL
);

-- Market offers (birzha)
CREATE TABLE IF NOT EXISTS market_offers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  resource_type INT NOT NULL,
  amount INT NOT NULL,
  price_per_unit INT NOT NULL
);

-- Messages (private notifications)
CREATE TABLE IF NOT EXISTS messages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  text TEXT NOT NULL,
  timestamp_millis BIGINT NOT NULL,
  is_read TINYINT DEFAULT 0,
  type VARCHAR(50) DEFAULT 'generic',
  payload_ruler VARCHAR(100) DEFAULT NULL
);

-- Chat messages (assembly)
CREATE TABLE IF NOT EXISTS chat_messages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  country_name VARCHAR(100) NOT NULL,
  text TEXT NOT NULL,
  timestamp_millis BIGINT NOT NULL,
  is_private TINYINT DEFAULT 0,
  target_ruler_name VARCHAR(100) DEFAULT NULL,
  is_system TINYINT DEFAULT 0,
  medal_path VARCHAR(255) DEFAULT NULL
);

-- Special targets (NPC neighbours)
CREATE TABLE IF NOT EXISTS special_targets (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ruler_name VARCHAR(100) NOT NULL,
  country_name VARCHAR(100) NOT NULL,
  perimeter INT NOT NULL,
  security INT NOT NULL,
  money INT DEFAULT 0,
  is_ally TINYINT DEFAULT 0
);

-- Alliances between players
CREATE TABLE IF NOT EXISTS alliances (
  id INT AUTO_INCREMENT PRIMARY KEY,
  rulerA VARCHAR(100) NOT NULL,
  rulerB VARCHAR(100) NOT NULL,
  initiator VARCHAR(100) NOT NULL,
  status INT NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  responded_at BIGINT DEFAULT NULL,
  UNIQUE KEY (rulerA, rulerB)
);

-- Active wars
CREATE TABLE IF NOT EXISTS wars_app (
  id INT AUTO_INCREMENT PRIMARY KEY,
  attacker_ruler VARCHAR(100) NOT NULL,
  defender_ruler VARCHAR(100) NOT NULL,
  attacker_country VARCHAR(100) NOT NULL,
  defender_country VARCHAR(100) NOT NULL,
  attacker_peh INT NOT NULL,
  attacker_kaz INT NOT NULL,
  attacker_gva INT NOT NULL,
  total_raids INT DEFAULT 0,
  total_captures INT DEFAULT 0,
  start_at INT NOT NULL,
  can_raid_at INT NOT NULL,
  can_capture_at INT NOT NULL,
  last_demolition_at INT DEFAULT 0,
  state VARCHAR(20) NOT NULL,
  is_resolved TINYINT DEFAULT 0,
  attacker_won TINYINT DEFAULT NULL,
  recon_acc INT DEFAULT 0
);

-- War logs
CREATE TABLE IF NOT EXISTS war_logs_app (
  id INT AUTO_INCREMENT PRIMARY KEY,
  war_id INT NOT NULL,
  ts INT NOT NULL,
  text TEXT NOT NULL
);

-- War moves (attacks, captures, demolitions)
CREATE TABLE IF NOT EXISTS war_moves_app (
  id INT AUTO_INCREMENT PRIMARY KEY,
  war_id INT NOT NULL,
  ts INT NOT NULL,
  move_type VARCHAR(20) NOT NULL,
  payload TEXT
);

-- Indexes for quicker lookups
CREATE INDEX IF NOT EXISTS idx_messages_ruler ON messages (ruler_name);
CREATE INDEX IF NOT EXISTS idx_chat_messages_ts ON chat_messages (timestamp_millis);
CREATE INDEX IF NOT EXISTS idx_special_targets_ruler ON special_targets (ruler_name);