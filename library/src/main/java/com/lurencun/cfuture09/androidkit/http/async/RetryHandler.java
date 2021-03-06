/*
 * @(#)RetryHandler.java		       Project:androidkit
 * Date:2013-5-8
 *
 * Copyright (c) 2013 CFuture09, Institute of Software, 
 * Guangdong Ocean University, Zhanjiang, GuangDong, China.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lurencun.cfuture09.androidkit.http.async;

import android.os.SystemClock;

import com.githang.androidkit.utils.Log4AK;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

import javax.net.ssl.SSLException;

/**
 * 以下代码参考自android-async-http框架。再次感叹，这真是一个伟大的框架。
 * 
 * @author Geek_Soledad <a target="_blank" href=
 *         "http://mail.qq.com/cgi-bin/qm_share?t=qm_mailme&email=XTAuOSVzPDM5LzI0OR0sLHM_MjA"
 *         style="text-decoration:none;"><img src=
 *         "http://rescdn.qqmail.com/zh_CN/htmledition/images/function/qm_open/ico_mailme_01.png"
 *         /></a>
 */
public class RetryHandler implements HttpRequestRetryHandler {
	private static final Log4AK log = Log4AK.getLog(RetryHandler.class);
	/**
	 * 重试等待时间。
	 */
	private static final int RETRY_SLEEP_TIME_MILLIS = 1500;
	/**
	 * 异常白名单，表示网络原因，继续重试
	 */
	private static HashSet<Class<?>> exceptionWhitelist = new HashSet<Class<?>>();
	/**
	 * 异常黑名单，表示用户原因，不重试
	 */
	private static HashSet<Class<?>> exceptionBlacklist = new HashSet<Class<?>>();

	static {
		// 进行重试，可能是服务器连接掉了
		exceptionWhitelist.add(NoHttpResponseException.class);
		// 进行重试，可能是由于从WI-FI转到3G失败时出现的错误
		exceptionWhitelist.add(UnknownHostException.class);
		// 进行重试，可能是由于从WI-FI转到3G失败时出现的错误
		exceptionWhitelist.add(SocketException.class);

		// 超时则不再重试
		exceptionBlacklist.add(InterruptedIOException.class);
		// SSL协议握手失败则不再重试
		exceptionBlacklist.add(SSLException.class);
	}

	/**
	 * 最大超时次数。
	 */
	private final int maxRetries;

	public RetryHandler(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	@Override
	public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
		// 是否重试。
		boolean retry = true;

		// 请求是否到达。
		Boolean b = (Boolean) context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
		boolean sent = (b != null && b.booleanValue());

		if (executionCount > maxRetries) {
			// 超过最大重试次数则不再重试
			retry = false;
		} else if (isInList(exceptionBlacklist, exception)) {
			// 如果是在黑名单中，则立即取消重试
			retry = false;
		} else if (isInList(exceptionWhitelist, exception)) {
			// 如果是在白名单中，则马上重试
			retry = true;
		} else if (!sent) {
			// 对于其他的错误，只有当请求还没有被完全发送时再重试
			retry = true;
		}

		if (retry) {
			// resend all idempotent requests
			HttpUriRequest currentReq = (HttpUriRequest) context
					.getAttribute(ExecutionContext.HTTP_REQUEST);
			String requestType = currentReq.getMethod();
			retry = !requestType.equals("POST");
		}

		if (retry) {
			SystemClock.sleep(RETRY_SLEEP_TIME_MILLIS);
		} else {
			exception.printStackTrace();
			log.w(exception);
		}
		return retry;
	}

	protected boolean isInList(HashSet<Class<?>> list, Throwable tr) {
		for (Class<?> clazz : list) {
			if (clazz.isInstance(tr)) {
				return true;
			}
		}
		return false;
	}
}
