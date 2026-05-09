DROP TABLE IF EXISTS ai_testops_document_parse_result;
DROP TABLE IF EXISTS ai_testops_document_chunk;
DROP TABLE IF EXISTS ai_testops_document;

CREATE TABLE ai_testops_document (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  file_name VARCHAR(255) NULL,
  file_type VARCHAR(32) NULL,
  file_path VARCHAR(1000) NULL,
  file_size BIGINT NULL,
  file_hash VARCHAR(64) NULL,
  duplicate_document_id VARCHAR(64) NULL,
  raw_text LONGTEXT NULL,
  metadata_json LONGTEXT NULL,
  parse_status VARCHAR(32) NOT NULL,
  parse_error VARCHAR(1000) NULL,
  uploaded_at DATETIME NULL,
  parsed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_id (document_id)
);

CREATE TABLE ai_testops_document_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT,
  chunk_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  chunk_index INT NOT NULL,
  section_title VARCHAR(255) NULL,
  chunk_text LONGTEXT NOT NULL,
  token_count INT NULL,
  extra_json LONGTEXT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chunk_id (chunk_id)
);

CREATE TABLE ai_testops_document_parse_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parse_result_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  metadata_json LONGTEXT NULL,
  sections_json LONGTEXT NULL,
  chunks_json LONGTEXT NULL,
  parse_result_json LONGTEXT NOT NULL,
  chunk_count INT NOT NULL DEFAULT 0,
  raw_text_length INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_parse_result_id (parse_result_id)
);

DROP TABLE IF EXISTS ai_testops_test_case_draft;
DROP TABLE IF EXISTS ai_testops_requirement_case_mapping;
DROP TABLE IF EXISTS ai_testops_review_record;
DROP TABLE IF EXISTS ai_testops_test_case;
DROP TABLE IF EXISTS ai_testops_validation_result;
DROP TABLE IF EXISTS ai_testops_requirement_extract;
DROP TABLE IF EXISTS ai_testops_generation_record;
DROP TABLE IF EXISTS ai_testops_prompt_template;

CREATE TABLE ai_testops_prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  template_type VARCHAR(64) NOT NULL,
  version VARCHAR(32) NOT NULL,
  prompt_content LONGTEXT NOT NULL,
  json_schema LONGTEXT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_code_version (template_code, version),
  KEY idx_prompt_enabled (template_code, enabled)
);

CREATE TABLE ai_testops_generation_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  generation_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  requirement_extract_id VARCHAR(64) NULL,
  prompt_template_code VARCHAR(64) NOT NULL,
  prompt_template_version VARCHAR(32) NULL,
  model_code VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) NULL,
  generation_type VARCHAR(64) NOT NULL,
  input_snapshot_json LONGTEXT NOT NULL,
  output_json LONGTEXT NULL,
  status VARCHAR(32) NOT NULL,
  error_message VARCHAR(1000) NULL,
  token_input INT NULL,
  token_output INT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_generation_id (generation_id)
);

CREATE TABLE ai_testops_requirement_extract (
  id BIGINT NOT NULL AUTO_INCREMENT,
  requirement_extract_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  requirements_json LONGTEXT NOT NULL,
  business_rules_json LONGTEXT NOT NULL,
  api_list_json LONGTEXT NOT NULL,
  field_constraints_json LONGTEXT NOT NULL,
  exception_cases_json LONGTEXT NOT NULL,
  risks_json LONGTEXT NOT NULL,
  raw_output_json LONGTEXT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_requirement_extract_id (requirement_extract_id)
);

CREATE TABLE ai_testops_validation_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  validation_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  validation_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_detail_json LONGTEXT NULL,
  warning_detail_json LONGTEXT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_validation_id (validation_id)
);

CREATE TABLE ai_testops_test_case_draft (
  id BIGINT NOT NULL AUTO_INCREMENT,
  draft_case_id VARCHAR(64) NOT NULL,
  case_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  requirement_extract_id VARCHAR(64) NULL,
  title VARCHAR(255) NOT NULL,
  preconditions_json LONGTEXT NULL,
  steps_json LONGTEXT NOT NULL,
  expected_results_json LONGTEXT NULL,
  priority VARCHAR(16) NOT NULL,
  case_type VARCHAR(64) NULL,
  risk_level VARCHAR(32) NULL,
  requirement_refs_json LONGTEXT NOT NULL,
  risk_tags_json LONGTEXT NULL,
  review_status VARCHAR(32) NOT NULL,
  raw_case_json LONGTEXT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_draft_case_id (draft_case_id)
);

CREATE TABLE ai_testops_test_case (
  id BIGINT NOT NULL AUTO_INCREMENT,
  test_case_id VARCHAR(64) NOT NULL,
  source_draft_case_id VARCHAR(64) NOT NULL,
  case_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  requirement_extract_id VARCHAR(64) NULL,
  title VARCHAR(255) NOT NULL,
  preconditions_json LONGTEXT NULL,
  steps_json LONGTEXT NOT NULL,
  expected_results_json LONGTEXT NULL,
  priority VARCHAR(16) NOT NULL,
  case_type VARCHAR(64) NULL,
  risk_level VARCHAR(32) NULL,
  requirement_refs_json LONGTEXT NOT NULL,
  risk_tags_json LONGTEXT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_case_id (test_case_id)
);

CREATE TABLE ai_testops_requirement_case_mapping (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mapping_id VARCHAR(64) NOT NULL,
  requirement_id VARCHAR(64) NOT NULL,
  test_case_id VARCHAR(64) NOT NULL,
  draft_case_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mapping_id (mapping_id)
);

CREATE TABLE ai_testops_review_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  review_record_id VARCHAR(64) NOT NULL,
  case_id VARCHAR(64) NOT NULL,
  draft_case_id VARCHAR(64) NULL,
  test_case_id VARCHAR(64) NULL,
  action VARCHAR(32) NOT NULL,
  before_json LONGTEXT NULL,
  after_json LONGTEXT NULL,
  reason VARCHAR(1000) NULL,
  reviewer VARCHAR(128) NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_record_id (review_record_id)
);

DROP TABLE IF EXISTS ai_testops_diff_risk_action_record;
DROP TABLE IF EXISTS ai_testops_diff_risk_case_rel;
DROP TABLE IF EXISTS ai_testops_diff_merge_gate_report;
DROP TABLE IF EXISTS ai_testops_diff_risk_item;
DROP TABLE IF EXISTS ai_testops_diff_changed_file;
DROP TABLE IF EXISTS ai_testops_diff_analysis_task;

CREATE TABLE ai_testops_diff_analysis_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NULL,
  requirement_extract_id VARCHAR(64) NULL,
  repo_url VARCHAR(500) NOT NULL,
  repo_name VARCHAR(255) NULL,
  source_branch VARCHAR(255) NOT NULL,
  target_branch VARCHAR(255) NOT NULL DEFAULT 'master',
  base_commit VARCHAR(128) NULL,
  head_commit VARCHAR(128) NULL,
  analysis_options LONGTEXT NULL,
  status VARCHAR(32) NOT NULL,
  fail_reason LONGTEXT NULL,
  changed_file_count INT NOT NULL DEFAULT 0,
  changed_method_count INT NOT NULL DEFAULT 0,
  high_risk_count INT NOT NULL DEFAULT 0,
  medium_risk_count INT NOT NULL DEFAULT 0,
  low_risk_count INT NOT NULL DEFAULT 0,
  not_covered_risk_count INT NOT NULL DEFAULT 0,
  merge_gate_status VARCHAR(32) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  updated_by VARCHAR(64) NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_diff_task_code (task_code)
);

CREATE TABLE ai_testops_diff_changed_file (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  old_file_path VARCHAR(1000) NULL,
  new_file_path VARCHAR(1000) NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  language VARCHAR(64) NULL,
  file_role VARCHAR(64) NULL,
  additions INT NOT NULL DEFAULT 0,
  deletions INT NOT NULL DEFAULT 0,
  changes INT NOT NULL DEFAULT 0,
  patch LONGTEXT NULL,
  patch_summary LONGTEXT NULL,
  is_test_file TINYINT NOT NULL DEFAULT 0,
  is_core_file TINYINT NOT NULL DEFAULT 0,
  initial_risk_level VARCHAR(32) NULL,
  initial_risk_reason LONGTEXT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE ai_testops_diff_risk_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  document_id VARCHAR(64) NULL,
  requirement_extract_id VARCHAR(64) NULL,
  risk_code VARCHAR(64) NOT NULL,
  risk_title VARCHAR(255) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  risk_category VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_rule_code VARCHAR(64) NULL,
  affected_module VARCHAR(255) NULL,
  affected_scenarios LONGTEXT NULL,
  risk_reason LONGTEXT NULL,
  test_suggestion LONGTEXT NULL,
  missing_test_scenarios LONGTEXT NULL,
  ai_confidence DECIMAL(5,2) NULL,
  coverage_status VARCHAR(32) NOT NULL,
  coverage_reason LONGTEXT NULL,
  process_status VARCHAR(32) NOT NULL,
  merge_gate_impact VARCHAR(32) NOT NULL,
  ignore_reason VARCHAR(1000) NULL,
  owner VARCHAR(64) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  updated_by VARCHAR(64) NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_diff_task_risk_code (task_id, risk_code)
);

CREATE TABLE ai_testops_diff_risk_case_rel (
  id BIGINT NOT NULL AUTO_INCREMENT,
  risk_id BIGINT NOT NULL,
  case_id BIGINT NOT NULL,
  document_id VARCHAR(64) NULL,
  requirement_extract_id VARCHAR(64) NULL,
  case_source_type VARCHAR(64) NULL,
  relation_type VARCHAR(64) NOT NULL,
  coverage_judgement VARCHAR(32) NOT NULL,
  judgement_reason LONGTEXT NULL,
  similarity_score DECIMAL(6,4) NULL,
  generated_from_ai TINYINT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_diff_risk_case (risk_id, case_id)
);

CREATE TABLE ai_testops_diff_risk_action_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  risk_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  before_status VARCHAR(32) NULL,
  after_status VARCHAR(32) NULL,
  action_desc LONGTEXT NULL,
  action_payload LONGTEXT NULL,
  operator VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE ai_testops_diff_merge_gate_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  report_code VARCHAR(64) NOT NULL,
  gate_status VARCHAR(32) NOT NULL,
  gate_reason LONGTEXT NULL,
  changed_file_count INT NOT NULL DEFAULT 0,
  changed_method_count INT NOT NULL DEFAULT 0,
  high_risk_count INT NOT NULL DEFAULT 0,
  medium_risk_count INT NOT NULL DEFAULT 0,
  low_risk_count INT NOT NULL DEFAULT 0,
  covered_risk_count INT NOT NULL DEFAULT 0,
  partial_covered_risk_count INT NOT NULL DEFAULT 0,
  not_covered_risk_count INT NOT NULL DEFAULT 0,
  need_confirm_risk_count INT NOT NULL DEFAULT 0,
  blocked_risk_count INT NOT NULL DEFAULT 0,
  suggested_case_count INT NOT NULL DEFAULT 0,
  suggested_regression_modules LONGTEXT NULL,
  report_summary LONGTEXT NULL,
  report_detail LONGTEXT NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_diff_report_code (report_code),
  UNIQUE KEY uk_diff_report_task (task_id)
);
