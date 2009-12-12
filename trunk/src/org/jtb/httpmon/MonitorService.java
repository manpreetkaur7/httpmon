package org.jtb.httpmon;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.jtb.httpmon.model.Action;
import org.jtb.httpmon.model.Monitor;
import org.jtb.httpmon.model.Request;
import org.jtb.httpmon.model.Response;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class MonitorService extends IntentService {
	//private static ClientConnectionManager CONNECTION_MGR;
	//private static HttpParams CONNECTION_PARAMS;

	static {
		// setURLConnectionTrust();
		//setHttpClientTrust();
	}

	private Prefs mPrefs;
	
	public MonitorService() {
		super("monitorService");
		mPrefs = new Prefs(this);
	}

	private static ClientConnectionManager getClientConnectionManager(HttpParams params) {
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		// http scheme
		schemeRegistry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), 80));
		// https scheme
		schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(),
				443));
		ClientConnectionManager ccm = new ThreadSafeClientConnManager(params,
				schemeRegistry);
		return ccm;
	}
	
	private static HttpParams getHttpParams() {
		HttpParams params = new BasicHttpParams();
		params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
		params.setParameter(
				ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE,
				new ConnPerRouteBean(30));
		params.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE,
				false);
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);		
		return params;
	}
	
	/*
	private static void setHttpClientTrust() {
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		// http scheme
		schemeRegistry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), 80));
		// https scheme
		schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(),
				443));

		CONNECTION_PARAMS = new BasicHttpParams();
		CONNECTION_PARAMS.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS,
				30);
		CONNECTION_PARAMS.setParameter(
				ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE,
				new ConnPerRouteBean(30));
		CONNECTION_PARAMS.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE,
				false);
		HttpProtocolParams.setVersion(CONNECTION_PARAMS, HttpVersion.HTTP_1_1);

		CONNECTION_MGR = new ThreadSafeClientConnManager(CONNECTION_PARAMS,
				schemeRegistry);
	}
	*/
	
	private static void setURLConnectionTrust() {
		try {
			HttpsURLConnection
					.setDefaultHostnameVerifier(new HostnameVerifier() {

						public boolean verify(String hostname,
								SSLSession session) {
							return true;
						}
					});

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new X509TrustManager[] { new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] chain,
						String authType) throws CertificateException {
				}

				public void checkServerTrusted(X509Certificate[] chain,
						String authType) throws CertificateException {
				}

				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
					// return null;
				}
			} }, new SecureRandom());

			HttpsURLConnection.setDefaultSSLSocketFactory(context
					.getSocketFactory());
		} catch (Exception e) { // should never happen
			Log.e(MonitorService.class.getSimpleName(),
					"error setting up SSL trust", e);
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String name = null;
		try {
			name = intent.getAction();
			Log.d(getClass().getSimpleName(), "received intent for: " + name);
			if (name == null) {
				Log.w(getClass().getSimpleName(), "name was null, returning");
				return;
			}

			Monitor monitor = mPrefs.getMonitor(name);
			if (monitor == null) {
				Log.w(getClass().getSimpleName(), "monitor was null for name: "
						+ name + ", returning");
				return;
			}
			monitor.setState(Monitor.STATE_RUNNING);
			mPrefs.setMonitor(monitor);
			sendBroadcast(new Intent("ManageMonitors.update"));

			Response response = getResponseFromHttpClient(monitor.getRequest());

			if (response.getThrowable() != null) {
				Log.w(getClass().getSimpleName(), response.getThrowable());
			}
			int state = Monitor.STATE_VALID;
			for (int i = 0; i < monitor.getConditions().size(); i++) {
				if (!monitor.getConditions().get(i).isValid(response)) {
					state = Monitor.STATE_INVALID;
					break;
				}
			}

			monitor = mPrefs.getMonitor(monitor.getName());
			if (monitor != null && monitor.getState() != Monitor.STATE_STOPPED) {
				monitor.setState(state);
				monitor.setLastUpdatedTime();

				for (int i = 0; i < monitor.getActions().size(); i++) {
					Action action = monitor.getActions().get(i);
					if (monitor.getState() == Monitor.STATE_INVALID) {
						action.failure(this, monitor);
					} else if (monitor.getState() == Monitor.STATE_VALID) {
						action.success(this, monitor);
					}
				}
				mPrefs.setMonitor(monitor);
				sendBroadcast(new Intent("ManageMonitors.update"));
			}
		} finally {
			if (name != null) {
				Intent wlIntent = new Intent("release", null, this,
						WakeLockReceiver.class);
				wlIntent.putExtra("org.jtb.httpmon.monitor.name", name);
				sendBroadcast(wlIntent);
			}
		}
	}

	private Response getResponseFromURLConnection(Request request) {
		Response response = new Response();
		InputStream is = null;

		int cTimeout = mPrefs.getConnectionTimeout();
		int rTimeout = mPrefs.getReadTimeout();
		String userAgent = mPrefs.getUserAgent();
		HttpURLConnection uc = null;

		try {
			URL u = new URL(request.getUrl());
			uc = (HttpURLConnection) u.openConnection();

			if (userAgent != null && userAgent.length() != 0) {
				uc.setRequestProperty("User-Agent", userAgent);
			}
			uc.setConnectTimeout(cTimeout * 1000);
			uc.setReadTimeout(rTimeout * 1000);
			uc.setUseCaches(false);
			uc.connect();

			int responseCode = uc.getResponseCode();
			response.setResponseCode(responseCode);
			response.setAlive(true);
			CodeTimer timer = new CodeTimer();
			is = uc.getInputStream();
			response.setContent(readResponseBody(is));
			response.setResponseTime(timer.getElapsed());
			response.setHeaderFields(uc.getHeaderFields());
		} catch (Throwable t) {
			response.setThrowable(t);
		} finally {
			try {
				if (is != null) {
					is.close();
				}
			} catch (IOException ioe) {
			}
			if (uc != null) {
				uc.disconnect();
			}
		}
		return response;
	}

	private String readResponseBody(InputStream is) throws IOException {
		BufferedInputStream bis = new BufferedInputStream(is);
		final byte[] buffer = new byte[1024];
		int count;

		StringBuilder sb = new StringBuilder();
		while ((count = bis.read(buffer)) != -1) {
			String s = new String(buffer, 0, count, "UTF-8");
			sb.append(s);
		}

		return sb.toString();
	}

	private Response getResponseFromHttpClient(Request request) {
		Response response = new Response();
		BufferedReader reader = null;

		int cTimeout = mPrefs.getConnectionTimeout();
		int rTimeout = mPrefs.getReadTimeout();
		String userAgent = mPrefs.getUserAgent();

		try {
			HttpParams params = getHttpParams();
			ClientConnectionManager ccm = getClientConnectionManager(params);
			HttpClient client = new DefaultHttpClient(ccm, params);

			HttpGet get = new HttpGet(request.getUrl());
			HttpConnectionParams.setConnectionTimeout(client.getParams(), cTimeout * 1000);
			HttpConnectionParams.setSoTimeout(client.getParams(), rTimeout * 1000);
			if (userAgent != null && userAgent.length() != 0) {
				get.setHeader("User-Agent", userAgent);
			}

			CodeTimer timer = new CodeTimer();
			HttpResponse res = client.execute(get);

			StatusLine sl = res.getStatusLine();
			int responseCode = sl.getStatusCode();
			response.setResponseCode(responseCode);

			response.setAlive(true);

			InputStream is = res.getEntity().getContent();
			String body = readResponseBody(is);
			response.setResponseTime(timer.getElapsed());
			response.setContent(body.toString());

			response.setHeaderFields(res.getAllHeaders());
		} catch (Throwable t) {
			response.setThrowable(t);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException ioe) {
			}
		}

		return response;
	}

}