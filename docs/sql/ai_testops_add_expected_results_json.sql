ALTER TABLE ai_testops_test_case_draft
    ADD COLUMN expected_results_json LONGTEXT NULL AFTER steps_json;

ALTER TABLE ai_testops_test_case
    ADD COLUMN expected_results_json LONGTEXT NULL AFTER steps_json;
