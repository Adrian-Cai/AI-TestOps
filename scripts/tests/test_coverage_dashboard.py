import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "coverage_dashboard.py"
SPEC = importlib.util.spec_from_file_location("coverage_dashboard", SCRIPT_PATH)
coverage_dashboard = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = coverage_dashboard
SPEC.loader.exec_module(coverage_dashboard)


class CoverageDashboardTest(unittest.TestCase):
    def test_parse_jacoco_xml_classifies_must_cover_and_exempt_classes(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            jacoco_file = Path(temp_dir) / "jacoco.xml"
            jacoco_file.write_text(
                """<?xml version="1.0" encoding="UTF-8"?>
                <report name="demo">
                  <package name="com/example/aitestops/diff/gate">
                    <class name="com/example/aitestops/diff/gate/DiffMergeGateCalculator">
                      <counter type="LINE" missed="6" covered="4"/>
                      <counter type="BRANCH" missed="2" covered="2"/>
                      <counter type="METHOD" missed="0" covered="1"/>
                    </class>
                  </package>
                  <package name="com/example/aitestops/testcase/entity">
                    <class name="com/example/aitestops/testcase/entity/AiTestopsTestCase">
                      <counter type="LINE" missed="1" covered="0"/>
                    </class>
                  </package>
                  <package name="com/example/aitestops/testcase/service">
                    <class name="com/example/aitestops/testcase/service/AiTestopsTestCaseService"/>
                  </package>
                  <counter type="LINE" missed="7" covered="4"/>
                  <counter type="BRANCH" missed="2" covered="2"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                encoding="utf-8",
            )

            report = coverage_dashboard.parse_jacoco_xml(str(jacoco_file), "demo")

        by_name = {item.name: item for item in report.classes}
        self.assertEqual(0.3636, report.line.ratio)
        self.assertIn("com/example/aitestops/diff/gate/DiffMergeGateCalculator", by_name)
        self.assertNotIn("com/example/aitestops/diff/gate/com/example/aitestops/diff/gate/DiffMergeGateCalculator", by_name)
        self.assertEqual("must", by_name["com/example/aitestops/diff/gate/DiffMergeGateCalculator"].priority)
        self.assertEqual("exempt", by_name["com/example/aitestops/testcase/entity/AiTestopsTestCase"].priority)
        self.assertEqual("exempt", by_name["com/example/aitestops/testcase/service/AiTestopsTestCaseService"].priority)
        self.assertIn("没有可执行行", by_name["com/example/aitestops/testcase/service/AiTestopsTestCaseService"].reason)

    def test_generate_dashboard_writes_priority_sections(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            jacoco_file = Path(temp_dir) / "jacoco.xml"
            output_dir = Path(temp_dir) / "dashboard"
            jacoco_file.write_text(
                """<?xml version="1.0" encoding="UTF-8"?>
                <report name="demo">
                  <package name="com/example/aitestops/diff/coverage">
                    <class name="DiffCoverageMatcher">
                      <counter type="LINE" missed="8" covered="2"/>
                      <counter type="BRANCH" missed="4" covered="0"/>
                      <counter type="METHOD" missed="0" covered="1"/>
                    </class>
                  </package>
                  <counter type="LINE" missed="8" covered="2"/>
                  <counter type="BRANCH" missed="4" covered="0"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                encoding="utf-8",
            )
            report = coverage_dashboard.parse_jacoco_xml(str(jacoco_file), "demo")

            html_path = coverage_dashboard.generate_dashboard(report, str(output_dir), 0.5, 0.3)

            content = html_path.read_text(encoding="utf-8")
            css_path = output_dir / "dashboard.css"
            css_exists = css_path.exists()
        self.assertIn("代码覆盖率看板", content)
        self.assertIn("必须优先补测", content)
        self.assertIn("DiffCoverageMatcher", content)
        self.assertIn('href="dashboard.css"', content)
        self.assertNotIn("<style>", content)
        self.assertTrue(css_exists)
        self.assertIn("覆盖匹配会影响补测判断", content)

    def test_missing_dashboard_explains_absent_jacoco_xml(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir) / "dashboard"
            html_path = coverage_dashboard.write_missing_dashboard(
                "demo",
                "target/site/jacoco/jacoco.xml",
                str(output_dir),
            )

            content = html_path.read_text(encoding="utf-8")
            css_path = output_dir / "dashboard.css"
            css_exists = css_path.exists()

        self.assertIn("覆盖率看板未生成", content)
        self.assertIn("target/site/jacoco/jacoco.xml", content)
        self.assertIn('href="dashboard.css"', content)
        self.assertNotIn("<style>", content)
        self.assertTrue(css_exists)


if __name__ == "__main__":
    unittest.main()
