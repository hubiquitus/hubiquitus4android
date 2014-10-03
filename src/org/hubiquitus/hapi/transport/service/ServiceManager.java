package org.hubiquitus.hapi.transport.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.hubiquitus.hapi.transport.Transport;
import org.json.JSONObject;

import android.util.Log;

public class ServiceManager {
	
	public enum Method {
		POST, GET
	}
	
	public static ServiceResponse initSerConnectionService(String serverUrl) throws ClientProtocolException, IOException {
		
		final HttpClient httpClient = ConnexionService.getConnexionService()
				.getHttpClient();
		
		HttpGet httpGet = new HttpGet(serverUrl);
		httpGet.setHeader("Connection", "Keep-Alive");
		HttpResponse httpResponse = httpClient.execute(httpGet);
		ServiceResponse serviceResponse = readResponse(httpResponse);
		return serviceResponse;
	}
	

	public static ServiceResponse requestService(String serverUrl, String service, Method method,
			JSONObject request) throws ClientProtocolException, IOException {

		final HttpClient httpClient = ConnexionService.getConnexionService()
				.getHttpClient();

		StringBuilder sb = new StringBuilder();
		sb.append(serverUrl).append(service);
		
		HttpRequestBase httpRequest = null;
		switch (method) {
		case GET:
			httpRequest = new HttpGet(sb.toString());
			break;
		case POST:
			httpRequest = new HttpPost(sb.toString());
			if (request != null) {
                StringEntity entity;
                if (request.has(Transport.HB) && Transport.HB.equals(request.optString(Transport.HB))) {
                    //Object send from XHR Transport for sending heartbeat : new JSONObject().put(Transport.HB,Transport.HB)
                    entity = new StringEntity(Transport.HB);
                } else {
                    entity = new StringEntity("["
                            + JSONObject.quote(request.toString()) + "]");

                }
                ((HttpPost) httpRequest).setEntity(entity);
                httpRequest.setHeader("Content-Type", "text/plain");

			}
			break;
		default:
			break;
		}
		
		ServiceResponse serviceResponse = null;
		
		if (httpRequest != null) {
			HttpResponse httpResponse = httpClient.execute(httpRequest);
			try {
				serviceResponse = readResponse(httpResponse);
			} catch (IOException e) {
				Log.e("ServiceManager", e.getMessage());
			}
		}
		
		return serviceResponse;
	}

	private static ServiceResponse readResponse(HttpResponse response)
			throws IOException {

		ServiceResponse serviceResponse = new ServiceResponse();

		if (response.getEntity() != null) {
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(response.getEntity().getContent()));

			StringBuilder stringBuilder = new StringBuilder();
			String line = "";
			while ((line = bufferedReader.readLine()) != null) {
				stringBuilder.append(line);
			}
			bufferedReader.close();

			serviceResponse.setText(stringBuilder.toString());
		}

		serviceResponse.setStatus(response.getStatusLine().getStatusCode());
		return serviceResponse;
	}

}
