CREATE TABLE IF NOT EXISTS ai_testops_document (
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
  UNIQUE KEY uk_document_id (document_id),
  KEY idx_document_hash (file_hash),
  KEY idx_document_parse_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_document_chunk (
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
  UNIQUE KEY uk_chunk_id (chunk_id),
  KEY idx_chunk_document (document_id, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_document_parse_result (
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
  UNIQUE KEY uk_parse_result_id (parse_result_id),
  KEY idx_parse_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_prompt_template (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_generation_record (
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
  UNIQUE KEY uk_generation_id (generation_id),
  KEY idx_generation_document (document_id),
  KEY idx_generation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_requirement_extract (
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
  UNIQUE KEY uk_requirement_extract_id (requirement_extract_id),
  KEY idx_extract_document (document_id),
  KEY idx_extract_generation (generation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_validation_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  validation_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  validation_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_detail_json LONGTEXT NULL,
  warning_detail_json LONGTEXT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_validation_id (validation_id),
  KEY idx_validation_generation (generation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_test_case_draft (
  id BIGINT NOT NULL AUTO_INCREMENT,
  draft_case_id VARCHAR(64) NOT NULL,
  case_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  requirement_extract_id VARCHAR(64) NULL,
  title VARCHAR(255) NOT NULL,
  preconditions_json LONGTEXT NULL,
  steps_json LONGTEXT NOT NULL,
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
  UNIQUE KEY uk_draft_case_id (draft_case_id),
  KEY idx_draft_document (document_id),
  KEY idx_draft_generation (generation_id),
  KEY idx_draft_review_status (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_test_case (
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
  priority VARCHAR(16) NOT NULL,
  case_type VARCHAR(64) NULL,
  risk_level VARCHAR(32) NULL,
  requirement_refs_json LONGTEXT NOT NULL,
  risk_tags_json LONGTEXT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_case_id (test_case_id),
  KEY idx_case_document (document_id),
  KEY idx_case_generation (generation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_requirement_case_mapping (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mapping_id VARCHAR(64) NOT NULL,
  requirement_id VARCHAR(64) NOT NULL,
  test_case_id VARCHAR(64) NOT NULL,
  draft_case_id VARCHAR(64) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  document_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mapping_id (mapping_id),
  KEY idx_mapping_requirement (requirement_id),
  KEY idx_mapping_case (test_case_id),
  KEY idx_mapping_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_testops_review_record (
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
  UNIQUE KEY uk_review_record_id (review_record_id),
  KEY idx_review_case (case_id),
  KEY idx_review_draft_case (draft_case_id),
  KEY idx_review_test_case (test_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS title VARCHAR(255) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS file_name VARCHAR(255) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS file_type VARCHAR(32) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS file_path VARCHAR(1000) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS file_size BIGINT NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS duplicate_document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS raw_text LONGTEXT NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS metadata_json LONGTEXT NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS parse_status VARCHAR(32) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS parse_error VARCHAR(1000) NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS uploaded_at DATETIME NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS parsed_at DATETIME NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_document ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE ai_testops_document ADD UNIQUE INDEX IF NOT EXISTS uk_document_id (document_id);
ALTER TABLE ai_testops_document ADD INDEX IF NOT EXISTS idx_document_hash (file_hash);
ALTER TABLE ai_testops_document ADD INDEX IF NOT EXISTS idx_document_parse_status (parse_status);

ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS chunk_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS chunk_index INT NULL;
ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS section_title VARCHAR(255) NULL;
ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS chunk_text LONGTEXT NULL;
ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS token_count INT NULL;
ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS extra_json LONGTEXT NULL;
ALTER TABLE ai_testops_document_chunk ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_document_chunk ADD UNIQUE INDEX IF NOT EXISTS uk_chunk_id (chunk_id);
ALTER TABLE ai_testops_document_chunk ADD INDEX IF NOT EXISTS idx_chunk_document (document_id, chunk_index);

ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS parse_result_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS metadata_json LONGTEXT NULL;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS sections_json LONGTEXT NULL;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS chunks_json LONGTEXT NULL;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS parse_result_json LONGTEXT NULL;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS chunk_count INT NULL DEFAULT 0;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS raw_text_length INT NULL DEFAULT 0;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_document_parse_result ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE ai_testops_document_parse_result ADD UNIQUE INDEX IF NOT EXISTS uk_parse_result_id (parse_result_id);
ALTER TABLE ai_testops_document_parse_result ADD INDEX IF NOT EXISTS idx_parse_document (document_id);

ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS template_code VARCHAR(64) NULL;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS template_name VARCHAR(128) NULL;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS template_type VARCHAR(64) NULL;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS version VARCHAR(32) NULL;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS prompt_content LONGTEXT NULL;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS json_schema LONGTEXT NULL;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS enabled TINYINT NULL DEFAULT 1;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_prompt_template ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE ai_testops_prompt_template DROP INDEX IF EXISTS uk_template_code;
ALTER TABLE ai_testops_prompt_template ADD UNIQUE INDEX IF NOT EXISTS uk_template_code_version (template_code, version);
ALTER TABLE ai_testops_prompt_template ADD INDEX IF NOT EXISTS idx_prompt_enabled (template_code, enabled);

ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS generation_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS requirement_extract_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS prompt_template_code VARCHAR(64) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS prompt_template_version VARCHAR(32) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS model_code VARCHAR(64) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS model_name VARCHAR(128) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS generation_type VARCHAR(64) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS input_snapshot_json LONGTEXT NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS output_json LONGTEXT NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS status VARCHAR(32) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS error_message VARCHAR(1000) NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS token_input INT NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS token_output INT NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS started_at DATETIME NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS finished_at DATETIME NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_generation_record ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE ai_testops_generation_record ADD UNIQUE INDEX IF NOT EXISTS uk_generation_id (generation_id);
ALTER TABLE ai_testops_generation_record ADD INDEX IF NOT EXISTS idx_generation_document (document_id);
ALTER TABLE ai_testops_generation_record ADD INDEX IF NOT EXISTS idx_generation_status (status);

ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS requirement_extract_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS generation_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS requirements_json LONGTEXT NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS business_rules_json LONGTEXT NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS api_list_json LONGTEXT NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS field_constraints_json LONGTEXT NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS exception_cases_json LONGTEXT NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS risks_json LONGTEXT NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS raw_output_json LONGTEXT NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_requirement_extract ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE ai_testops_requirement_extract ADD UNIQUE INDEX IF NOT EXISTS uk_requirement_extract_id (requirement_extract_id);
ALTER TABLE ai_testops_requirement_extract ADD INDEX IF NOT EXISTS idx_extract_document (document_id);
ALTER TABLE ai_testops_requirement_extract ADD INDEX IF NOT EXISTS idx_extract_generation (generation_id);

ALTER TABLE ai_testops_validation_result ADD COLUMN IF NOT EXISTS validation_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_validation_result ADD COLUMN IF NOT EXISTS generation_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_validation_result ADD COLUMN IF NOT EXISTS validation_type VARCHAR(64) NULL;
ALTER TABLE ai_testops_validation_result ADD COLUMN IF NOT EXISTS status VARCHAR(32) NULL;
ALTER TABLE ai_testops_validation_result ADD COLUMN IF NOT EXISTS error_detail_json LONGTEXT NULL;
ALTER TABLE ai_testops_validation_result ADD COLUMN IF NOT EXISTS warning_detail_json LONGTEXT NULL;
ALTER TABLE ai_testops_validation_result ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_validation_result ADD UNIQUE INDEX IF NOT EXISTS uk_validation_id (validation_id);
ALTER TABLE ai_testops_validation_result ADD INDEX IF NOT EXISTS idx_validation_generation (generation_id);

ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS draft_case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS generation_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS requirement_extract_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS title VARCHAR(255) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS preconditions_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS steps_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS case_type VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS risk_level VARCHAR(32) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS requirement_refs_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS risk_tags_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS raw_case_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_test_case_draft ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE ai_testops_test_case_draft ADD UNIQUE INDEX IF NOT EXISTS uk_draft_case_id (draft_case_id);
ALTER TABLE ai_testops_test_case_draft ADD INDEX IF NOT EXISTS idx_draft_document (document_id);
ALTER TABLE ai_testops_test_case_draft ADD INDEX IF NOT EXISTS idx_draft_generation (generation_id);
ALTER TABLE ai_testops_test_case_draft ADD INDEX IF NOT EXISTS idx_draft_review_status (review_status);

ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS test_case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS source_draft_case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS generation_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS requirement_extract_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS title VARCHAR(255) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS preconditions_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS steps_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS case_type VARCHAR(64) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS risk_level VARCHAR(32) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS requirement_refs_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS risk_tags_json LONGTEXT NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS status VARCHAR(32) NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_test_case ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE ai_testops_test_case ADD UNIQUE INDEX IF NOT EXISTS uk_test_case_id (test_case_id);
ALTER TABLE ai_testops_test_case ADD INDEX IF NOT EXISTS idx_case_document (document_id);
ALTER TABLE ai_testops_test_case ADD INDEX IF NOT EXISTS idx_case_generation (generation_id);

ALTER TABLE ai_testops_requirement_case_mapping ADD COLUMN IF NOT EXISTS mapping_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_case_mapping ADD COLUMN IF NOT EXISTS requirement_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_case_mapping ADD COLUMN IF NOT EXISTS test_case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_case_mapping ADD COLUMN IF NOT EXISTS draft_case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_case_mapping ADD COLUMN IF NOT EXISTS generation_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_case_mapping ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_requirement_case_mapping ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_requirement_case_mapping ADD UNIQUE INDEX IF NOT EXISTS uk_mapping_id (mapping_id);
ALTER TABLE ai_testops_requirement_case_mapping ADD INDEX IF NOT EXISTS idx_mapping_requirement (requirement_id);
ALTER TABLE ai_testops_requirement_case_mapping ADD INDEX IF NOT EXISTS idx_mapping_case (test_case_id);
ALTER TABLE ai_testops_requirement_case_mapping ADD INDEX IF NOT EXISTS idx_mapping_document (document_id);

ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS review_record_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS draft_case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS test_case_id VARCHAR(64) NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS action VARCHAR(32) NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS before_json LONGTEXT NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS after_json LONGTEXT NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS reason VARCHAR(1000) NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS reviewer VARCHAR(128) NULL;
ALTER TABLE ai_testops_review_record ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE ai_testops_review_record ADD UNIQUE INDEX IF NOT EXISTS uk_review_record_id (review_record_id);
ALTER TABLE ai_testops_review_record ADD INDEX IF NOT EXISTS idx_review_case (case_id);
ALTER TABLE ai_testops_review_record ADD INDEX IF NOT EXISTS idx_review_draft_case (draft_case_id);
ALTER TABLE ai_testops_review_record ADD INDEX IF NOT EXISTS idx_review_test_case (test_case_id);
