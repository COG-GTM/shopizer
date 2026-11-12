"""Regression tests for the report's two safety gates; no Docker or Maven needed."""
from pathlib import Path
import io
import runpy
import tempfile
import unittest
from unittest.mock import patch

runner = runpy.run_path(str(Path(__file__).with_name("run.py")))


class GateTests(unittest.TestCase):
    def test_success_with_no_response_body_is_allowed(self):
        # Payment configuration returns HTTP 200 with an empty body.
        with patch("urllib.request.urlopen", return_value=io.BytesIO(b"")):
            self.assertIsNone(runner["request"]("http://127.0.0.1:1234/api/v1/private/modules/payment", "POST", {}))

    def test_coverage_does_not_accept_a_regression_hidden_by_rounding(self):
        nodes = []
        for kind, (covered, total) in runner["BASELINE"].items():
            # Same four-decimal display percentage, but strictly worse coverage.
            nodes.append(f'<counter type="{kind}" covered="{covered * 1000 - 1}" '
                         f'missed="{(total - covered) * 1000 + 1}"/>')
        with tempfile.TemporaryDirectory() as directory:
            xml = Path(directory) / "coverage.xml"
            xml.write_text("<report>" + "".join(nodes) + "</report>")
            self.assertTrue(all(not value["passes"] for value in runner["coverage"](xml).values()))

    def test_existing_counter_continues_and_keeps_old_rows(self):
        before = {kind: {"ids": [199, 200], "counter": 7} for kind in runner["SEGMENTS"]}
        after = {kind: {"ids": [199, 200, 350, 351, 352], "counter": 8} for kind in before}
        ids = {kind: [350, 351, 352] for kind in before}
        result = runner["verify_continuity"](before, after, ids)
        self.assertEqual(350, result["product"]["first_expected"])

    def test_counter_reinterpretation_collision_and_lost_rows_fail(self):
        for scenario in ("counter-reset", "collision", "lost-row", "duplicate"):
            with self.subTest(scenario=scenario):
                before = {kind: {"ids": [199, 200], "counter": 7} for kind in runner["SEGMENTS"]}
                after = {kind: {"ids": [199, 200, 350, 351, 352], "counter": 8} for kind in before}
                ids = {kind: [350, 351, 352] for kind in before}
                if scenario == "counter-reset":
                    ids["order"] = [7, 8, 9]
                elif scenario == "collision":
                    ids["customer"] = [350, 199, 352]
                elif scenario == "lost-row":
                    after["product"]["ids"].remove(199)
                else:
                    ids["product"] = [350, 350, 352]
                with self.assertRaises(AssertionError):
                    runner["verify_continuity"](before, after, ids)


if __name__ == "__main__":
    unittest.main()
