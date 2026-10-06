"""Release admission checks shared by CI and deterministic policy tests."""

import re


def validate_tag(tag: str, version: str, annotated: bool, branches: list[str]) -> None:
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?", version):
        raise ValueError("Invalid release version")
    if version.endswith("-SNAPSHOT") or tag != f"v{version}":
        raise ValueError("Release tag must match the non-snapshot project version")
    if not annotated:
        raise ValueError("Release tag must be annotated")
    if not any(branch == "origin/main" or re.fullmatch(r"origin/release/\d+\.\d+", branch) for branch in branches):
        raise ValueError("Release commit must belong to an allowed release branch")


def validate_gate(jobs: dict) -> None:
    for name in ("workflow-tests", "build", "metadata"):
        if jobs.get(name, {}).get("result") != "success":
            raise ValueError(f"Required job {name} did not succeed")


def snapshot_eligible(ref: str) -> bool:
    return ref == "refs/heads/main" or bool(re.fullmatch(r"refs/heads/release/\d+\.\d+", ref))
