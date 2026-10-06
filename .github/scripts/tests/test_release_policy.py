import unittest

from release_policy import snapshot_eligible, validate_gate, validate_tag


class ReleasePolicyTest(unittest.TestCase):
    def test_snapshots_only_use_allowed_branches(self):
        for ref in ("refs/heads/main", "refs/heads/release/0.1", "refs/heads/release/10.20"):
            self.assertTrue(snapshot_eligible(ref))
        for ref in ("refs/heads/feature", "refs/heads/release/work", "refs/tags/v0.1.0", "refs/heads/release/0.1/extra"):
            self.assertFalse(snapshot_eligible(ref))

    def test_valid_release_and_maintenance_tags(self):
        for branch in ("origin/main", "origin/release/0.1"):
            validate_tag("v0.1.0", "0.1.0", True, [branch])

    def test_invalid_tags_and_untrusted_branches(self):
        cases = [
            ("v0.1.1", "0.1.0", True, ["origin/main"]),
            ("v0.1.0-SNAPSHOT", "0.1.0-SNAPSHOT", True, ["origin/main"]),
            ("v0.1.0", "0.1.0", False, ["origin/main"]),
            ("v0.1.0", "0.1.0", True, ["origin/feature"]),
            ("v0.1.0", "0.1.0", True, ["origin/release/work"]),
            ("v0.1.0", "0.1.0", True, []),
        ]
        for case in cases:
            with self.subTest(case=case), self.assertRaises(ValueError):
                validate_tag(*case)

    def test_entire_matrix_and_workflows_must_succeed(self):
        jobs = {key: {"result": "success"} for key in ("workflow-tests", "build", "metadata")}
        validate_gate(jobs)
        for key in jobs:
            for result in ("failure", "cancelled", "skipped", None):
                with self.subTest(key=key, result=result), self.assertRaises(ValueError):
                    validate_gate({**jobs, key: {"result": result}})
            with self.assertRaises(ValueError):
                validate_gate({k: v for k, v in jobs.items() if k != key})


if __name__ == "__main__":
    unittest.main()
