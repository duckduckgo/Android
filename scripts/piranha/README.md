# Kotlin Feature Flag Cleanup CLI

A command-line tool for cleaning up stale feature flags in Kotlin projects.

## Installation

Ensure you have **Python 3.12** installed. Clone the repository and install dependencies:

```sh
git clone https://github.com/duckduckgo/Android.git
cd <path/to>/Android/scripts/piranha
pip install -r requirements.txt
```

## Requirements

- Python 3.12
- Dependencies listed in `requirements.txt`

### Step 1: Install Python 3.12 for your platform
Windows:
- Download Python 3.12 (https://www.python.org/downloads/release/python-3120/) and install it

Linux:
```sh
sudo apt update
sudo apt install python3.12 python3.12-venv
```

MacOS:
```sh
brew install python@3.12
```

### Step 2:  Create a Virtual Environment with Python 3.12
Windows:
```sh
py -3.12 -m venv myenv
```

MacOS/Linux:
```sh
python3.12 -m venv myenv
```

### Step 3: Activate the Virtual Environment
Windows:
```sh
myenv\Scripts\activate
```

MacOS/Linux
```sh
source myenv/bin/activate
```

### Step 4: Verify Python Version Inside Virtual Environment
```sh
python --version
```

## Usage

```sh
python clean.py [-h] -r ROOT_PATH -c CONFIGURATION_PATH -f FLAGS [FLAGS ...]
```

## Options

- `-h, --help`  
  Show this help message and exit.

- `-r ROOT_PATH, --root-path ROOT_PATH`  
  Root path for searching Kotlin files.

- `-c CONFIGURATION_PATH, --configuration-path CONFIGURATION_PATH`  
  Path for the configuration files.

- `-f FLAGS [FLAGS ...], --flags FLAGS [FLAGS ...]`  
  Space-separated list of flag configurations in the format:  
  `flagname:treated_value` (e.g., `'flag1:true flag2:false'`).

## Explanation

- **`configuration-path`**: Path to the `rules.toml` file containing cleanup rules.
- **`root-path`**: The root path of your Android project.

## Example Execution

Assuming you're in the Android project folder, you can run:

```sh
python scripts/piranha/clean.py -r . -c scripts/piranha/configurations/ -f screenLock:false optimizeTrackerEvaluationV2:true
```

This command runs the cleanup tool in the current directory (`.`), using the configuration files from `scripts/piranha/configurations/` and applying the flag transformations provided.

## Notes

- The script modifies Kotlin files based on the given flag configurations.
- Use carefully, as it may remove or alter code.

