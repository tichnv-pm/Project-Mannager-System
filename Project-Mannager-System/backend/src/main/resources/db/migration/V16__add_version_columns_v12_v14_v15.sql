-- V16__add_version_columns_v12_v14_v15.sql
-- Bo sung cot version (optimistic locking - BaseEntity) cho cac bang tao o V12/V14/V15
-- bi thieu khi entity extends BaseEntity. Khong sua file migration da chay.

ALTER TABLE project_wiki_page_histories
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE sprints
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE test_cases
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE test_runs
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
