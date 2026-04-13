-- ------------------------------------------------------------
-- Add numeric direction field for realtime car tables
-- Direction code:
--   1 = to Turpan
--   2 = to Hami
-- ------------------------------------------------------------

SET NAMES utf8mb4;

ALTER TABLE uc_car_real_time
    ADD COLUMN IF NOT EXISTS direction TINYINT NULL COMMENT 'direction code: 1=turpan, 2=hami' AFTER real_speed;

ALTER TABLE uc_car_real_time_current
    ADD COLUMN IF NOT EXISTS direction TINYINT NULL COMMENT 'direction code: 1=turpan, 2=hami' AFTER real_speed;

-- Backfill from legacy text field driving_direction
UPDATE uc_car_real_time
SET direction = CASE
    WHEN direction IN (1, 2) THEN direction
    WHEN LOWER(driving_direction) IN (
        '1', 'up', 'turpan', 'toez', 'to_ez',
        'hamimi_to_tuyugou', 'hami_to_turpan', 'to_turpan'
    ) THEN 1
    WHEN LOWER(driving_direction) IN (
        '2', 'down', 'hami', 'towh', 'to_wh',
        'tuyugou_to_hamimi', 'turpan_to_hami', 'to_hami'
    ) THEN 2
    ELSE direction
END
WHERE direction IS NULL OR direction NOT IN (1, 2);

UPDATE uc_car_real_time_current
SET direction = CASE
    WHEN direction IN (1, 2) THEN direction
    WHEN LOWER(driving_direction) IN (
        '1', 'up', 'turpan', 'toez', 'to_ez',
        'hamimi_to_tuyugou', 'hami_to_turpan', 'to_turpan'
    ) THEN 1
    WHEN LOWER(driving_direction) IN (
        '2', 'down', 'hami', 'towh', 'to_wh',
        'tuyugou_to_hamimi', 'turpan_to_hami', 'to_hami'
    ) THEN 2
    ELSE direction
END
WHERE direction IS NULL OR direction NOT IN (1, 2);

ALTER TABLE uc_car_real_time
    ADD INDEX idx_uc_car_real_time_direction (direction);

ALTER TABLE uc_car_real_time_current
    ADD INDEX idx_uc_car_real_time_current_direction (direction);

