import os

def replace_in_file(filepath):
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        new_content = content
        replacements = {
            "com.duckduckgo.common.ui.view.text.RevengeTextView": "com.duckduckgo.common.ui.view.text.DaxTextView",
            "com.duckduckgo.common.ui.view.text.RevengeTextInput": "com.duckduckgo.common.ui.view.text.DaxTextInput",
            "com.duckduckgo.common.ui.view.button.RevengeButton": "com.duckduckgo.common.ui.view.button.DaxButton",
            "com.duckduckgo.common.ui.view.button.RevengeButtonPrimary": "com.duckduckgo.common.ui.view.button.DaxButtonPrimary",
            "com.duckduckgo.common.ui.view.button.RevengeButtonSecondary": "com.duckduckgo.common.ui.view.button.DaxButtonSecondary",
            "com.duckduckgo.common.ui.view.button.RevengeButtonGhost": "com.duckduckgo.common.ui.view.button.DaxButtonGhost",
            "com.duckduckgo.common.ui.view.button.RevengeButtonDestructive": "com.duckduckgo.common.ui.view.button.DaxButtonDestructive",
            "com.duckduckgo.common.ui.view.button.RevengeButtonGhostDestructive": "com.duckduckgo.common.ui.view.button.DaxButtonGhostDestructive",
            "com.duckduckgo.common.ui.view.button.RevengeButtonGhostAlt": "com.duckduckgo.common.ui.view.button.DaxButtonGhostAlt",
            "com.duckduckgo.common.ui.view.RevengeSwitch": "com.duckduckgo.common.ui.view.DaxSwitch",
            "com.duckduckgo.common.ui.view.shape.RevengeBubbleCardView": "com.duckduckgo.common.ui.view.shape.DaxBubbleCardView",
            "com.duckduckgo.common.ui.view.shape.RevengeOnboardingBubbleCardView": "com.duckduckgo.common.ui.view.shape.DaxOnboardingBubbleCardView",
            "com.duckduckgo.common.ui.view.listitem.RevengeGridItem": "com.duckduckgo.common.ui.view.listitem.DaxGridItem",
            "com.duckduckgo.common.ui.view.listitem.RevengeExpandableMenuItem": "com.duckduckgo.common.ui.view.listitem.DaxExpandableMenuItem"
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
        if "build" in dirs:
            dirs.remove("build")
        if ".git" in dirs:
            dirs.remove(".git")
            
        for file in files:
            if file.endswith(".xml"):
                replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
