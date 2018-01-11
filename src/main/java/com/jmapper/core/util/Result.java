package com.jmapper.core.util;

public class Result<T> {

	private T data;
	private boolean success;
	private String msg;
	
	
	public Result(T data, boolean success, String msg) {
		super();
		this.data = data;
		this.success = success;
		this.msg = msg;
	}
	
    

	public Result(boolean success, String msg) {
		super();
		this.success = success;
		this.msg = msg;
	}
    


	public Result(boolean success) {
		super();
		this.success = success;
	}



	public T getData() {
		return data;
	}
	public void setData(T data) {
		this.data = data;
	}
	public boolean isSuccess() {
		return success;
	}
	public void setSuccess(boolean success) {
		this.success = success;
	}
	public String getMsg() {
		return msg;
	}
	public void setMsg(String msg) {
		this.msg = msg;
	}
	
	@Override
	public String toString() {
		return "Result [data=" + data + ", success=" + success + ", msg=" + msg + "]";
	}
	
	
}
