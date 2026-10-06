"""Inspect a checked-out tag without executing issue or tag content as shell code."""

import os
import subprocess
from pathlib import Path

from release_policy import snapshot_eligible, validate_tag


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def main() -> None:
    lines = Path("gradle.properties").read_text().splitlines()
    versions = [line.removeprefix("version=") for line in lines if line.startswith("version=")]
    if len(versions) != 1:
        raise ValueError("Exactly one project version must be configured")
    version = versions[0]
    ref = os.environ.get("GITHUB_REF", "")
    if ref.startswith("refs/tags/"):
        tag = ref.removeprefix("refs/tags/")
        git("fetch", "origin", "+refs/heads/main:refs/remotes/origin/main", "+refs/heads/release/*:refs/remotes/origin/release/*")
        commit = git("rev-parse", f"refs/tags/{tag}^{{commit}}")
        branches = git("for-each-ref", f"--contains={commit}", "--format=%(refname:short)", "refs/remotes/origin/").splitlines()
        validate_tag(tag, version, git("cat-file", "-t", f"refs/tags/{tag}") == "tag", branches)
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        # Project versions are restricted to one safe line before writing Actions output.
        if not version or any(c not in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.-" for c in version):
            raise ValueError("Invalid project version")
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"version={version}\n")
            stream.write(f"snapshot_eligible={str(snapshot_eligible(ref)).lower()}\n")


if __name__ == "__main__":
    main()
