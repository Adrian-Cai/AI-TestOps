import importlib.util
import os
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "ai_ci_analyzer.py"
SPEC = importlib.util.spec_from_file_location("ai_ci_analyzer", SCRIPT_PATH)
ai_ci_analyzer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ai_ci_analyzer)


class AiCiAnalyzerTest(unittest.TestCase):
    def test_parse_junit_reports_collects_failed_cases(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            report_dir = Path(temp_dir)
            (report_dir / "TEST-demo.xml").write_text(
                """<?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="2" failures="1" errors="0" skipped="0">
                  <testcase classname="DemoTest" name="passes"/>
                  <testcase classname="DemoTest" name="fails">
                    <failure message="expected true"/>
                  </testcase>
                </testsuite>
                """,
                encoding="utf-8",
            )

            result = ai_ci_analyzer.parse_junit_reports(str(report_dir))

        self.assertEqual(2, result["total"])
        self.assertEqual(1, result["failures"])
        self.assertEqual(0, result["errors"])
        self.assertEqual("DemoTest", result["failed_cases"][0]["class_name"])
        self.assertEqual("fails", result["failed_cases"][0]["test_name"])

    def test_parse_jacoco_xml_calculates_coverages_and_low_classes(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            jacoco_file = Path(temp_dir) / "jacoco.xml"
            jacoco_file.write_text(
                """<?xml version="1.0" encoding="UTF-8"?>
                <report name="demo">
                  <package name="com/example">
                    <class name="CoveredClass">
                      <counter type="LINE" missed="1" covered="9"/>
                    </class>
                    <class name="LowClass">
                      <counter type="LINE" missed="8" covered="2"/>
                    </class>
                  </package>
                  <counter type="LINE" missed="10" covered="30"/>
                  <counter type="BRANCH" missed="2" covered="6"/>
                  <counter type="METHOD" missed="1" covered="4"/>
                </report>
                """,
                encoding="utf-8",
            )

            result = ai_ci_analyzer.parse_jacoco_xml(str(jacoco_file))

        self.assertEqual(0.75, result["line_coverage"])
        self.assertEqual(0.75, result["branch_coverage"])
        self.assertEqual(0.8, result["method_coverage"])
        self.assertEqual(1, len(result["low_coverage_classes"]))
        self.assertEqual("com/example/LowClass", result["low_coverage_classes"][0]["class_name"])

    def test_chat_completions_url_accepts_base_variants(self):
        self.assertEqual(
            "https://api.openai.com/v1/chat/completions",
            ai_ci_analyzer.chat_completions_url("https://api.openai.com"),
        )
        self.assertEqual(
            "https://api.openai.com/v1/chat/completions",
            ai_ci_analyzer.chat_completions_url("https://api.openai.com/v1"),
        )
        self.assertEqual(
            "https://api.openai.com/v1/chat/completions",
            ai_ci_analyzer.chat_completions_url("https://api.openai.com/v1/chat/completions"),
        )

    def test_redact_sensitive_removes_api_keys(self):
        old_value = os.environ.get("AI_API_KEY")
        fake_openai_key = "sk-" + "live-secret-value"
        fake_compatible_key = "ak-" + "another-secret"
        os.environ["AI_API_KEY"] = fake_openai_key
        try:
            result = ai_ci_analyzer.redact_sensitive(
                f"invalid key {fake_openai_key} and {fake_compatible_key}"
            )
        finally:
            if old_value is None:
                os.environ.pop("AI_API_KEY", None)
            else:
                os.environ["AI_API_KEY"] = old_value

        self.assertNotIn(fake_openai_key, result)
        self.assertNotIn(fake_compatible_key, result)
        self.assertIn("[REDACTED]", result)


if __name__ == "__main__":
    unittest.main()
