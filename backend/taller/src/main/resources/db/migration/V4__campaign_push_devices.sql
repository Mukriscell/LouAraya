CREATE TABLE push_devices (
    owner_id bigint NOT NULL,
    installation_id varchar(80) NOT NULL,
    endpoint varchar(300) NOT NULL UNIQUE,
    public_key varchar(90) NOT NULL,
    auth_secret varchar(25) NOT NULL,
    preferred_category varchar(30),
    PRIMARY KEY (owner_id, installation_id)
);

INSERT INTO write_guards (operation) VALUES ('push_devices');
