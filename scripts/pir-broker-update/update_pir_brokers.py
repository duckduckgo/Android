#!/usr/bin/env python3
"""
Downloads the latest PIR broker bundle and compares it against the bundled JSONs.
Applies changes (add/update/remove) and writes a summary to --output.
The output file is empty when there are no changes.
"""

import os
import sys
import json
import zipfile
import tempfile
import argparse
import requests
from pathlib import Path


BUNDLE_URL = "https://dbp.duckduckgo.com/dbp/remote/v0?name=all.zip&type=spec"


def parse_version(version_str):
    """Parse a semver-like version string into a zero-padded 3-tuple for reliable comparison."""
    try:
        parts = [int(p) for p in str(version_str).strip().split(".")]
        while len(parts) < 3:
            parts.append(0)
        return tuple(parts[:3])
    except (ValueError, AttributeError):
        return (0, 0, 0)


def download_bundle(auth_token, output_path):
    headers = {"Authorization": f"bearer {auth_token}"}
    response = requests.get(BUNDLE_URL, headers=headers, timeout=60)
    response.raise_for_status()
    with open(output_path, "wb") as f:
        f.write(response.content)


def load_json_files(directory):
    """Return (parsed_dict, failed_set) for all .json files found.

    parsed_dict maps filename -> {"data": ..., "path": ...} for successfully parsed files.
    failed_set contains filenames that were found but could not be parsed.
    """
    result = {}
    failed = set()
    for json_file in Path(directory).rglob("*.json"):
        name = json_file.name
        try:
            with open(json_file, encoding="utf-8") as f:
                data = json.load(f)
            # Use only the filename (not the full path) as key
            if name not in result:
                result[name] = {"data": data, "path": json_file}
        except (json.JSONDecodeError, IOError) as e:
            print(f"WARNING: Could not read {json_file}: {e}", file=sys.stderr)
            failed.add(name)
    return result, failed


def main():
    parser = argparse.ArgumentParser(description="Update PIR broker JSON files from remote bundle")
    parser.add_argument("--brokers-dir", required=True, help="Path to the local brokers assets directory")
    parser.add_argument("--output", required=True, help="Path to write the changes summary (empty = no changes)")
    args = parser.parse_args()

    auth_token = os.environ.get("PIR_API_AUTH_TOKEN")
    if not auth_token:
        print("ERROR: PIR_API_AUTH_TOKEN environment variable not set", file=sys.stderr)
        sys.exit(1)

    brokers_dir = Path(args.brokers_dir)
    if not brokers_dir.is_dir():
        print(f"ERROR: brokers directory not found: {brokers_dir}", file=sys.stderr)
        sys.exit(1)

    with tempfile.TemporaryDirectory() as tmpdir:
        zip_path = os.path.join(tmpdir, "bundle.zip")
        extract_dir = os.path.join(tmpdir, "extracted")
        os.makedirs(extract_dir)

        print(f"Downloading bundle from {BUNDLE_URL}...")
        download_bundle(auth_token, zip_path)

        print("Extracting bundle...")
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(extract_dir)

        downloaded, failed_downloads = load_json_files(extract_dir)
        existing, _ = load_json_files(brokers_dir)

        print(f"Downloaded {len(downloaded)} broker files, found {len(existing)} bundled files.")

        if not downloaded:
            print("ERROR: Remote bundle contained no JSON files. Aborting to prevent deleting all existing brokers.", file=sys.stderr)
            sys.exit(1)

        added = []
        updated = []
        removed = []

        # Detect added and updated
        for filename, info in downloaded.items():
            downloaded_data = info["data"]
            downloaded_version = downloaded_data.get("version", "0")

            if filename not in existing:
                added.append(filename)
                dest = brokers_dir / filename
                with open(info["path"], encoding="utf-8") as f:
                    content = f.read()
                with open(dest, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"  [Added] {filename}")
            else:
                existing_version = existing[filename]["data"].get("version", "0")
                if parse_version(downloaded_version) > parse_version(existing_version):
                    updated.append(f"{filename} ({existing_version} → {downloaded_version})")
                    dest = brokers_dir / filename
                    with open(info["path"], encoding="utf-8") as f:
                        content = f.read()
                    with open(dest, "w", encoding="utf-8") as f:
                        f.write(content)
                    print(f"  [Updated] {filename} ({existing_version} → {downloaded_version})")

        # Detect removed — skip files that failed to parse in the download,
        # since absence from `downloaded` may be due to a parse error, not actual removal.
        for filename in existing:
            if filename not in downloaded and filename not in failed_downloads:
                removed.append(filename)
                existing[filename]["path"].unlink()
                print(f"  [Removed] {filename}")

        # Build summary
        lines = []
        if added:
            lines.append(f"**Added ({len(added)}):**")
            for name in sorted(added):
                lines.append(f"- {name}")
        if updated:
            lines.append(f"**Updated ({len(updated)}):**")
            for info in sorted(updated):
                lines.append(f"- {info}")
        if removed:
            lines.append(f"**Removed ({len(removed)}):**")
            for name in sorted(removed):
                lines.append(f"- {name}")

        with open(args.output, "w", encoding="utf-8") as f:
            if lines:
                f.write("\n".join(lines) + "\n")
            # Empty file signals no changes to the workflow

        if lines:
            print(f"\nChanges detected: {len(added)} added, {len(updated)} updated, {len(removed)} removed.")
        else:
            print("\nNo changes detected.")


if __name__ == "__main__":
    main()
