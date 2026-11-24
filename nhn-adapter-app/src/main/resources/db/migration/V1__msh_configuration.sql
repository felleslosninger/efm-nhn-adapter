-- The MSH configuration of each HER-id the adapter has registered in MSH. The times are in UTC.
CREATE TABLE msh_configuration
(
    her_id              INT          NOT NULL PRIMARY KEY,
    -- The organization that owns the HER-id, whose HelseID tenant the configuration is registered with
    organization_number VARCHAR(9)   NOT NULL,
    -- Whether the HelseID token is multi-tenant, as for a client acting through a supplier
    multi_tenant        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Only the adapter's HelseID client may act for the HER-id in MSH
    client_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    -- The last time the adapter sent or received a message for the HER-id
    last_used           DATETIME(6)  NULL
);
