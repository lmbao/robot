package com.mlb.robot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Robot for rob
 * 
 * @version 1.0 2016-01-20
 * @author 
 * 
 */
public class MobicbcRobot {

	public static final String CONTENT_TYPE = "Content-type";

	public static final String INPUT_ENCODING = "inputEncoding";

	private static final String ROB_URL = "http://mobicbc2.99wuxian.com/mobilefront/mj/mobileCreateOrder.action";

	/** 日期格式yyyyMMddHHmmss **/
	public static String FORMAT_TIME = "yyyyMMddHHmmss";

	/**
	 * rob interval
	 */
	private static final int ROB_INTERVAL = 120; // FIXME
	private static final int u = 2; // simulate 3 user to rob FIXME
	private static final int OVER_TIME = 2000; // over millsecond FIXME

	/**
	 * sleep some ms until act begin, because server time precision to second.
	 */
	private static final long BEFORE_MS = 580; // FIXME

	// private static final long TIME_DIF = 0; //

	// private static final List<String> success = new
	// CopyOnWriteArrayList<String>();

	private static final String faceValue = "30"; // FIXME
	private static final String amount = "10"; // FIXME

	public static void main(String[] args) {
		System.out.println(getCurrentDate());
		final MobicbcRobot robot = new MobicbcRobot();
		final String time = "20160225100000"; // FIXME
		Set<String> pl = robot.getPhoneFromFile();
		System.out.println("time:" + time + ",size:" + pl.size());
		SimpleDateFormat df = new SimpleDateFormat(FORMAT_TIME);
		long dt = 0;
		try {
			Date d = df.parse(time);
			dt = d.getTime();
		} catch (ParseException e) {
			// e.printStackTrace();
		}
		final long start = dt;
		ExecutorService threadPool = Executors.newFixedThreadPool(pl.size());
		List<Future<Integer>> flist = Lists.newArrayListWithCapacity(pl.size());

		int r = 0;
		for (final String phone : pl) {
			flist.add(threadPool.submit(new Callable<Integer>() {
				@Override
				public Integer call() {
					return robot.doRob(start, phone);
				}
			}));
		}

		for (Future<Integer> f : flist) {
			try {
				r += f.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				// e.printStackTrace();
			}
		}

		System.out.println(getCurrentDate() + "," + r);
		threadPool.shutdown();
	}

	private Integer doRob(final long start, final String m) {

		final ExecutorService threadPool = Executors.newFixedThreadPool(u);

		long now = System.currentTimeMillis();
		if (start < now - 5000) {
			System.err.println("dont rob, time is over.");
			return 0;
		}
		if (start - 300000 > now) {// sleep until startTime - 5min
			System.out.println("sleep " + (start - now - 300000) + " ms");
			try {
				Thread.sleep(start - 300000 - now);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		final AtomicInteger count = new AtomicInteger(0);
		final List<Future<Integer>> flist = Lists.newArrayListWithCapacity(u);
		for (int i = 0; i < u; i++) {
			RobThread rt = new RobThread(start, m, count);
			flist.add(threadPool.submit(rt));
			try {
				Thread.sleep(150); // ever thread interval
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// long s = start - System.currentTimeMillis();
		// if (s > 0) {
		// try {
		// Thread.sleep(s - TIME_DIF); // 时间差
		// } catch (InterruptedException e) {
		// Thread.currentThread().interrupt();
		// }
		// }
		// RobOnTimeThread rot = new RobOnTimeThread(start, m, f, a, count);
		// flist.add(threadPool.submit(rot));
		// try {
		// Thread.sleep(50);
		// } catch (InterruptedException e) {
		// Thread.currentThread().interrupt();
		// }
		// RobOnTimeThread rot2 = new RobOnTimeThread(start, m, f, a, count);
		// flist.add(threadPool.submit(rot2));
		for (Future<Integer> r : flist) {
			try {
				if (r.get() > 0) {
					threadPool.shutdown();
					return 1;
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				// e.printStackTrace();
			}
		}
		System.err.println("rob nothing: mobile=" + m + ", time=" + getCurrentDate());
		threadPool.shutdown();
		return 0;
	}

	private class RobThread implements Callable<Integer> {

		private final long start;
		private final String mobile;
		private final AtomicInteger count;

		private RobThread(long time, String mobile, AtomicInteger count) {
			this.start = time;
			this.mobile = mobile;
			this.count = count;
		}

		@Override
		public Integer call() {

			if (count.get() > 0) {
				return 0;
			}
			// rob one to get server time.
			long t = doGet(mobile);
			if (t == 1) {
				count.incrementAndGet();
				return 1;
			}
			if (t == 0) {
				return 0;
			}
			if (start + OVER_TIME < t) {
				return 0;
			}
			long s = start - t;
			if (s > 1000) {
				try {
					Thread.sleep(s - BEFORE_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			// rob 10 times
			for (int i = 0; i < 10; i++) {
				if (count.get() > 0) {
					return 0;
				}
				long t2 = doGet(mobile);
				if (t2 == 1) {
					count.incrementAndGet();
					return 1;
				}
				if (t2 == 0) {
					return 0;
				}
				if (start + OVER_TIME < t2) {
					return 0;
				}
				if (t2 == -1) { // 服务器忙
					Random ran = new Random();
					int stop = ran.nextInt(30);
					System.out.println("sleep " + stop + ", now time:" + getCurrentDate());
					try {
						Thread.sleep(stop);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					i--;
				} else if (t2 > 0 && start > t2) { // 活动未开始
					Random ran = new Random();
					int stop = ran.nextInt(ROB_INTERVAL);
					System.out.println("sleep " + stop + ", now time:" + getCurrentDate() + ",server time: " + t2);
					try {
						Thread.sleep(stop);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}
			return 0;
		}
	}

	private long doGet(String m) {
		SimpleDateFormat df = new SimpleDateFormat(FORMAT_TIME);
		String begin = getCurrentDate();
		String[] r = mobiOrder(m);
		String end = getCurrentDate();
		if (r[1].contains("error_code")) {
			return -1;
		}
		String[] ss = r[1].split(",");
		long ot = 0;
		try {
			ot = df.parse(r[0]).getTime();
		} catch (ParseException e) {
			// ignore
		}
		String oid = "";// "orderID":196470866
		for (String s : ss) {
			if (s.contains("orderID")) {
				oid = s.substring(s.indexOf(":") + 1);
			} else if (s.contains("orderAmount")) {
				if (s.contains(amount + "00")) { // 已抢到
					System.err.println("rob one:" + m + ",time:" + begin + "-" + end + "-" + r[0] + ",oid=" + oid);
					// success.add(m);
					return 1;
				}
			}
		}
		return ot;
	}

	private String[] mobiOrder(String mobile) {
		Map<String, String> sendPamams = new HashMap<String, String>();
		sendPamams.put("jsonParam", "{\"mobile\":\"" + mobile + "\",\"faceValueCode\":\"" + faceValue
				+ "\",\"amount\":\"" + amount + "\",\"backendID\":\"icbcwap\",\"appCode\":\"mobilefee\"}");
		long s = System.currentTimeMillis();
		String[] resp = MobicbcRobot.sendRequestPost(ROB_URL, sendPamams);
		System.out.println("mobile=" + mobile + ";time=" + (System.currentTimeMillis() - s) + ";resp=" + resp[1]);
		// {"data":{"appCode":"mobilefee","channelName":"ICBC.CW.MOB.02","channelState":1,"payParams":{"interfaceVersion":"1.1.0","orderID":196470866,"orderNote":"payerinfo:15601810082","payMerchantCode":"E9999","merchOrderParams":"41520916split20160120132702spliticbcwap","orderTime":"20160120132702","mdseNum":1,"backURL":"http:\/\/mobnotify.99wuxian.com\/mobilefront\/m\/topupPayNotify.notify","currency":"156","orderAmount":3000,"mdseName":"30元话费-15601810082","channel":"ICBC.CW.MOB.02","goodsURL":"http:\/\/mobicbc2.99wuxian.com:80\/mobilefront\/m\/topupPayNotify.action","merchantCode":"Z0031","mdsePrice":30.0,"signData":"QUQLjlsA5zOy0jay2ca65z0w8p3WCS9IHwTP66+r9WI2FPOCIx1xwoSYGfHf3nnwSYOAmNneE1OfAaDX4KBPvLv\/1KQV5OTFCDxlhtMPlhUA+tQmSyEdAty2ocIMhv1bd2V8l4R0GQxNUEKmvsqW06qxfxpWW5snkgl6c5P5ZDlnTQ7udjavGLZfKnm7j0\/iTzUTBAgn1y+wy\/SJoFiNU6kHObVXSgR7+iom\/dEQa7P9ywHdEKtL\/0TtoQ4ZVB0KN8GMYgPlrSnySStHoIuOKzUuGWt30s9MwAjSgAN0ZBB0hM5WjFqLIoPsje4YzCiWyWsjojmQIpB66QaGaMn9SQ==","backendId":"icbcwap"},"payUrl":"http:\/\/mps.handpay.cn\/pgw\/payment\/sendorderpay.do"},"status":"0"}
		// {"errObject":{"error_code":0,"error_message":"哎呀，当前排队下单的人数太多了，稍后再试试吧"},"status":"1"}
		return resp;
	}

	// @SuppressWarnings("unused")
	// private class RobOnTimeThread implements Callable<Integer> {
	//
	// private final long start;
	// private final String mobile;
	// private final String faceValue;
	// private final String amount;
	// private final AtomicInteger count;
	//
	// private RobOnTimeThread(long time, String mobile, String faceValue,
	// String amount, AtomicInteger count) {
	// this.start = time;
	// this.mobile = mobile;
	// this.faceValue = faceValue;
	// this.amount = amount;
	// this.count = count;
	// }
	//
	// @Override
	// public Integer call() {
	//
	// // rob 5 times
	// for (int i = 0; i < 5; i++) {
	// if (count.get() > 0) {
	// return 0;
	// }
	// if (i == 0) {
	// System.out.println("=========" + getCurrentDate());
	// }
	// long t2 = doGet(mobile, faceValue, amount);
	// if (t2 == 1) {
	// System.out.println("RobOnTimeThread rob:" + mobile + ", now time: " +
	// getCurrentDate()
	// + ", times: " + i);
	// count.incrementAndGet();
	// return 1;
	// }
	// if (t2 == 0) {
	// return 0;
	// }
	// if (start + 1000 < t2) {
	// return 0;
	// }
	// if (t2 > 0 && start > t2) {
	// System.out.println("sleep 50-2, now time:" + getCurrentDate() +
	// ",server time: " + t2);
	// try {
	// Thread.sleep(50);
	// } catch (InterruptedException e) {
	// // ignore
	// }
	// }
	// }
	// return 0;
	// }
	// }

	/**
	 * 发送报文 使用http post的方式
	 * 
	 * @param url
	 *            请求URL
	 * @param sendPamams
	 *            请求参数
	 * @return 请求结果
	 */
	public static String[] sendRequestPost(String url, Map<String, String> sendPamams) {
		return sendRequestPost(url, sendPamams, null);
	}

	/**
	 * 发送报文 使用http post的方式
	 * 
	 * @param url
	 *            请求URL
	 * @param sendPamams
	 *            请求参数
	 * @param connectionParams
	 *            报文头参数 <br>
	 *            eg: <br>
	 *            connectionParams.put(HttpClientProtocol.CONTENT_TYPE,
	 *            "application/x-www-form-urlencoded; charset=utf-8");<br>
	 *            connectionParams.put(HttpClientProtocol.INPUT_ENCODING,
	 *            "utf-8");
	 * @return 请求结果
	 */
	public static String[] sendRequestPost(String url, Map<String, String> sendPamams,
			Map<String, String> connectionParams) {

		String[] result = new String[2];
		HttpClient httpClient = new HttpClient();
		httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
		PostMethod postMethod = new PostMethod(url);
		postMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 30000);

		// 设置报文头信息
		if (connectionParams != null && !connectionParams.get(CONTENT_TYPE).isEmpty()) {
			postMethod.addRequestHeader(CONTENT_TYPE, connectionParams.get(CONTENT_TYPE));
		} else {
			postMethod.addRequestHeader(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=utf-8");
		}

		postMethod.setRequestHeader("Origin", "http://mobicbc2.99wuxian.com");
		postMethod.setRequestHeader("Referer", "http://mobicbc2.99wuxian.com/iphone/mobile/");
		postMethod
				.setRequestHeader(
						"User-Agent",
						"Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1");

		// 构造请求参数
		NameValuePair[] values = new NameValuePair[sendPamams.size()];
		int index = 0;
		for (Map.Entry<String, String> param : sendPamams.entrySet()) {
			values[index++] = new NameValuePair(param.getKey(), param.getValue());
		}
		postMethod.setRequestBody(values);

		// 执行请求
		int respCode = 0;
		try {
			respCode = httpClient.executeMethod(postMethod);
			String d = postMethod.getResponseHeader("Date").getValue();
			@SuppressWarnings("deprecation")
			Date date = new Date(d);
			String s = new SimpleDateFormat(FORMAT_TIME).format(date);
			System.out.println("server date dif:" + s + ":" + getCurrentDate());
			result[0] = s;
		} catch (HttpException e) {
			throw new RuntimeException(e.getMessage(), e);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}

		// ///////////// 解析response /////////////
		// 返回失败
		if (respCode != 200) {
			throw new RuntimeException("http resp code:" + respCode);
		}

		// 返回成功
		try {
			InputStream in = postMethod.getResponseBodyAsStream();
			BufferedReader reader = null;
			if (connectionParams != null && !connectionParams.get(INPUT_ENCODING).isEmpty()) {
				reader = new BufferedReader(new InputStreamReader(in, connectionParams.get(INPUT_ENCODING)));
			} else {
				reader = new BufferedReader(new InputStreamReader(in, "utf-8"));
			}
			String str;
			StringBuffer buffer = new StringBuffer();
			while ((str = reader.readLine()) != null) {
				buffer.append(str);
			}
			result[1] = buffer.toString();
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		} finally {
			// 断开连接
			postMethod.releaseConnection();
		}

	}

	public static String getCurrentDate() {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		return format.format(new Date());
	}

	private Set<String> getPhoneFromFile() {
		Set<String> pl = Sets.newHashSet();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				MobicbcRobot.class.getResourceAsStream("/p.txt")));
		String s = null;
		try {
			while ((s = reader.readLine()) != null) {
				String[] ps = s.split(" ");
				System.out.println(ps[0] + "--" + ps[ps.length - 1]);
				pl.add(ps[0]);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				// ignore
			}
		}
		return pl;
	}
}
