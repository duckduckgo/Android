import os
import argparse
import logging
from os.path import join, dirname
from polyglot_piranha import execute_piranha, PiranhaArguments

# Set up logging
FORMAT = "%(levelname)s %(name)s %(asctime)-15s %(filename)s:%(lineno)d %(message)s"
logging.basicConfig(format=FORMAT)
logging.getLogger().setLevel(logging.DEBUG)
logger = logging.getLogger(__name__)

def get_folders_with_kotlin_files(root_path):
    """Finds all folders containing Kotlin (.kt) files starting from the given root path."""
    folders_with_kt_files = []

    for root, _, files in os.walk(root_path):
        if any(file.endswith('.kt') for file in files):
            folders_with_kt_files.append(root)

    return folders_with_kt_files

def run_ff_cleaner(flags_config, root_path, configuration_path):
    """Runs Piranha feature flag cleanup for Kotlin with the provided flag configurations and root path.
    
    Args:
        flags_config: List of tuples containing (flag_name, treated_value)
        root_path: Path to the root directory
        configuration_path: Path to configuration files
    """
    logger.info("Running the stale feature flag cleanup demo for Kotlin")

    for flag_name, treated in flags_config:
        logger.info(f"Processing flag: {flag_name} with treated value: {treated}")
        # Convert treated to boolean (in case it's passed as a string)
        treated_bool = treated.lower() == "true"
        
        # Compute treated_complement programmatically
        treated_complement = "false" if treated_bool else "true"

        args = PiranhaArguments(
            "kt",
            substitutions={
                "treated": str(treated_bool).lower(),
                "treated_complement": treated_complement,
                "flag_name": flag_name,
            },
            allow_dirty_ast=True,
            paths_to_codebase=get_folders_with_kotlin_files(root_path),
            path_to_configurations=configuration_path,
            cleanup_comments=True,
            delete_consecutive_new_lines=True,
        )

        execute_piranha(args)
        logger.info(f"Piranha execution completed for flag: {flag_name}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run stale feature flag cleanup for Kotlin.")
    parser.add_argument("-r", "--root-path", type=str, 
                       default=os.environ.get('FF_ROOT_PATH'),
                       help="Root path for searching Kotlin files (default: $FF_ROOT_PATH)")
    parser.add_argument("-c", "--configuration-path", type=str,
                       default=os.environ.get('FF_CLEAN_CONFIGURATION_PATH'),
                       help="Path for the configuration files (default: $FF_CLEAN_CONFIGURATION_PATH)")
    parser.add_argument("-f", "--flags", type=str, required=True, nargs="+",
                       help="Space-separated list of flag configurations in format: flagname:treated_value " +
                            "(e.g., 'flag1:true flag2:false')")
    
    args = parser.parse_args()
    
    # Parse flag configurations
    flags_config = []
    for flag_arg in args.flags:
        try:
            flag_name, treated = flag_arg.split(":")
            if treated.lower() not in ["true", "false"]:
                raise ValueError(f"Invalid treated value for flag {flag_name}: {treated}")
            flags_config.append((flag_name, treated))
        except ValueError as e:
            parser.error(f"Invalid flag configuration format: {flag_arg}. Use format 'flagname:treated_value'. {str(e)}")
    
    run_ff_cleaner(flags_config, args.root_path, args.configuration_path)

    print("Completed running the stale feature flag cleanup demo")

