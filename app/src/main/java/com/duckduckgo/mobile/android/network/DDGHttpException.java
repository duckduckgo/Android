package com.duckduckgo.mobile.android.network;

public class DDGHttpException extends Exception {
	private int status;
	 
    /**
     *
     */
    private static final long serialVersionUID = 1L;
 
    public DDGHttpException() {
    }
     
    public DDGHttpException(String message){
        super(message);
    }
    
    public DDGHttpException(String message, int httpStatus){
        super(message);
        this.status = httpStatus;
    }
    
    public DDGHttpException(int httpStatus){
        this.status = httpStatus;
    }
    
    public int getHttpStatus(){
    	return status;
    }
}
