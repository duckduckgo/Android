package com.duckduckgo.mobile.android.util;

public enum SESSIONTYPE {
	SESSION_SEARCH(0), SESSION_BROWSE(2);
	
	private int code;
	
	private SESSIONTYPE(int c) {
		   code = c;
		 }
		 
	public int getCode() {
		   return code;
	}
	
	public static SESSIONTYPE getByCode(int code){
		switch(code){
			case 0:
				return SESSION_SEARCH;
			case 2:
				return SESSION_BROWSE;
			default:
				return SESSION_BROWSE;
		}	
			
	}
}