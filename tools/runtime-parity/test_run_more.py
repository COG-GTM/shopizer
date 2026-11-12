"""Pure-function and mocked HTTP coverage for the runtime parity runner."""
from pathlib import Path
import io
import runpy
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

runner = runpy.run_path(str(Path(__file__).with_name("run.py")))


class RunnerTests(unittest.TestCase):
    def test_coverage_accepts_equal_and_improved_counters(self):
        for improvement in (0, 1):
            with self.subTest(improvement=improvement), tempfile.TemporaryDirectory() as directory:
                xml = Path(directory) / "coverage.xml"
                xml.write_text("<report>" + "".join(
                    f'<counter type="{kind}" covered="{covered + improvement}" '
                    f'missed="{total - covered - improvement}"/>'
                    for kind, (covered, total) in runner["BASELINE"].items()) + "</report>")
                result = runner["coverage"](xml)
                self.assertEqual(set(runner["BASELINE"]), set(result))
                self.assertTrue(all(value["passes"] for value in result.values()))

    def test_coverage_rejects_missing_and_empty_counters(self):
        for content in ('<report/>', '<report><counter type="LINE" covered="0" missed="0"/></report>'):
            with self.subTest(content=content), tempfile.TemporaryDirectory() as directory:
                xml = Path(directory) / "coverage.xml"
                xml.write_text(content)
                with self.assertRaises(AssertionError):
                    runner["coverage"](xml)

    def test_suite_counts_sums_reports_and_defaults_missing_attributes(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ("a.xml", "b.xml")]
            paths[0].write_text('<testsuite tests="8" skipped="2" failures="1" errors="1"/>')
            paths[1].write_text('<testsuite tests="3"/>')
            self.assertEqual(dict(tests=11, skipped=2, failures=1, errors=1), runner["suite_counts"](paths))

    def test_suite_counts_rejects_absent_reports(self):
        with self.assertRaisesRegex(AssertionError, "No Surefire reports"):
            runner["suite_counts"]([])

    def test_continuity_reports_every_segment(self):
        before, after, fixtures = {}, {}, {}
        for index, kind in enumerate(runner["SEGMENTS"], start=3):
            fixtures[kind] = [index * 50, index * 50 + 1]
            before[kind] = dict(ids=[1, 2], counter=index)
            after[kind] = dict(ids=[1, 2] + fixtures[kind], counter=index + 1)
        result = runner["verify_continuity"](before, after, fixtures)
        self.assertEqual(set(runner["SEGMENTS"]), set(result))
        for kind, measurement in result.items():
            self.assertEqual(dict(existing_rows=2, counter_before=before[kind]["counter"],
                                  counter_after=after[kind]["counter"], new_ids=fixtures[kind],
                                  first_expected=fixtures[kind][0], collisions=0), measurement)

    def test_request_decodes_json_and_sends_bearer_body(self):
        with patch("urllib.request.urlopen", return_value=io.BytesIO(b'{"id": 42}')) as opened:
            self.assertEqual({"id": 42}, runner["request"]("http://127.0.0.1:1234", "POST", {"x": 1}, "jwt"))
            request = opened.call_args.args[0]
            self.assertEqual("Bearer jwt", request.get_header("Authorization"))
            self.assertEqual(b'{"x": 1}', request.data)
            self.assertEqual("POST", request.method)

    def test_request_propagates_http_errors(self):
        error = urllib.error.HTTPError("http://127.0.0.1:1234", 403, "Forbidden", {}, None)
        with patch("urllib.request.urlopen", side_effect=error):
            with self.assertRaises(urllib.error.HTTPError) as caught:
                runner["request"]("http://127.0.0.1:1234")
            self.assertIs(error, caught.exception)


if __name__ == "__main__":
    unittest.main()
