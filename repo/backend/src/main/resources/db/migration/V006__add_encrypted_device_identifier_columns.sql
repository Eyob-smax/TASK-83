ALTER TABLE checkin_records
    ADD COLUMN device_token_encrypted VARBINARY(512) NULL AFTER device_token_hash;

ALTER TABLE device_bindings
    ADD COLUMN device_token_encrypted VARBINARY(512) NULL AFTER device_token_hash;

CREATE INDEX idx_checkin_device_token_hash ON checkin_records(device_token_hash);
CREATE INDEX idx_device_binding_token_hash ON device_bindings(device_token_hash);
