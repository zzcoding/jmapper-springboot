package com.jmapper.core.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;





import org.apache.log4j.Logger;



public class MultithreadUtil {

	Logger logger = Logger.getLogger(MultithreadUtil.class);
	BlockingQueue<Runnable> queue = null;
	ThreadPoolExecutor pool = null;

	private int corePoolSize=15;
	private int maximumPoolSize=20;
	private long keepAliveTime=50;
	private TimeUnit unit=TimeUnit.SECONDS;
    
	
	public void init() {
		queue = new LinkedBlockingQueue<Runnable>();
		pool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue);
	}

	public void execute(Runnable run) {
		pool.execute(run);
	
	}

	
	public void dispose() {
		logger.info("线程池将关闭");
		pool.shutdown();
	}

	public void waitThread(){
		logger.info("当前正在执行的线程数+++++++============:"+pool.getActiveCount());
		while(pool.getActiveCount()!=0){
			logger.info("当前正在执行的线程数+++++++============:"+pool.getActiveCount());
			try
			{
				Thread.sleep(10000);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	
	}
	public int getCorePoolSize() {
		return corePoolSize;
	}

	public void setCorePoolSize(int corePoolSize) {
		this.corePoolSize = corePoolSize;
	}

	public int getMaximumPoolSize() {
		return maximumPoolSize;
	}

	public void setMaximumPoolSize(int maximumPoolSize) {
		this.maximumPoolSize = maximumPoolSize;
	}

	public long getKeepAliveTime() {
		return keepAliveTime;
	}

	public void setKeepAliveTime(long keepAliveTime) {
		this.keepAliveTime = keepAliveTime;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	public void setUnit(TimeUnit unit) {
		this.unit = unit;
	}

	public ThreadPoolExecutor getPool() {
		return pool;
	}

	public void setPool(ThreadPoolExecutor pool) {
		this.pool = pool;
	}

}
