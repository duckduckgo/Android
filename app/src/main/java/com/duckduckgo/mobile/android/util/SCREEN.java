package com.duckduckgo.mobile.android.util;

public enum SCREEN {
	SCR_WEBVIEW(0), SCR_ABOUT(1), SCR_HELP(2), SCR_SETTINGS(3);
	
	private int code;
	
	private SCREEN(int c) {
		   code = c;
		 }
		 
	public int getCode() {
		   return code;
	}
	
	public static SCREEN getByCode(int code){
		switch(code){
			case 0:
                return SCR_WEBVIEW;
            case 1:
				return SCR_ABOUT;
            case 2:
                return SCR_HELP;
            case 3:
                return SCR_SETTINGS;
			default:
				return SCR_WEBVIEW;
		}	
			
	}
}