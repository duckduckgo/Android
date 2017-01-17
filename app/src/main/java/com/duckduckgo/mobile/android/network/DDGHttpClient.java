package com.duckduckgo.mobile.android.network;

import info.guardianproject.onionkit.trust.StrongHttpsClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.zip.GZIPInputStream;

import android.content.Context;
import ch.boye.httpclientandroidlib.Header;
import ch.boye.httpclientandroidlib.HeaderElement;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.HttpRequest;
import ch.boye.httpclientandroidlib.HttpRequestInterceptor;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.NameValuePair;
import ch.boye.httpclientandroidlib.client.entity.UrlEncodedFormEntity;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.entity.HttpEntityWrapper;
import ch.boye.httpclientandroidlib.impl.client.DefaultConnectionKeepAliveStrategy;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.params.BasicHttpParams;
import ch.boye.httpclientandroidlib.params.HttpParams;
import ch.boye.httpclientandroidlib.params.HttpProtocolParams;
import ch.boye.httpclientandroidlib.protocol.HTTP;
import ch.boye.httpclientandroidlib.protocol.HttpContext;
import ch.boye.httpclientandroidlib.util.EntityUtils;

import com.duckduckgo.mobile.android.activity.DuckDuckGo;
import com.duckduckgo.mobile.android.util.DDGConstants;


public class DDGHttpClient extends StrongHttpsClient {
	HttpGet request;
	HttpEntity entity;
	public HttpResponse response;
	HttpPost post;
	
	ClientConnectionManager connManager;
			
	private String m_strResult;

	// Set the timeout in milliseconds until a connection is established.
	int timeoutConnection = 3000;
	
	// Set the default socket timeout (SO_TIMEOUT) in milliseconds which is the timeout for waiting for data.
	int timeoutSocket = 3000;
	
	private int mStatusCode;

	/**
	 * XXX currently UNUSED
	 */
	public DDGHttpClient(Context context){
		super(context);
		HttpParams httpParameters = new BasicHttpParams();
		//HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		//HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
//		HttpClientParams.setRedirecting(httpParameters, false);
		setParams(httpParameters);
		setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy());
		
		// HttpClientParams.setCookiePolicy(httpParameters, CookiePolicy.BEST_MATCH);
		HttpProtocolParams.setUserAgent(httpParameters, DDGConstants.USER_AGENT);
		// HttpProtocolParams.setUserAgent(httpParameters, "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
		// HttpProtocolParams.setUserAgent(httpParameters, "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:5.0) Gecko/20110619 Firefox/5.0");
		// HttpProtocolParams.setUserAgent(httpParameters, "Prototip/0.1 (Linux; U; Android 1.1)");

	}
	
	public DDGHttpClient(Context context, ClientConnectionManager cm, HttpParams httpParams){
		super(context);
		setParams(httpParams);
		connManager = cm;
		
		// HttpParams httpParameters = new BasicHttpParams();
		//HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		//HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
//		HttpClientParams.setRedirecting(httpParams, false);
		// setParams(httpParams);
		setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy());
		
		// HttpClientParams.setCookiePolicy(httpParameters, CookiePolicy.BEST_MATCH);
//		HttpProtocolParams.setUserAgent(httpParams, "HTC_Dream Mozilla/5.0 (Linux; U; Android 1.5; en-us; Build/CUPCAKE) AppleWebKit/528.5+ (KHTML, like Gecko) Version/3.1.2 Mobile Safari/525.20.1");
		// HttpProtocolParams.setUserAgent(httpParameters, "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
		// HttpProtocolParams.setUserAgent(httpParameters, "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:5.0) Gecko/20110619 Firefox/5.0");
		// HttpProtocolParams.setUserAgent(httpParameters, "Prototip/0.1 (Linux; U; Android 1.1)");
		HttpProtocolParams.setUserAgent(httpParams, DDGConstants.USER_AGENT);
		
		this.addRequestInterceptor(new HttpRequestInterceptor() {

            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                if (!request.containsHeader("Accept-Encoding")) {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }

        });
		
		this.addResponseInterceptor(new HttpResponseInterceptor() {

            public void process(
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++) {
                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(
                                    new GzipDecompressingEntity(response.getEntity()));
                            return;
                        }
                    }
                }
            }

        });		

	}
	
	static class GzipDecompressingEntity extends HttpEntityWrapper {

        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent()
            throws IOException, IllegalStateException {

            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();

            return new GZIPInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            // length of ungzipped content is not known
            return -1;
        }

    }
	
	
	public HttpEntity doPost(String url, List<NameValuePair> params) throws DDGHttpException
	{	
		try {
			post = new HttpPost(url);
			post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
			
			response = execute(post);
			mStatusCode = response.getStatusLine().getStatusCode();
			if(mStatusCode != HttpStatus.SC_OK){
				throw new DDGHttpException(mStatusCode);
			}
		    
			return response.getEntity();
		}
		catch(DDGHttpException ddgEx){
			throw ddgEx;
		}
		catch(Exception ex){
			// unsupportencoding, clientprotocol, io
			throw new DDGHttpException(ex.getMessage());
		}
	}

	public String doPostString(String url, List<NameValuePair> params) throws DDGHttpException
	{	
		try {
			HttpEntity entity = doPost(url,params);
			m_strResult = EntityUtils.toString(entity);
//			EntityUtils.consume(entity);
			return m_strResult;
		}
		catch(DDGHttpException ddgEx){
			throw ddgEx;
		}
		catch(IOException ex){
			// io
			throw new DDGHttpException(ex.getMessage());
		}
	}
	
	
	public HttpEntity doGet(String url) throws DDGHttpException {
		try {
			request = new HttpGet(url);
	//		Log.v("REQ",url);
			
			response = execute(request);
			mStatusCode = response.getStatusLine().getStatusCode();					
			if(mStatusCode != HttpStatus.SC_OK){
				throw new DDGHttpException(mStatusCode);
			}
			
			entity = response.getEntity();
			return entity;
		}
		catch(DDGHttpException ddgEx){
			throw ddgEx;
		}
		catch(Exception ex){
			// clientprotocol, io
			throw new DDGHttpException(ex.getMessage());
		}
	}
	
	public String doGetString(String url) throws DDGHttpException {
		try {			
			entity = doGet(url);
			m_strResult = EntityUtils.toString(entity);
//			EntityUtils.consume(entity);
			return m_strResult;
		}
		catch(DDGHttpException ddgEx){
			throw ddgEx;
		}
		catch(IOException ex){
			// io
			throw new DDGHttpException(ex.getMessage());
		}
	}
	
	public HttpEntity doGet(String url, List<NameValuePair> params, boolean raw) throws DDGHttpException {
		try {
			String paramString = "";
			int paramSize = params.size();
			NameValuePair p;
			
			if(raw){
				for(int i=0;i<paramSize;i++){
					p = params.get(i);
					paramString += p.getName() + "=" + URLEncoder.encode(p.getValue(),"utf-8");
					if(i!=paramSize-1) paramString += "&";
				}
			}
			else {
				// default
				for(int i=0;i<paramSize;i++){
					p = params.get(i);
					paramString += p.getName() + "=" + p.getValue();
					if(i!=paramSize-1) paramString += "&";
				}
			}
			
			request = new HttpGet(url + "?" + paramString);
	//		Log.v("REQ",url + "?" + paramString);
			
			response = execute(request);
			mStatusCode = response.getStatusLine().getStatusCode();		
			if(mStatusCode != HttpStatus.SC_OK){
				throw new DDGHttpException(mStatusCode);
			}
			
			return response.getEntity();
		}
		catch(DDGHttpException ddgEx){
			throw ddgEx;
		}
		catch(Exception ex){
			// clientprotocol, io
			throw new DDGHttpException(ex.getMessage());
		}
	}
	
	public String doGetString(String url, List<NameValuePair> params) throws DDGHttpException {
		try {			
			entity = doGet(url, params, false);
			m_strResult = EntityUtils.toString(entity);
//			EntityUtils.consume(entity);
			return m_strResult;
		}
		catch(DDGHttpException ddgEx){
			throw ddgEx;
		}
		catch(IOException ex){
			// io
			throw new DDGHttpException(ex.getMessage());
		}
	}
	
	public String rawGet(String url, List<NameValuePair> params) throws DDGHttpException {
		try {			
			entity = doGet(url, params, true);
			m_strResult = EntityUtils.toString(entity);
//			EntityUtils.consume(entity);
			return m_strResult;
		}
		catch(DDGHttpException ddgEx){
			throw ddgEx;
		}
		catch(IOException ex){
			// io
			throw new DDGHttpException(ex.getMessage());
		}
	}

}