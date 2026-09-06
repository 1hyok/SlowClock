"""Check declared classes/getters in SDK dexdump XML, never DEX string references."""

import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


REQUIRED_CLASSES = {
    "com.example.slowclock.data.model.Schedule": ("getTitle", "getStartTime"),
    "com.example.slowclock.data.model.User": ("getShareCode", "getFcmToken"),
    "com.example.slowclock.data.model.PublicProfile": (),
    "com.example.slowclock.navigation.MainKey": (),
}


def find_dexdump():
    override = os.environ.get("DEXDUMP")
    if override:
        return override
    on_path = shutil.which("dexdump")
    if on_path:
        return on_path
    for variable in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        sdk = os.environ.get(variable)
        if not sdk:
            continue
        candidates = list((Path(sdk) / "build-tools").glob("*/dexdump"))
        candidates = [path for path in candidates if os.access(path, os.X_OK)]
        if candidates:
            return str(max(candidates, key=lambda path: tuple(
                int(part) for part in re.findall(r"\d+", path.parent.name)
            )))
    raise ValueError("SDK dexdump is required; set ANDROID_SDK_ROOT, ANDROID_HOME, or DEXDUMP.")


def verify(dex_files):
    if not dex_files:
        raise ValueError("At least one DEX file is required.")
    dexdump = find_dexdump()
    definitions = {}
    for dex_file in dex_files:
        result = subprocess.run(
            [dexdump, "-l", "xml", dex_file], capture_output=True, check=True,
        )
        root = ET.fromstring(result.stdout)
        if root.tag != "api":
            raise ValueError("dexdump did not return API XML.")
        for package in root.findall("package"):
            for definition in package.findall("class"):
                name = ".".join(filter(None, (package.get("name"), definition.get("name"))))
                if name in REQUIRED_CLASSES:
                    if name in definitions:
                        raise ValueError(f"Duplicate DEX class definition: {name}")
                    definitions[name] = definition

    for name, getters in REQUIRED_CLASSES.items():
        definition = definitions.get(name)
        if definition is None:
            raise ValueError(f"R8 reflection class definition is missing: {name}")
        for getter in getters:
            matches = [
                method for method in definition.findall("method")
                if method.get("name") == getter
                and method.get("visibility") == "public"
                and method.get("static") == "false"
                and method.get("return") not in (None, "void")
                and not method.findall("parameter")
            ]
            if not matches:
                raise ValueError(f"R8 reflection getter definition is missing: {name}.{getter}()")
    print(f"Verified reflection definitions in {len(dex_files)} DEX file(s).")


if __name__ == "__main__":
    try:
        verify(sys.argv[1:])
    except (OSError, ValueError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"{error}\nCheck app/proguard-rules.pro keep rules and the SDK dexdump tool.", file=sys.stderr)
        sys.exit(1)
