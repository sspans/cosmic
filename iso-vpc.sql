INSERT INTO `vpc` 
(
  `uuid`,
  `name`,
  `display_text`,
  `cidr`,
  `vpc_offering_id`,
  `zone_id`,
  `state`,
  `domain_id`,
  `account_id`,
  `network_domain`,
  `removed`,
  `created`,
  `restart_required`,
  `display`,
  `uses_distributed_router`,
  `region_level_vpc`,
  `redundant`,
  `source_nat_list`,
  `syslog_server_list`
)
VALUES
(
  UUID(), -- uuid
  (SELECT `name` FROM `networks` WHERE `id` = 205), -- name
  (SELECT `name` FROM `networks` WHERE `id` = 205), -- display_text
  (SELECT `cidr` FROM `networks` WHERE `id` = 205), -- cidr
  1, -- vpc_offering_id
  1, -- zone_id
  'Enabled', -- state
  (SELECT `domain_id` FROM `networks` WHERE `id` = 205), -- domain_id
  (SELECT `account_id` FROM `networks` WHERE `id` = 205), -- account_id
  (SELECT `network_domain` FROM `networks` WHERE `id` = 205), -- network_domain
  NULL, -- removed
  (SELECT `created` FROM `networks` WHERE `id` = 205), -- created
  0, -- restart_required
  1, -- display
  0, -- uses_distributed_router
  0, -- region_level_vpc
  (SELECT `redundant` FROM `networks` WHERE `id` = 205), -- redundant
  NULL, -- source_nat_list
  NULL -- syslog_server_list
);

INSERT INTO `vpc_service_map`
(
  `vpc_id`,
  `service`,
  `provider`,
  `created`
)
SELECT
(
  (SELECT `id` FROM `vpc` WHERE `uuid` = 'xxx'), -- vpc_id
  `service`,
  `provider`,
  `created`
)
FROM `vpc_offering_service_map`  
WHERE `vpc_offering_id` = (SELECT `vpc_offering_id` FROM `vpc` WHERE `uuid` = 'xxx');

INSERT INTO `network_acl`
(
  `name`,
  `uuid`,
  `vpc_id`,
  `description`,
  `display`
)
VALUES
(
  CONCAT((SELECT `name` FROM `networks` WHERE `id` = 205), '_acl'), -- name
  UUID(), -- uuid
  (SELECT `id` FROM `vpc` WHERE `uuid` = 'xxx'), -- vpc_id
  CONCAT((SELECT `name` FROM `networks` WHERE `id` = 205), '_acl'), -- description
  1 -- display
);

-- TODO
INSERT INTO `network_acl_item`
(

);

UPDATE `networks`
SET
  `vpc_id` = (SELECT `id` FROM `vpc` WHERE `uuid` = 'xxx'),
  `acl_id` = (SELECT `id` FROM `network_acl` WHERE `uuid` = 'xxx'),
  `network_offering_id` = 14
WHERE `id` = 205;


-- TODO
UPDATE `domain_router`
SET
  `vpc_id` = (SELECT `id` FROM `vpc` WHERE `uuid` = 'xxx'),
  `acl_id` = (SELECT `id` FROM `network_acl` WHERE `uuid` = 'xxx'),
  `network_offering_id` = 14
WHERE `id` = 205


