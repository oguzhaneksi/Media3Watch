-- V3: Add device and SDK metadata columns to sessions table
-- All columns are nullable for backward compatibility with older SDK versions
-- that do not send this information.

ALTER TABLE sessions ADD COLUMN IF NOT EXISTS device_model VARCHAR(128);
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS os_version INTEGER;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS sdk_version VARCHAR(32);
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS connection_type VARCHAR(16);

COMMENT ON COLUMN sessions.device_model IS 'Android device model (Build.MODEL)';
COMMENT ON COLUMN sessions.os_version IS 'Android API level (Build.VERSION.SDK_INT)';
COMMENT ON COLUMN sessions.sdk_version IS 'Media3Watch SDK version string';
COMMENT ON COLUMN sessions.connection_type IS 'Network type at session start: Wi-Fi, Cellular, Ethernet, or Unknown';
