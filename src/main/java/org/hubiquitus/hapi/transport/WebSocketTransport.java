package org.hubiquitus.hapi.transport;

import android.util.Log;

import org.hubiquitus.hapi.listener.ResponseListener;
import org.hubiquitus.hapi.transport.exception.TransportException;
import org.hubiquitus.hapi.transport.listener.TransportListener;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Web socket transport class
 * 
 * @author teabow
 * 
 */
public class WebSocketTransport extends Transport {

	/**
	 * Constant for websocket secure
	 */
	private static final String WSS = "wss";
	private static final String HTTPS = "https";

	/**
	 * Web socket client
	 */
	private WebSocketClient webSocketClient;
	/**
	 * Ping timeout timer
	 */
	private Timer pingTimeoutTimer;
	/**
	 * Ping timeout
	 */
	private static final int PING_TIMEOUT = 3000;
	/**
	 * Close socket ping timeout code
	 */
	private static final int CLOSE_PING_TIMEOUT_CODE = 987654321;

	/**
	 * Constructor
	 * 
	 * @param transportListener
	 *            transport listener
	 */
	public WebSocketTransport(TransportListener transportListener) {
		super(transportListener);
	}

	/**
	 * Initialize the socket
	 * 
	 * @param endpoint
	 *            endpoint
	 */
	private void initSocket(String endpoint) {

		URI endpointURI;
		
		try {
			endpointURI = new URI(endpoint);

			this.webSocketClient = new WebSocketClient(endpointURI) {

				@Override
				public void onOpen(ServerHandshake arg0) {
					
					try {
						this.send(buildNegotiateMessage().toString());
						WebSocketTransport.this.pingTimeoutTimer = new Timer();
						WebSocketTransport.this.pingTimeoutTimer.schedule(new TimerTask() {
							@Override
							public void run() {
								WebSocketTransport.this.transportListener.onWebSocketPingTimeout();
								close(CLOSE_PING_TIMEOUT_CODE);
							}
						}, PING_TIMEOUT);
					} catch (JSONException e) {
						Log.w(getClass().getCanonicalName(), e);
					}
				}

				@Override
				public void onMessage(String message) {
					try {
						WebSocketTransport.this.handleMessage(message);
					} catch (JSONException | IOException e) {
						Log.w(getClass().getCanonicalName(), e);
					}
                }

				@Override
				public void onError(Exception arg0) {
					WebSocketTransport.this.transportListener.onError(arg0.getMessage());
					arg0.printStackTrace();
				}

				@Override
				public void onClose(int arg0, String arg1, boolean arg2) {
					if (arg0 != CLOSE_PING_TIMEOUT_CODE) {
						WebSocketTransport.this.authentified = false;
						WebSocketTransport.this.transportListener.onDisconnect();
					}
				}
			};
			
			if (endpoint.startsWith(WSS) || endpoint.startsWith(HTTPS)) {
				
				TrustManager tm = new X509TrustManager() {
					
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					
					@Override
					public void checkServerTrusted(X509Certificate[] chain, String authType)
							throws CertificateException {}
					
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType)
							throws CertificateException {}
				};
				
				SSLContext sslContext;
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] {tm}, null);
                
                SSLSocketFactory factory = sslContext.getSocketFactory();
                this.webSocketClient.setSocket(factory.createSocket());
			}
			
		} catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException | IOException e) {
			Log.w(getClass().getCanonicalName(), e);
		}
    }

	public WebSocketClient getWebSocketClient() {
		return this.webSocketClient;
	}

	@Override
	public void connect(String endpoint, JSONObject authData) {
		super.connect(endpoint, authData);
		this.authData = authData;
		if (this.webSocketClient == null) {
            this.initSocket(endpoint + "/websocket");
			this.webSocketClient.connect();
		}
	}

	@Override
	public JSONObject send(String to, Object content, int timeout,
			ResponseListener responseListener) throws TransportException {
		JSONObject jsonMessage = super.send(to, content, timeout,
				responseListener);
		if (this.webSocketClient == null) {
			throw new TransportException("webSocketClient is null in send");
		}
		
		try {
			this.webSocketClient.send(jsonMessage.toString());
		} catch (Exception e) {
			Log.w(getClass().getCanonicalName(), e);
			transportListener.onError(e.getMessage() != null ? e.getMessage() : "");
		}
		
		try {
			this.responseQueue.put(jsonMessage.getString(ID), responseListener);
		} catch (JSONException e) {
			Log.w(getClass().getCanonicalName(), e);
		}
		return jsonMessage;
	}

	@Override
	protected void send(JSONObject jsonObject) throws TransportException {
		if (this.webSocketClient == null) {
			throw new TransportException("webSocketClient is null");
		}
		this.webSocketClient.send(jsonObject.toString());
	}

    @Override
    protected void sendHeartBeat() throws TransportException {
        if (this.webSocketClient == null) {
            throw new TransportException("webSocketClient is null");
        }
        this.webSocketClient.send(Transport.HB);
    }

    @Override
	public void disconnect() {
		if (this.webSocketClient != null) {
            this.webSocketClient.close();
            this.webSocketClient = null;
		}
		this.transportListener.onDisconnect();
	}
	
	@Override
	public void silentDisconnect() {
		if (this.webSocketClient != null) {
            this.webSocketClient.close();
            this.webSocketClient = null;
		}
	}

	/**
	 * Cancels the ping timeout timer.
	 */
	void cancelPingTimeout() {
		if (this.pingTimeoutTimer != null) {
			this.pingTimeoutTimer.cancel();
		}
	}

}