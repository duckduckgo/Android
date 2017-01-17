package com.duckduckgo.mobile.android.views.autocomplete;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

public class DDGAutoCompleteTextView extends EditText implements View.OnLongClickListener {

    private android.view.ActionMode actionMode;
    private android.view.ActionMode.Callback actionModeCallback;

	public DDGAutoCompleteTextView(Context context) {
		super(context);
	}

	public DDGAutoCompleteTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public DDGAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	private BackButtonPressedEventListener backButtonPressedEventListener;

	public void setOnBackButtonPressedEventListener(BackButtonPressedEventListener eventListener) {
		backButtonPressedEventListener = eventListener;
	}
	
	public String getTrimmedText(){
		return getText().toString().trim();
	}

	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
			backButtonPressedEventListener.onBackButtonPressed();
			return false;
		}
        if(keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP){
            super.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
            return true;
        }
		return super.dispatchKeyEvent(event);
	}

    public void addBang() {
		if(isCursorAtEnd() && !lastCharIsSpaceOrNull()){
            getText().insert(getSelectionStart(), " !");
		} else {
			getText().replace(getSelectionStart(), getSelectionEnd(), "!");
            setSelection(getSelectionEnd());
		}
	}

    @Override
    public boolean onLongClick(View v) {
        return true;
    }

	private boolean lastCharIsSpaceOrNull(){
		return !hasText() || getText().charAt(getText().length() - 1) == ' ';
	}

	private boolean hasText() {
		return getText().length() > 0;
	}

	private boolean isCursorAtEnd() {
		return getSelectionStart() == getText().length();
	}

	public void addTextWithTrailingSpace(String phrase) {
		setText(phrase.trim() + " ");
        setCursorAtEnd();
    }

    private void setCursorAtEnd() {
        setSelection(getText().length());
    }

    public void pasteQuery(String suggestion) {
        releaseFocus();
        setText(suggestion);
        append(" ");
        obtainFocus();
        setCursorAtEnd();
    }

    private void releaseFocus() {
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    private void obtainFocus() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.requestFocus();
    }

}
