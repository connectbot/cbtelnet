import json
import os

from release_policy import validate_gate

validate_gate(json.loads(os.environ["NEEDS_JSON"]))
