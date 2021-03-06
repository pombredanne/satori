package satori.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;

import satori.config.SConfig;
import satori.session.SSession;
import satori.task.STaskHandler;

public class SBlobClient {
	private static HttpURLConnection createUnsecureConnection(String address) throws Exception {
		URL url = new URL("http://" + address);
		return (HttpURLConnection)url.openConnection();
	}
	private static HttpsURLConnection createSecureConnection(String address) throws Exception {
		SSLContext context = SSLContext.getInstance("SSL");
		context.init(null, new TrustManager[] { new X509TrustManager() {
			@Override public X509Certificate[] getAcceptedIssuers() { return null; }
			@Override public void checkClientTrusted(X509Certificate[] certs, String authType) {}
			@Override public void checkServerTrusted(X509Certificate[] certs, String authType) {}
		} }, new SecureRandom());
		SSLSocketFactory socket_factory = context.getSocketFactory();
		URL url = new URL("https://" + address);
		HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
		connection.setSSLSocketFactory(socket_factory);
		connection.setHostnameVerifier(new HostnameVerifier() {
			@Override public boolean verify(String hostname, SSLSession session) { return true; }
		});
		return connection;
	}
	private static HttpURLConnection createConnection(String path) throws Exception {
		String address = SConfig.getHost() + ":" + SConfig.getBlobsPort() + path;
		if (SConfig.getUseSSL()) return createSecureConnection(address);
		else return createUnsecureConnection(address);
	}
	private static String getUploadPath() { return "/blob/upload"; }
	private static String getDownloadPath(String hash) { return "/blob/download/" + hash; }
	
	private static HttpURLConnection putBlobSetup(File file) throws Exception {
		HttpURLConnection connection = createConnection(getUploadPath());
		connection.setDoOutput(true);
		connection.setUseCaches(false);
		connection.setRequestMethod("PUT");
		connection.setRequestProperty("Cookie", "satori_token=" + SSession.getToken());
		connection.setRequestProperty("Filename", file.getName());
		if (file.length() > Integer.MAX_VALUE) throw new Exception("Cannot handle blobs bigger than " + Integer.MAX_VALUE + "B");
		connection.setFixedLengthStreamingMode((int)file.length());
		return connection;
	}
	private static HttpURLConnection getBlobSetup(String hash) throws Exception {
		HttpURLConnection connection = createConnection(getDownloadPath(hash));
		connection.setUseCaches(false);
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Cookie", "satori_token=" + SSession.getToken());
		return connection;
	}
	private static void checkResponse(HttpURLConnection connection) throws Exception {
		int response = connection.getResponseCode();
		if (response != HttpURLConnection.HTTP_OK) throw new Exception("Error saving blob: " + response + " " + connection.getResponseMessage());
	}
	private static void putBlob(HttpURLConnection connection, File file) throws Exception {
		InputStream in = new FileInputStream(file);
		try {
			OutputStream out = connection.getOutputStream();
			try { IOUtils.copy(in, out); }
			finally { IOUtils.closeQuietly(out); }
		} finally { IOUtils.closeQuietly(in); }
	}
	private static String readResponse(HttpURLConnection connection) throws Exception {
		InputStream in = connection.getInputStream();
		Writer result = new StringWriter();
		try { IOUtils.copy(in, result); }
		finally { IOUtils.closeQuietly(in); }
		return result.toString();
	}
	private static void getBlob(HttpURLConnection connection, File file) throws Exception {
		InputStream in = connection.getInputStream();
		try {
			OutputStream out = new FileOutputStream(file);
			try { IOUtils.copy(in, out); }
			finally { IOUtils.closeQuietly(out); }
		} finally { IOUtils.closeQuietly(in); }
	}
	
	public static String putBlob(STaskHandler handler, File file) throws Exception {
		handler.log("Saving blob...");
		HttpURLConnection connection = putBlobSetup(file);
		putBlob(connection, file);
		checkResponse(connection);
		return readResponse(connection);
	}
	
	public static InputStream getBlobStream(String hash) throws Exception {
		HttpURLConnection connection = getBlobSetup(hash);
		checkResponse(connection);
		return connection.getInputStream();
	}
	public static void getBlob(STaskHandler handler, String hash, File file) throws Exception {
		handler.log("Loading blob...");
		HttpURLConnection connection = getBlobSetup(hash);
		checkResponse(connection);
		getBlob(connection, file);
	}
}
