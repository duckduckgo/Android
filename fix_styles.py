import os

def replace_in_file(filepath):
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        new_content = content
        replacements = {
            "Widget.DuckDuckGo": "Widget.Revenge",
            "ShapeAppearance.DuckDuckGo": "ShapeAppearance.Revenge",
            "Theme.DuckDuckGo": "Theme.Revenge",
            "Typography.DuckDuckGo": "Typography.Revenge"
        }
        
        for old, new in replacements.items():
            new_content = new_content.replace(old, new)
            
        if new_content != content:
            with open(filepath, 'w') as f:
                f.write(new_content)
            print(f"Updated: {filepath}")
            
    except Exception as e:
        print(f"Error processing {filepath}: {e}")

def main():
    for root, dirs, files in os.walk("."):
        # Skip build directories to avoid wasting time on generated files
        if "build" in dirs:
            dirs.remove("build")
        if ".git" in dirs:
            dirs.remove(".git")
            
        for file in files:
            if file.endswith(".xml"):
                replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
