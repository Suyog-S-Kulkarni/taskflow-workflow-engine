CREATE TABLE workflow_definition (
                                     id CHAR(36)
                                            CHARACTER SET ascii
                                         COLLATE ascii_bin
                                                            NOT NULL,

                                     tenant_id CHAR(36)
                                            CHARACTER SET ascii
                                         COLLATE ascii_bin
                                                            NOT NULL,

                                     workflow_id CHAR(36)
                                            CHARACTER SET ascii
                                         COLLATE ascii_bin
                                                            NOT NULL,

                                     definition_version INT NOT NULL,

                                     name VARCHAR(100) NOT NULL,

                                     tasks_json JSON NOT NULL,

                                     created_at TIMESTAMP(6)
                                                            NOT NULL
                                         DEFAULT CURRENT_TIMESTAMP(6),

                                     CONSTRAINT pk_workflow_definition
                                         PRIMARY KEY (id),

                                     CONSTRAINT uk_workflow_definition_version
                                         UNIQUE (
                                                 tenant_id,
                                                 workflow_id,
                                                 definition_version
                                             ),

                                     CONSTRAINT chk_workflow_definition_version
                                         CHECK (definition_version >= 1)
) ENGINE = InnoDB;