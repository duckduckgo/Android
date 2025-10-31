# Use-Case Analysis

## 1. Can we create Fire Tabs (Containerised tabs that burn after use)?

### Why “Fire Tabs” Don’t Work Well
A single tab can open links in new tabs, creating parent-child relationships. To isolate each tab fully, we’d have to:
- Block new tabs from opening (breaking expected web behavior), or
- Open new tabs as entirely independent Fire Tabs (breaking navigation, logins, and site context).

Both options would cause confusing and inconsistent behavior for users.

### Proposal: Move from Fire Tabs → Fire Sessions
Instead of isolated tabs, we should think in terms of **Fire Sessions**: self-contained browsing environments that leave no trace once closed.

A **Fire Session** behaves like a temporary browsing profile. It is completely separate from the **Default Session** (the user’s normal browsing environment).

Each Fire Session is isolated:
- Pages visited in one session are invisible to others (no shared link history).
- Cookies, scripts, and site data (including logins) don’t persist across sessions.
- No data from Fire Sessions contaminates the Default Session.

We can decide whether to:
- Allow **multiple concurrent Fire Sessions**, or
- Limit users to **a single Fire Session** at a time (simpler implementation and UI).

**Effort:** Medium (~1 week).    
(UI/UX effort not included.)

### Requirements
To support Fire Sessions, the device’s WebView must implement the **`Profile` APIs**.    
If those APIs aren’t available, Fire Sessions cannot function properly.

### Cleaning Up a Fire Session
When all tabs within a Fire Session are closed, the entire session can be **securely wiped** - no restart required.

#### Requirements
- Ideal cleanup requires the **`WebStorageCompat` APIs** for reliable deletion.
- If unavailable, we can attempt manual cleanup (as proven by a PoC). However, this path adds moderate complexity.

### Feature Behavior Inside Fire Sessions

#### Search and Suggestion History
We have several choices depending on desired user experience:
- **Access Default Session history in Fire Sessions**
    - ✅ Yes: No extra work.
    - 🚫 No: Small effort (~1 day).
- **Access Fire Session history in Default Session**
    - ✅ Yes: No change needed.
    - 🚫 No:
        - Maintain separate Fire Session history: Medium effort (~1 week).
        - Save no history at all: Small effort (~1 day).

#### Favorites and Bookmarks
Bookmarks and favorites remain **shared** between Fire Sessions and the Default Session, preserving consistency and reducing user confusion.

#### Fireproofing
The concept of “fireproofing” (making a site persist across burns) doesn’t apply within Fire Sessions.    
This option should be **removed or disabled** from the browser menu while in a Fire Session.

#### Privacy Protections
Privacy protections work the same way in all sessions.    
Disabling protections for a site affects **every session**, as these settings are global rather than per-session.

### Summary
To reduce engineering time, we could:
- Enable Fire Sessions **only on devices that fully support** the required APIs.
- Use **exploratory Pixels** in the current app to measure the percentage of users with compatible devices before committing to implementation.

**Effort:** Medium-Large (~1-2 weeks).  
(UI/UX not included.)

## 2. Can we burn single tabs?

Yes, it’s technically feasible to **burn** a single tab’s data, similar to desktop browsers:
- **Burn all site data** visited in that tab, or let the user **select specific sites** to burn.    
  **Effort:** Medium (~1 week).
- **Delete associated search and suggestion history** from that tab.    
  **Effort:** Medium (~1 week).

(UI/UX not included.)

All cleanup can occur immediately - no restart required.

Links visited in the burned tab would remain to be marked as visited in other tabs.
There are potential JavaScript injections we could do to prevent this, but it would have to be evaluated separately. 

#### Requirements
Requires WebView support for `WebStorageCompat` APIs.

## 3. Can we clear data only (not tabs)?

We can allow users to **clear all browsing data** while keeping their open tabs.    
Tabs would remain visible in the tab switcher, but without live previews or cached states.

This operation would require an **app restart** to reinitialize cleanly.

**Effort:** Small (~1 day).    
(UI/UX not included.)

## 4. Can we clear tabs only (not data)?

Yes. No technical investment needed - only UI work to expose this option.

## 5. Clear history (back and forward stack and auto-suggest) separately from data?

Yes.

**Effort:** Small (~1 day).    
(UI/UX not included.)