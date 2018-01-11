package com.jmapper.core.util;

import java.util.Map;

import org.springframework.jdbc.core.RowMapper;

public abstract class BigTableParameter implements RowMapper<Object>{

	public BigTableParameter(Map<String, Object> parameters) {
		super();
		this.parameters = parameters;
	}

	private Map<String,Object> parameters;

	public Map<String, Object> getParameters() {
		return parameters;
	}

	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}
	
	

}
