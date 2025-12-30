import os

replacements = {
    # Layout IDs in Bindings
    "includeOnboardingDaxDialogBubble": "includeOnboardingRevengeDialogBubble",
    "includeOnboardingInContextDaxDialog": "includeOnboardingInContextRevengeDialog",
    "includeDuckChatSetting": "includeRevengeChatSetting",
    "includeDuckChatMenuItem": "includeRevengeChatMenuItem",
    "duckChatMenuItem": "revengeChatMenuItem",
    "duckNewChatMenuItem": "revengeNewChatMenuItem",

    # String Resources
    "onboardingSerpDaxDialogDescription": "onboardingSerpRevengeDialogDescription",
    "onboardingSerpDaxDialogButton": "onboardingSerpRevengeDialogButton",
    "onboardingTrackersBlockedDaxDialogButton": "onboardingTrackersBlockedRevengeDialogButton",
    "onboardingFireButtonDaxDialogDescription": "onboardingFireButtonRevengeDialogDescription",
    "onboardingFireButtonDaxDialogOkButton": "onboardingFireButtonRevengeDialogOkButton",
    "onboardingSitesDaxDialogDescription": "onboardingSitesRevengeDialogDescription",
    "onboardingEndDaxDialogDescription": "onboardingEndRevengeDialogDescription",
    "onboardingEndDaxDialogButton": "onboardingEndRevengeDialogButton",
    "onboardingSearchDaxDialogTitle": "onboardingSearchRevengeDialogTitle",
    "onboardingSearchDaxDialogDescription": "onboardingSearchRevengeDialogDescription",
    "onboardingSitesDaxDialogTitle": "onboardingSitesRevengeDialogTitle",
    "onboardingEndDaxDialogTitle": "onboardingEndRevengeDialogTitle",
    "onboardingPrivacyProDaxDialogTitle": "onboardingPrivacyProRevengeDialogTitle",
    "onboardingPrivacyProDaxDialogDescription": "onboardingPrivacyProRevengeDialogDescription",
    "onboardingPrivacyProDaxDialogFreeTrialOkButton": "onboardingPrivacyProRevengeDialogFreeTrialOkButton",
    "onboardingPrivacyProDaxDialogOkButton": "onboardingPrivacyProRevengeDialogOkButton",
    "onboardingPrivacyProDaxDialogCancelButton": "onboardingPrivacyProRevengeDialogCancelButton",
    
    "fireClearAllPlusDuckChats": "fireClearAllPlusRevengeChats",
    "settingsClearDataActionPlusDuckChatsSecondaryText": "settingsClearDataActionPlusRevenge_ChatsSecondaryText",
    
    "onboardingSearchDaxDialogOption1English": "onboardingSearchRevengeDialogOption1English",
    "onboardingSearchDaxDialogOption1": "onboardingSearchRevengeDialogOption1",
    "onboardingSearchDaxDialogOption2US": "onboardingSearchRevengeDialogOption2US",
    "onboardingSearchDaxDialogOption2": "onboardingSearchRevengeDialogOption2",
    "onboardingSearchDaxDialogOption4": "onboardingSearchRevengeDialogOption4",
    "onboardingSitesDaxDialogOption4": "onboardingSitesRevengeDialogOption4",
    
    "preOnboardingDaxDialog1Title": "preOnboardingRevengeDialog1Title",
    "preOnboardingDaxDialog1Button": "preOnboardingRevengeDialog1Button",
    "preOnboardingDaxDialog1SecondaryButton": "preOnboardingRevengeDialog1SecondaryButton",
    "preOnboardingDaxDialog2Title": "preOnboardingRevengeDialog2Title",
    "preOnboardingDaxDialog2Button": "preOnboardingRevengeDialog2Button",
    "preOnboardingDaxDialog3Title": "preOnboardingRevengeDialog3Title",
    "preOnboardingDaxDialog3Text": "preOnboardingRevengeDialog3Text",
    "preOnboardingDaxDialog3Button": "preOnboardingRevengeDialog3Button",
    "preOnboardingDaxDialog3SecondaryButton": "preOnboardingRevengeDialog3SecondaryButton",

    # Settings strings
    "settingsAutomaticallyClearWhatOptionTabsAndDataAndChats": "settingsAutomaticallyClearWhatOptionTabsAndDataAndChats", # This one already matches or is similar
    "settingsClearAiDataTitle": "settingsClearAiDataTitle",
    "settingsClearAiDataDeleteMessage": "settingsClearAiDataDeleteMessage",
    "settingsClearDataActionPlusRevenge_ChatsSecondaryText": "settingsClearDataActionPlusRevenge_ChatsSecondaryText",
    
    # Specific missing ones from logs
    "onboardingSitesSuggestionsDaxDialogTitle": "onboardingSitesSuggestionsRevengeDialogTitle",
    "onboardingSitesRevengeDialogDescription": "onboardingSitesRevengeDialogDescription",
    "onboardingSitesRevengeDialogOption1": "onboardingSitesRevengeDialogOption1",
    "onboardingSitesRevengeDialogOption2": "onboardingSitesRevengeDialogOption2",
    "onboardingSitesRevengeDialogOption3": "onboardingSitesRevengeDialogOption3",
    "onboardingSitesRevengeDialogOption4": "onboardingSitesRevengeDialogOption4",
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
apply_replacements("duckchat/duckchat-impl/src/main")
