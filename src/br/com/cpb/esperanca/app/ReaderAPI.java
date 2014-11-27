package br.com.cpb.esperanca.app;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class ReaderAPI {
    
    private static final String BASE_URL = "http://ws.cpb.com.br/apps/cpbreader/";
    
    private static AsyncHttpClient mClient = new AsyncHttpClient();
    
    public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        mClient.get(getAbsoluteUrl(url), params, responseHandler);
    }

    public static void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        mClient.post(getAbsoluteUrl(url), params, responseHandler);
    }

    public static String getAbsoluteUrl(String relativeUrl) {
    	String urlName = null; 
    	//CPB URL SERVER
    	urlName = BASE_URL + relativeUrl;
   		
    	return 	urlName;
    }
}
