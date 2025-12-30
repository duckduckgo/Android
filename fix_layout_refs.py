import os

replacements = {
    # Layout Filenames (renamed from dax to revenge)
    "layout/include_onboarding_in_context_dax_dialog": "layout/include_onboarding_in_context_revenge_dialog",
    "layout/include_onboarding_bubble_dax_dialog": "layout/include_onboarding_bubble_revenge_dialog",
    
    # Generic replacements for dax_dialog to revenge_dialog if they appear in XML
    "@layout/include_onboarding_in_context_dax_dialog": "@layout/include_onboarding_in_context_revenge_dialog",
    "@layout/include_onboarding_bubble_dax_dialog": "@layout/include_onboarding_bubble_revenge_dialog",
}

def apply_replacements(directory):
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(".kt") or file.endswith(".java") or file.endswith(".xml"):
                path = os.path.join(root, file)
                with open(path, 'r') as f:
                    content = f.read()
                
                new_content = content
                for old, new in replacements.items():
                    new_content = new_content.replace(old, new)
                
                if new_content != content:
                    print(f"Updating {path}")
                    with open(path, 'w') as f:
                        f.write(new_content)

apply_replacements("app/src/main")
