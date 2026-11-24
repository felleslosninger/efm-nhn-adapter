-- The MSH configuration of each HER-id the adapter has registered in MSH
CREATE TABLE msh_configuration
(
    her_id              INT          NOT NULL,
    -- The organization that owns the HER-id, whose HelseID tenant the configuration is registered with
    organization_number VARCHAR(9)   NOT NULL,
    -- Only the adapter's HelseID client may act for the HER-id in MSH
    client_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL,
    -- The last time the adapter sent or received a message for the HER-id
    last_used           TIMESTAMP(6) NULL,
    CONSTRAINT pk_msh_configuration PRIMARY KEY (her_id)
);
