UPDATE settings SET value = '00:00', updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE key = 'work_start_time';

UPDATE settings SET value = '23:00', updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE key IN ('cutoff_regular', 'cutoff_grill');
