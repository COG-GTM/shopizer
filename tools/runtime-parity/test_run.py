"""Regression tests for the report's safety gates; no Docker or Maven needed."""
from email.message import EmailMessage
from pathlib import Path
import io
import runpy
import smtplib
import tempfile
import unittest
from unittest.mock import patch

runner = runpy.run_path(str(Path(__file__).with_name("run.py")))


class GateTests(unittest.TestCase):
    def test_only_the_checkout_confirmations_satisfy_the_mail_gate(self):
        expected = dict(subject="Shopizer - Order confirmation: 42", customer="checkout@example.test",
                        merchant="merchant@example.test")
        customer = dict(subject=expected["subject"], recipients=[expected["customer"]])
        merchant = dict(subject=expected["subject"], recipients=[expected["merchant"]])
        registration = dict(subject="Welcome to Shopizer", recipients=[expected["customer"]])
        other_order = dict(subject="Shopizer - Order confirmation: 41", recipients=[expected["merchant"]])
        both_recipients = dict(subject=expected["subject"], recipients=list(merchant["recipients"] + customer["recipients"]))
        required = dict(messages=2, customer=1, merchant=1)
        for messages in ([], [registration, registration], [customer, registration], [customer, other_order],
                         [customer, customer], [both_recipients], [merchant, merchant]):
            with self.subTest(messages=messages):
                self.assertNotEqual(required, runner["checkout_mail_counts"](messages, expected))
        self.assertEqual(required, runner["checkout_mail_counts"]([registration, other_order, customer, merchant], expected))

    def test_smtp_sink_decodes_subject_and_keeps_only_metadata(self):
        message = EmailMessage()
        message["Subject"] = "Café - Order confirmation: 42"
        message["From"] = "sender@example.test"
        message["To"] = "customer@example.test"
        message.set_content("The body is discarded, never retained in parity evidence.")
        with runner["smtp_sink"]() as sink:
            with smtplib.SMTP(*sink.server_address) as smtp:
                smtp.send_message(message)
            self.assertEqual(1, sink.deliveries)
            self.assertEqual([dict(subject=str(message["Subject"]), recipients=["customer@example.test"])], sink.messages)

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
        for scenario in ("counter-reset", "collision", "lost-row", "duplicate", "wrong-first-id",
                         "later-low-id", "later-gap", "later-out-of-order"):
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
                elif scenario == "wrong-first-id":
                    # A later matching minimum must not hide the wrong first allocation.
                    ids["order"] = [351, 350, 352]
                elif scenario in ("later-low-id", "later-gap", "later-out-of-order"):
                    # Existing rows and disjoint IDs alone do not prove correct allocation.
                    ids["order"] = {"later-low-id": [350, 349, 352],
                                    "later-gap": [350, 355, 352],
                                    "later-out-of-order": [350, 352, 351]}[scenario]
                    after["order"]["ids"] = [199, 200] + ids["order"]
                else:
                    ids["product"] = [350, 350, 352]
                with self.assertRaises(AssertionError):
                    runner["verify_continuity"](before, after, ids)


if __name__ == "__main__":
    unittest.main()
