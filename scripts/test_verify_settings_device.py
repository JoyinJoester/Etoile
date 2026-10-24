import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("device_verifier", Path(__file__).with_name("verify-settings-device.py"))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


def report(codes=(0, 0, 0), final="INSTRUMENTATION_CODE: -1"):
    names = ["bundledLegalDocumentsHaveContentAndReturnToAbout",
             "settingsChildrenReturnToTheSameSettingsPage",
             "repeatedSettingsBackNavigationKeepsHomeUsable"]
    output = []
    for name, code in zip(names, codes):
        for status in [1, code]:
            output.extend([f"INSTRUMENTATION_STATUS: class={verifier.CLASS}",
                           f"INSTRUMENTATION_STATUS: test={name}",
                           f"INSTRUMENTATION_STATUS_CODE: {status}"])
    return "\n".join(output + ["OK (3 tests)", final])


class InstrumentationResultsTest(unittest.TestCase):
    def test_requires_three_actual_completed_tests(self):
        results, passed = verifier.parse_results(report())
        self.assertTrue(passed)
        self.assertEqual(3, len(results))
        for incomplete in ["OK (0 tests)\nINSTRUMENTATION_CODE: -1", report((0, 0)), ""]:
            self.assertFalse(verifier.parse_results(incomplete)[1])

    def test_rejects_failures_skips_crashes_and_duplicate_cases(self):
        for code in [-1, -2, -3, -4]:
            self.assertFalse(verifier.parse_results(report((0, code, 0)))[1])
        self.assertFalse(verifier.parse_results(report(final="INSTRUMENTATION_CODE: 0"))[1])
        self.assertFalse(verifier.parse_results(report() + "\nINSTRUMENTATION_FAILED: crashed")[1])
        duplicate = report().replace("settingsChildrenReturnToTheSameSettingsPage",
                                     "bundledLegalDocumentsHaveContentAndReturnToAbout")
        self.assertFalse(verifier.parse_results(duplicate)[1])


if __name__ == "__main__":
    unittest.main()
