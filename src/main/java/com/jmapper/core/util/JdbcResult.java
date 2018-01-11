package com.jmapper.core.util;

import java.sql.ResultSet;

public abstract class JdbcResult {

	private ResultSet rs;
	public abstract void releaseResoures();
	public ResultSet getRs() {
		return rs;
	}
	public void setRs(ResultSet rs) {
		this.rs = rs;
	}
	public JdbcResult(ResultSet rs) {
		super();
		this.rs = rs;
	}
	
}
